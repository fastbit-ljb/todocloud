import unittest

from app.ai_parser import _localize_reference
from app.plan_ai import _build_plan_prompt, _normalize_proposal


class PlanAiTests(unittest.TestCase):
    def setUp(self) -> None:
        self.reference = _localize_reference(
            "2026-10-08T01:00:00Z",
            "Asia/Shanghai",
        )
        self.ideas = [{"id": 3, "content": "整理旅行照片"}]
        self.tasks = [{"id": 7, "title": "赶晚上十点的火车"}]

    def test_prompt_protects_meals_and_sleep_with_explicit_exceptions(self) -> None:
        prompt = _build_plan_prompt(
            self.ideas,
            self.tasks,
            self.reference,
            "Asia/Shanghai",
            "帮我安排",
            None,
            [],
        )

        self.assertIn("午餐 12:00-13:00", prompt)
        self.assertIn("睡眠时段 23:00-次日 07:00", prompt)
        self.assertIn("赶火车或飞机", prompt)
        self.assertIn("想法不是承诺", prompt)

    def test_prompt_expands_plan_requests_into_concrete_work(self) -> None:
        prompt = _build_plan_prompt(
            [],
            [],
            self.reference,
            "Asia/Shanghai",
            "给我制定一天的考研学习计划",
            None,
            [],
        )

        self.assertIn("直接产出并排好可执行内容", prompt)
        self.assertIn("禁止输出“制定规划、列出科目、细化计划", prompt)
        self.assertIn("英语、数学或专业课等具体复习/练习时段", prompt)
        self.assertIn("不要用准备计划代替真正执行", prompt)

    def test_normalize_filters_unknown_and_duplicate_sources(self) -> None:
        raw = {
            "summary": "留出吃饭和休息时间。",
            "items": [
                {
                    "source_type": "idea",
                    "source_id": 3,
                    "title": "整理照片",
                    "start_at": "2026-10-08T14:00:00+08:00",
                    "due_at": "2026-10-08T15:00:00+08:00",
                    "duration_minutes": 60,
                    "steps": ["筛选照片"],
                },
                {"source_type": "idea", "source_id": 3, "title": "重复"},
                {"source_type": "idea", "source_id": 999, "title": "伪造来源"},
            ],
        }

        result = _normalize_proposal(raw, self.ideas, self.tasks, self.reference)

        self.assertEqual(len(result["items"]), 1)
        self.assertEqual(result["items"][0]["title"], "整理照片")
        self.assertEqual(result["items"][0]["steps"], [{"title": "筛选照片"}])

    def test_normalize_allows_direct_prompt_items_when_requested(self) -> None:
        raw = {
            "summary": "按你的描述安排。",
            "items": [
                {"source_type": "prompt", "source_id": 1, "title": "练习吉他"},
                {"source_type": "prompt", "source_id": 31, "title": "越界编号"},
            ],
        }

        blocked = _normalize_proposal(raw, [], [], self.reference)
        allowed = _normalize_proposal(
            raw,
            [],
            [],
            self.reference,
            allow_prompt_items=True,
        )

        self.assertEqual(blocked["items"], [])
        self.assertEqual(len(allowed["items"]), 1)
        self.assertEqual(allowed["items"][0]["source_type"], "prompt")

    def test_prompt_explains_recurrence(self) -> None:
        prompt = _build_plan_prompt([], [], self.reference, "Asia/Shanghai", "每周三晚上组会", None, [])
        self.assertIn("重复规则", prompt)
        self.assertIn('"recurrence"', prompt)
        self.assertIn("不要把每一次都单独列出来", prompt)

    def test_normalize_keeps_valid_recurrence_only_with_a_due_time(self) -> None:
        due = "2026-10-14T20:00:00+08:00"
        raw = {
            "summary": "",
            "items": [
                {"source_type": "prompt", "source_id": 1, "title": "组会", "due_at": due, "recurrence": "Weekly"},
                {"source_type": "prompt", "source_id": 2, "title": "背单词", "recurrence": "daily"},
                {"source_type": "prompt", "source_id": 3, "title": "跑步", "due_at": due, "recurrence": "hourly"},
                {"source_type": "prompt", "source_id": 4, "title": "交报告", "due_at": due},
            ],
        }
        items = _normalize_proposal(raw, [], [], self.reference, allow_prompt_items=True)["items"]
        rules = {item["title"]: item["recurrence"] for item in items}
        self.assertEqual(rules, {"组会": "weekly", "背单词": None, "跑步": None, "交报告": None})


if __name__ == "__main__":
    unittest.main()
