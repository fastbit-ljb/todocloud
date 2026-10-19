"""End-to-end API tests against real PostgreSQL + Redis (see tests/pg.py)."""
import itertools
import os
import unittest

from tests import pg

_counter = itertools.count()


@pg.requires_postgres
@unittest.skipUnless(
    os.environ.get("DATABASE_URL") == pg.TEST_DATABASE_URL,
    "API tests need DATABASE_URL to point at TEST_DATABASE_URL",
)
class ApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        import httpx

        from app.core import rate_limit
        from app.db.migrate import upgrade_to_head
        from app.db.session import engine
        from app.main import app

        self.engine, self.redis = engine, rate_limit._redis
        await pg.reset_schema()
        await upgrade_to_head(engine)
        async for key in self.redis.scan_iter("todocloud:rate:*"):
            await self.redis.delete(key)
        self.client = httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test/api/v1"
        )

    async def asyncTearDown(self) -> None:
        await self.client.aclose()
        # Each test runs on its own event loop; drop connections bound to this one.
        await self.engine.dispose()
        await self.redis.aclose()

    async def _register(self, password: str = "correct-horse-1") -> dict:
        email = f"user{next(_counter)}@example.com"
        response = await self.client.post(
            "/auth/register", json={"email": f"  {email.upper()} ", "password": password}
        )
        self.assertEqual(response.status_code, 201, response.text)
        body = response.json()
        self.assertEqual(body["user"]["email"], email)  # trimmed and lower-cased
        return {"email": email, "password": password, **body}

    @staticmethod
    def _auth(session: dict) -> dict:
        return {"Authorization": f"Bearer {session['access_token']}"}

    async def test_auth_flow(self) -> None:
        session = await self._register()

        duplicate = await self.client.post(
            "/auth/register", json={"email": session["email"], "password": "another-pass-1"}
        )
        self.assertEqual(duplicate.status_code, 409)

        wrong = await self.client.post(
            "/auth/login", json={"email": session["email"], "password": "nope-nope"}
        )
        self.assertEqual(wrong.status_code, 401)

        login = await self.client.post(
            "/auth/login", json={"email": session["email"], "password": session["password"]}
        )
        self.assertEqual(login.status_code, 200)

        me = await self.client.get("/auth/me", headers=self._auth(login.json()))
        self.assertEqual(me.json()["email"], session["email"])
        self.assertEqual((await self.client.get("/auth/me")).status_code, 401)
        bad_token = {"Authorization": "Bearer not.a.token"}
        self.assertEqual((await self.client.get("/auth/me", headers=bad_token)).status_code, 401)

        # Refresh tokens rotate: the old one stops working once used.
        refreshed = await self.client.post(
            "/auth/refresh", json={"refresh_token": session["refresh_token"]}
        )
        self.assertEqual(refreshed.status_code, 200)
        reused = await self.client.post(
            "/auth/refresh", json={"refresh_token": session["refresh_token"]}
        )
        self.assertEqual(reused.status_code, 401)

        new_refresh = refreshed.json()["refresh_token"]
        logout = await self.client.post("/auth/logout", json={"refresh_token": new_refresh})
        self.assertEqual(logout.status_code, 204)
        after_logout = await self.client.post("/auth/refresh", json={"refresh_token": new_refresh})
        self.assertEqual(after_logout.status_code, 401)

    async def test_task_crud_and_completion(self) -> None:
        auth = self._auth(await self._register())

        created = await self.client.post(
            "/tasks",
            headers=auth,
            json={
                "title": "  写周报 ",
                "due_at": "2026-10-12T10:00:00Z",
                "reminder_offset_minutes": 30,
                "steps": [{"title": "收集数据"}],
            },
        )
        self.assertEqual(created.status_code, 201, created.text)
        task = created.json()
        self.assertEqual(task["title"], "写周报")
        self.assertEqual(task["steps"], [{"title": "收集数据", "completed": False}])
        self.assertIsNone(task["completed_at"])

        done = await self.client.patch(f"/tasks/{task['id']}", headers=auth, json={"completed": True})
        self.assertTrue(done.json()["completed"])
        self.assertIsNotNone(done.json()["completed_at"])
        reopened = await self.client.patch(
            f"/tasks/{task['id']}", headers=auth, json={"completed": False}
        )
        self.assertIsNone(reopened.json()["completed_at"])

        bad_window = await self.client.patch(
            f"/tasks/{task['id']}", headers=auth, json={"start_at": "2026-10-13T10:00:00Z"}
        )
        self.assertEqual(bad_window.status_code, 422)

        listed = await self.client.get("/tasks", headers=auth)
        self.assertEqual([t["id"] for t in listed.json()], [task["id"]])

        self.assertEqual(
            (await self.client.delete(f"/tasks/{task['id']}", headers=auth)).status_code, 204
        )
        self.assertEqual((await self.client.get("/tasks", headers=auth)).json(), [])
        self.assertEqual(
            (await self.client.delete(f"/tasks/{task['id']}", headers=auth)).status_code, 404
        )

    async def test_recurring_task_spawns_next_occurrence_once(self) -> None:
        auth = self._auth(await self._register())
        no_due = await self.client.post(
            "/tasks", headers=auth, json={"title": "每天背单词", "recurrence": "daily"}
        )
        self.assertEqual(no_due.status_code, 422)
        bad_rule = await self.client.post(
            "/tasks", headers=auth,
            json={"title": "x", "due_at": "2099-01-01T00:00:00Z", "recurrence": "hourly"},
        )
        self.assertEqual(bad_rule.status_code, 422)

        created = await self.client.post(
            "/tasks",
            headers=auth,
            json={
                "title": "周会",
                "start_at": "2099-01-07T06:00:00Z",
                "due_at": "2099-01-07T07:00:00Z",
                "recurrence": "weekly",
                "steps": [{"title": "准备议程", "completed": True}],
            },
        )
        self.assertEqual(created.status_code, 201, created.text)
        task = created.json()
        self.assertEqual(task["recurrence"], "weekly")

        done = await self.client.patch(f"/tasks/{task['id']}", headers=auth, json={"completed": True})
        self.assertEqual(done.status_code, 200, done.text)
        self.assertIsNone(done.json()["recurrence"])  # the rule moved on

        tasks = (await self.client.get("/tasks", headers=auth)).json()
        self.assertEqual(len(tasks), 2)
        nxt = next(t for t in tasks if t["id"] != task["id"])
        self.assertEqual(nxt["title"], "周会")
        self.assertEqual(nxt["recurrence"], "weekly")
        self.assertFalse(nxt["completed"])
        self.assertTrue(nxt["due_at"].startswith("2099-01-14T07:00:00"))
        self.assertTrue(nxt["start_at"].startswith("2099-01-14T06:00:00"))
        self.assertEqual(nxt["steps"], [{"title": "准备议程", "completed": False}])

        # Un-checking and re-checking the old one does not spawn another copy.
        await self.client.patch(f"/tasks/{task['id']}", headers=auth, json={"completed": False})
        await self.client.patch(f"/tasks/{task['id']}", headers=auth, json={"completed": True})
        self.assertEqual(len((await self.client.get("/tasks", headers=auth)).json()), 2)

        # Clearing the rule makes it a one-off task again.
        cleared = await self.client.patch(f"/tasks/{nxt['id']}", headers=auth, json={"recurrence": None})
        self.assertIsNone(cleared.json()["recurrence"])

    async def test_task_list_order(self) -> None:
        auth = self._auth(await self._register())
        ids = {}
        for title, due in [("later", "2026-10-20T00:00:00Z"), ("none", None), ("soon", "2026-10-11T00:00:00Z")]:
            response = await self.client.post("/tasks", headers=auth, json={"title": title, "due_at": due})
            ids[title] = response.json()["id"]
        finished = await self.client.post("/tasks", headers=auth, json={"title": "finished"})
        await self.client.patch(f"/tasks/{finished.json()['id']}", headers=auth, json={"completed": True})

        titles = [t["title"] for t in (await self.client.get("/tasks", headers=auth)).json()]
        self.assertEqual(titles, ["soon", "later", "none", "finished"])

    async def test_users_cannot_see_or_change_each_others_data(self) -> None:
        alice = self._auth(await self._register())
        bob = self._auth(await self._register())
        task = (await self.client.post("/tasks", headers=alice, json={"title": "alice"})).json()
        idea = (await self.client.post("/ideas", headers=alice, json={"content": "alice idea"})).json()

        self.assertEqual((await self.client.get("/tasks", headers=bob)).json(), [])
        self.assertEqual((await self.client.get("/ideas", headers=bob)).json(), [])
        for method, path in [
            ("PATCH", f"/tasks/{task['id']}"),
            ("DELETE", f"/tasks/{task['id']}"),
            ("PATCH", f"/ideas/{idea['id']}"),
            ("DELETE", f"/ideas/{idea['id']}"),
        ]:
            response = await self.client.request(
                method, path, headers=bob, json={} if method == "PATCH" else None
            )
            self.assertEqual(response.status_code, 404, f"{method} {path}")
        self.assertEqual(len((await self.client.get("/tasks", headers=alice)).json()), 1)

    async def test_plan_chat_answers_questions_and_plans_on_request(self) -> None:
        from unittest import mock

        auth = self._auth(await self._register())
        task = (await self.client.post("/tasks", headers=auth, json={"title": "写周报"})).json()
        replies = []

        def fake_model(prompt: str) -> dict:
            replies.append(prompt)
            return replies_queue.pop(0)

        replies_queue = [
            {"intent": "answer", "reply": "你只有一项任务，今天就能做完。"},
            {
                "intent": "plan",
                "summary": "下午写周报",
                "items": [
                    {"source_type": "task", "source_id": task["id"], "title": "写周报",
                     "start_at": "2026-10-12T14:00:00+08:00", "duration_minutes": 60}
                ],
            },
            {"intent": "answer", "reply": "不紧，留了午休。"},
        ]
        with mock.patch("app.plan_ai._call_plan_model", side_effect=fake_model):
            question = await self.client.post(
                "/plans/chat", headers=auth, json={"message": "我今天任务多吗？"}
            )
            self.assertEqual(question.status_code, 200, question.text)
            self.assertEqual(question.json()["kind"], "answer")
            self.assertIsNone(question.json()["plan"])
            self.assertEqual((await self.client.get("/plans", headers=auth)).json(), [])

            history = [
                {"role": "user", "content": "我今天任务多吗？"},
                {"role": "assistant", "content": question.json()["reply"]},
            ]
            created = await self.client.post(
                "/plans/chat", headers=auth,
                json={"message": "那帮我排一下", "history": history},
            )
            self.assertEqual(created.json()["kind"], "plan")
            plan = created.json()["plan"]
            self.assertEqual(plan["proposal"]["items"][0]["source_id"], task["id"])
            self.assertIn("我今天任务多吗", replies[1])  # earlier turns reach the model

            revised = await self.client.post(
                f"/plans/{plan['id']}/messages", headers=auth, json={"message": "这样排紧不紧？"}
            )
        self.assertEqual(revised.status_code, 200, revised.text)
        body = revised.json()
        self.assertEqual(body["proposal"], plan["proposal"])  # question left the plan alone
        self.assertEqual(body["messages"][-1], {"role": "assistant", "content": "不紧，留了午休。"})
        self.assertIn("拿不准时选", replies[0])
        self.assertIn("拿不准时选", replies[2])

    async def test_legacy_generate_still_always_plans(self) -> None:
        from unittest import mock

        auth = self._auth(await self._register())
        with mock.patch(
            "app.plan_ai._call_plan_model",
            return_value={"intent": "answer", "reply": "忽略", "summary": "计划", "items": []},
        ) as model:
            response = await self.client.post(
                "/plans/generate", headers=auth, json={"message": "随便问问"}
            )
        self.assertEqual(response.status_code, 201, response.text)
        self.assertEqual(response.json()["status"], "draft")
        self.assertIn('intent 必须是 "plan"', model.call_args.args[0])

    async def _draft_plan(self, auth: dict) -> tuple[dict, dict, dict]:
        """A draft plan with one existing task, one inbox idea and one new prompt item."""
        from unittest import mock

        task = (
            await self.client.post(
                "/tasks", headers=auth,
                json={"title": "写周报", "due_at": "2026-10-20T10:00:00+08:00",
                      "reminder_offset_minutes": 10, "steps": [{"title": "列提纲", "completed": True}]},
            )
        ).json()
        idea = (await self.client.post("/ideas", headers=auth, json={"content": "整理书桌"})).json()
        raw = {
            "intent": "plan",
            "summary": "明天三件事",
            "items": [
                {"source_type": "task", "source_id": task["id"], "title": "写周报",
                 "start_at": "2026-10-12T09:00:00+08:00", "due_at": "2026-10-12T10:00:00+08:00",
                 "duration_minutes": 60, "steps": [{"title": "写初稿"}]},
                {"source_type": "idea", "source_id": idea["id"], "title": "整理书桌",
                 "start_at": "2026-10-12T15:00:00+08:00", "duration_minutes": 30},
                {"source_type": "prompt", "source_id": 1, "title": "买牛奶",
                 "start_at": "2026-10-12T18:00:00+08:00", "duration_minutes": 15},
            ],
        }
        with mock.patch("app.plan_ai._call_plan_model", return_value=raw):
            created = await self.client.post(
                "/plans/chat", headers=auth,
                json={"message": "帮我排明天，还要买牛奶", "force_plan": True},
            )
        self.assertEqual(created.json()["kind"], "plan", created.text)
        return created.json()["plan"], task, idea

    async def test_accept_selected_items_with_edits_then_undo(self) -> None:
        auth = self._auth(await self._register())
        plan, task, idea = await self._draft_plan(auth)
        items = plan["proposal"]["items"]
        self.assertEqual(len(items), 3)

        # Adopt the task (moved to 11:00) and the new prompt item; leave the idea out.
        edited_task = {**items[0], "start_at": "2026-10-12T11:00:00+08:00",
                       "due_at": "2026-10-12T12:00:00+08:00"}
        accepted = await self.client.post(
            f"/plans/{plan['id']}/accept", headers=auth, json={"items": [edited_task, items[2]]}
        )
        self.assertEqual(accepted.status_code, 200, accepted.text)
        body = accepted.json()
        self.assertEqual(body["plan"]["status"], "accepted")
        self.assertEqual(len(body["plan"]["proposal"]["items"]), 2)
        self.assertNotIn("accept_record", body["plan"]["proposal"])
        tasks = {item["title"]: item for item in (await self.client.get("/tasks", headers=auth)).json()}
        self.assertEqual(set(tasks), {"写周报", "买牛奶"})
        self.assertTrue(tasks["写周报"]["due_at"].startswith("2026-10-12T04:00:00"))
        self.assertEqual(len((await self.client.get("/ideas", headers=auth)).json()), 1)  # idea untouched

        undone = await self.client.post(f"/plans/{plan['id']}/undo", headers=auth)
        self.assertEqual(undone.status_code, 200, undone.text)
        undo = undone.json()
        self.assertEqual(undo["plan"]["status"], "draft")
        self.assertEqual(len(undo["plan"]["proposal"]["items"]), 3)  # full proposal is back
        self.assertEqual(undo["deleted_task_ids"], [tasks["买牛奶"]["id"]])
        after = (await self.client.get("/tasks", headers=auth)).json()
        self.assertEqual([item["id"] for item in after], [task["id"]])
        self.assertEqual(after[0]["due_at"], task["due_at"])
        self.assertEqual(after[0]["reminder_offset_minutes"], 10)
        self.assertEqual(after[0]["steps"], [{"title": "列提纲", "completed": True}])

        # Undo twice is refused; the draft can be accepted again (all items by default).
        self.assertEqual((await self.client.post(f"/plans/{plan['id']}/undo", headers=auth)).status_code, 409)
        again = await self.client.post(f"/plans/{plan['id']}/accept", headers=auth)
        self.assertEqual(again.status_code, 200, again.text)
        self.assertEqual((await self.client.get("/ideas", headers=auth)).json(), [])  # idea converted

        undone_again = await self.client.post(f"/plans/{plan['id']}/undo", headers=auth)
        self.assertEqual(undone_again.status_code, 200, undone_again.text)
        self.assertEqual([item["id"] for item in (await self.client.get("/ideas", headers=auth)).json()],
                         [idea["id"]])  # idea back in the inbox

    async def test_plan_items_can_become_recurring_tasks(self) -> None:
        from unittest import mock

        auth = self._auth(await self._register())
        task = (
            await self.client.post(
                "/tasks", headers=auth, json={"title": "跑步", "due_at": "2026-10-20T07:30:00+08:00"}
            )
        ).json()
        raw = {
            "intent": "plan",
            "summary": "固定节奏",
            "items": [
                {"source_type": "prompt", "source_id": 1, "title": "组会",
                 "start_at": "2026-10-14T19:00:00+08:00", "due_at": "2026-10-14T20:00:00+08:00",
                 "recurrence": "weekly"},
                {"source_type": "prompt", "source_id": 2, "title": "背单词",
                 "due_at": "2026-10-13T08:00:00+08:00", "recurrence": "daily"},
                {"source_type": "task", "source_id": task["id"], "title": "跑步",
                 "due_at": "2026-10-13T07:30:00+08:00", "recurrence": "weekdays"},
            ],
        }
        with mock.patch("app.plan_ai._call_plan_model", return_value=raw):
            plan = (
                await self.client.post(
                    "/plans/chat", headers=auth, json={"message": "每周三组会，每天背单词", "force_plan": True}
                )
            ).json()["plan"]
        proposed = {i["title"]: i["recurrence"] for i in plan["proposal"]["items"]}
        self.assertEqual(proposed, {"组会": "weekly", "背单词": "daily", "跑步": "weekdays"})

        # A 1.3.6 client sends the items back without the field: the proposed rule is kept.
        # Here the user also switched 背单词 off ("recurrence": null) explicitly.
        old_style = [{k: v for k, v in item.items() if k != "recurrence"} for item in plan["proposal"]["items"]]
        next(i for i in old_style if i["title"] == "背单词")["recurrence"] = None
        accepted = await self.client.post(
            f"/plans/{plan['id']}/accept", headers=auth, json={"items": old_style}
        )
        self.assertEqual(accepted.status_code, 200, accepted.text)
        rules = {t["title"]: t["recurrence"] for t in (await self.client.get("/tasks", headers=auth)).json()}
        self.assertEqual(rules, {"背单词": None, "跑步": "weekdays", "组会": "weekly"})

        undone = await self.client.post(f"/plans/{plan['id']}/undo", headers=auth)
        self.assertEqual(undone.status_code, 200, undone.text)
        after = (await self.client.get("/tasks", headers=auth)).json()
        self.assertEqual([(t["title"], t["recurrence"]) for t in after], [("跑步", None)])

    async def test_accept_rejects_foreign_or_empty_selection(self) -> None:
        auth = self._auth(await self._register())
        plan, _, _ = await self._draft_plan(auth)
        foreign = {**plan["proposal"]["items"][0], "source_id": 999_999}
        response = await self.client.post(
            f"/plans/{plan['id']}/accept", headers=auth, json={"items": [foreign]}
        )
        self.assertEqual(response.status_code, 422)
        empty = await self.client.post(f"/plans/{plan['id']}/accept", headers=auth, json={"items": []})
        self.assertEqual(empty.status_code, 400)
        other = self._auth(await self._register())
        self.assertEqual((await self.client.post(f"/plans/{plan['id']}/undo", headers=other)).status_code, 404)

    async def test_import_plan_made_on_phone_then_revise_accept_undo(self) -> None:
        from unittest import mock

        auth = self._auth(await self._register())
        task = (await self.client.post(
            "/tasks", headers=auth, json={"title": "写周报", "due_at": "2026-10-20T18:00:00+08:00"}
        )).json()
        proposal = {
            "summary": "手机上用自己的密钥排的",
            "items": [
                {"source_type": "task", "source_id": task["id"], "title": "写周报",
                 "start_at": "2026-10-13T09:00:00+08:00", "due_at": "2026-10-13T10:00:00+08:00",
                 "duration_minutes": 60, "steps": [{"title": "写初稿"}], "note": "", "recurrence": None},
                {"source_type": "prompt", "source_id": 1, "title": "组会",
                 "due_at": "2026-10-14T20:00:00+08:00", "note": "", "recurrence": "weekly"},
            ],
        }
        messages = [{"role": "user", "content": "帮我排"}, {"role": "assistant", "content": "手机上用自己的密钥排的"}]
        with mock.patch("app.plan_ai._call_plan_model") as model:
            created = await self.client.post(
                "/plans/import", headers=auth, json={"proposal": proposal, "messages": messages}
            )
            self.assertEqual(created.status_code, 201, created.text)
            plan = created.json()
            self.assertEqual(plan["status"], "draft")
            self.assertEqual(len(plan["proposal"]["items"]), 2)
            self.assertEqual(plan["messages"], messages)

            # A question-only turn keeps the proposal; a plan turn replaces it.
            asked = await self.client.post(
                f"/plans/{plan['id']}/import-revision", headers=auth,
                json={"proposal": None, "messages": messages + [{"role": "user", "content": "满吗"},
                                                                  {"role": "assistant", "content": "不满"}]},
            )
            self.assertEqual(asked.status_code, 200, asked.text)
            self.assertEqual(len(asked.json()["proposal"]["items"]), 2)
            self.assertEqual(len(asked.json()["messages"]), 4)
            model.assert_not_called()  # the server never calls a model for imported plans

        accepted = await self.client.post(f"/plans/{plan['id']}/accept", headers=auth)
        self.assertEqual(accepted.status_code, 200, accepted.text)
        rules = {t["title"]: t["recurrence"] for t in (await self.client.get("/tasks", headers=auth)).json()}
        self.assertEqual(rules, {"写周报": None, "组会": "weekly"})
        late = await self.client.post(
            f"/plans/{plan['id']}/import-revision", headers=auth, json={"proposal": None, "messages": []}
        )
        self.assertEqual(late.status_code, 409)
        undone = await self.client.post(f"/plans/{plan['id']}/undo", headers=auth)
        self.assertEqual(undone.status_code, 200, undone.text)
        self.assertEqual([t["title"] for t in (await self.client.get("/tasks", headers=auth)).json()], ["写周报"])

    async def test_imported_plan_cannot_touch_other_users_tasks(self) -> None:
        owner = self._auth(await self._register())
        victim_task = (await self.client.post(
            "/tasks", headers=owner, json={"title": "别人的任务", "due_at": "2026-10-20T18:00:00+08:00"}
        )).json()
        attacker = self._auth(await self._register())
        proposal = {"summary": "x", "items": [
            {"source_type": "task", "source_id": victim_task["id"], "title": "改掉",
             "due_at": "2026-10-13T10:00:00+08:00", "note": ""},
        ]}
        plan = (await self.client.post(
            "/plans/import", headers=attacker, json={"proposal": proposal, "messages": []}
        )).json()
        accepted = await self.client.post(f"/plans/{plan['id']}/accept", headers=attacker)
        self.assertEqual(accepted.status_code, 200, accepted.text)
        self.assertEqual(accepted.json()["tasks"], [])
        mine = (await self.client.get("/tasks", headers=owner)).json()
        self.assertEqual(mine[0]["due_at"], victim_task["due_at"])  # untouched
        other = await self.client.post(
            f"/plans/{plan['id']}/import-revision", headers=owner, json={"proposal": None, "messages": []}
        )
        self.assertEqual(other.status_code, 404)
        unauth = await self.client.post("/plans/import", json={"proposal": proposal, "messages": []})
        self.assertEqual(unauth.status_code, 401)

    async def test_plan_chat_with_images(self) -> None:
        import io
        import json as jsonlib
        from unittest import mock

        from PIL import Image

        def png() -> bytes:
            buffer = io.BytesIO()
            Image.new("RGB", (8, 8), "white").save(buffer, format="PNG")
            return buffer.getvalue()

        auth = self._auth(await self._register())
        payload = jsonlib.dumps({"message": "按这张课表帮我排复习"})
        calls = []

        def fake_model(prompt, parts=None):
            calls.append((prompt, parts))
            return {"intent": "answer", "reply": "周三下午有空。"}

        with mock.patch("app.plan_ai._call_plan_model", side_effect=fake_model), \
                mock.patch(
                    "app.api.plans.store_image",
                    side_effect=lambda user_id, name, *_: f"{user_id}/{next(_counter)}-{name}",
                ) as stored:
            ok = await self.client.post(
                "/plans/chat/images",
                headers=auth,
                data={"payload": payload},
                files=[("files", ("a.png", png(), "image/png")), ("files", ("b.png", png(), "image/png"))],
            )
            too_many = await self.client.post(
                "/plans/chat/images",
                headers=auth,
                data={"payload": payload},
                files=[("files", (f"{i}.png", png(), "image/png")) for i in range(4)],
            )
            wrong_type = await self.client.post(
                "/plans/chat/images",
                headers=auth,
                data={"payload": payload},
                files=[("files", ("a.gif", b"GIF89a", "image/gif"))],
            )
            bad_payload = await self.client.post(
                "/plans/chat/images",
                headers=auth,
                data={"payload": "{}"},
                files=[("files", ("a.png", png(), "image/png"))],
            )
        self.assertEqual(ok.status_code, 200, ok.text)
        self.assertEqual(ok.json()["kind"], "answer")
        prompt, parts = calls[0]
        self.assertIn("附带了 2 张图片", prompt)
        self.assertEqual(len(parts), 2)
        self.assertTrue(parts[0]["image_url"]["url"].startswith("data:image/"))
        self.assertEqual(stored.call_count, 2)
        self.assertEqual(too_many.status_code, 400)
        self.assertEqual(wrong_type.status_code, 415)
        self.assertEqual(bad_payload.status_code, 422)
        self.assertEqual(len(calls), 1)  # rejected uploads never reach the model

        conn = await pg.connect()
        try:
            self.assertEqual(await conn.fetchval("SELECT count(*) FROM attachments"), 2)
        finally:
            await conn.close()

    async def test_conversation_history_is_persistent_private_and_versioned(self) -> None:
        from uuid import uuid4
        auth = self._auth(await self._register())
        other = self._auth(await self._register())
        conversation_id = str(uuid4())
        payload = {"title": "英语学习", "messages": [{"role": "user", "content": "每天学 20 分钟"}]}
        created = await self.client.put(f"/conversations/{conversation_id}", headers=auth, json=payload)
        self.assertEqual(created.status_code, 200, created.text)
        self.assertEqual(created.json()["revision"], 1)
        self.assertEqual((await self.client.get("/conversations", headers=auth)).json()[0]["id"], conversation_id)
        self.assertEqual((await self.client.get("/conversations", headers=other)).json(), [])
        self.assertEqual((await self.client.get(f"/conversations/{conversation_id}", headers=other)).status_code, 404)
        self.assertEqual((await self.client.put(f"/conversations/{conversation_id}", headers=other, json=payload)).status_code, 404)
        self.assertEqual((await self.client.get("/conversations")).status_code, 401)
        self.assertEqual((await self.client.put(f"/conversations/{conversation_id}", headers=auth, json=payload)).status_code, 409)
        payload["revision"] = 1
        payload["messages"].append({"role": "assistant", "content": "从单词开始"})
        updated = await self.client.put(f"/conversations/{conversation_id}", headers=auth, json=payload)
        self.assertEqual(updated.status_code, 200, updated.text)
        read = await self.client.get(f"/conversations/{conversation_id}", headers=auth)
        self.assertEqual(read.json()["messages"], updated.json()["messages"])
        self.assertEqual(read.json()["revision"], 2)

    async def test_conversation_references_reach_model_and_cannot_cross_accounts(self) -> None:
        from uuid import uuid4
        from unittest import mock
        auth = self._auth(await self._register())
        other = self._auth(await self._register())
        reference_id = str(uuid4())
        saved = await self.client.put(f"/conversations/{reference_id}", headers=auth, json={
            "title": "以前的学习目标", "messages": [{"role": "user", "content": "我的目标是通过英语考试"}],
        })
        self.assertEqual(saved.status_code, 200, saved.text)
        with mock.patch("app.api.plans.plan_turn", new_callable=mock.AsyncMock) as planner:
            planner.return_value = {"intent": "answer", "reply": "继续学习", "proposal": None}
            answer = await self.client.post("/plans/chat", headers=auth, json={"message": "接着上次的目标", "reference_ids": [reference_id]})
            self.assertEqual(answer.status_code, 200, answer.text)
            self.assertIn("通过英语考试", planner.call_args.args[4])
            planner.reset_mock()
            blocked = await self.client.post("/plans/chat", headers=other, json={"message": "读旧会话", "reference_ids": [reference_id]})
            self.assertEqual(blocked.status_code, 404, blocked.text)
            planner.assert_not_called()

    async def test_conversation_cannot_attach_another_users_plan(self) -> None:
        from uuid import uuid4
        auth = self._auth(await self._register())
        response = await self.client.put(f"/conversations/{uuid4()}", headers=auth, json={
            "title": "x", "messages": [], "plan_id": 2_147_483_000,
        })
        self.assertEqual(response.status_code, 404, response.text)

    async def test_app_version_is_public(self) -> None:
        from unittest import mock

        from app import app_version
        from tests.test_app_version import RELEASE

        app_version.reset_cache()
        with mock.patch.object(app_version, "_fetch_latest_release", return_value=RELEASE):
            response = await self.client.get("/app/version")
        app_version.reset_cache()
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["latest_version"], "1.3.2")
        self.assertEqual(response.json()["download_url"], "https://example.invalid/app.apk")

    async def test_idea_crud(self) -> None:
        auth = self._auth(await self._register())
        created = await self.client.post(
            "/ideas",
            headers=auth,
            json={"content": " 学吉他 ", "duration_minutes": 60, "items": [{"content": "买琴"}]},
        )
        self.assertEqual(created.status_code, 201, created.text)
        idea = created.json()
        self.assertEqual(idea["content"], "学吉他")
        self.assertEqual(idea["items"], [{"content": "买琴"}])

        updated = await self.client.patch(
            f"/ideas/{idea['id']}", headers=auth, json={"content": "学尤克里里"}
        )
        self.assertEqual(updated.json()["content"], "学尤克里里")
        self.assertEqual(updated.json()["duration_minutes"], 60)  # untouched fields stay

        bad_window = await self.client.post(
            "/ideas",
            headers=auth,
            json={
                "content": "x",
                "start_at": "2026-10-13T00:00:00Z",
                "due_at": "2026-10-12T00:00:00Z",
            },
        )
        self.assertEqual(bad_window.status_code, 422)

        self.assertEqual(
            (await self.client.delete(f"/ideas/{idea['id']}", headers=auth)).status_code, 204
        )
        self.assertEqual((await self.client.get("/ideas", headers=auth)).json(), [])


if __name__ == "__main__":
    unittest.main()
