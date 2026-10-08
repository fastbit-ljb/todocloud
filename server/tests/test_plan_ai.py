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


if __name__ == "__main__":
    unittest.main()
