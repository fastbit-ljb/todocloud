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
