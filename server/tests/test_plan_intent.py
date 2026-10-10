import unittest

from app.ai_parser import _localize_reference
from app.plan_ai import ANSWER_FALLBACK, _build_plan_prompt, _normalize_turn


class PlanIntentTests(unittest.TestCase):
    def setUp(self) -> None:
        self.reference = _localize_reference("2026-10-08T01:00:00Z", "Asia/Shanghai")
        self.ideas = [{"id": 3, "content": "整理旅行照片"}]
        self.tasks = [{"id": 7, "title": "赶晚上十点的火车"}]
        self.plan_raw = {
            "intent": "plan",
            "summary": "先整理照片",
            "items": [{"source_type": "idea", "source_id": 3, "title": "整理旅行照片"}],
        }

    def _turn(self, raw: dict, force_plan: bool = False) -> dict:
        return _normalize_turn(
            raw, self.ideas, self.tasks, self.reference,
            force_plan=force_plan, allow_prompt_items=True,
        )

    def test_answer_intent_returns_reply_without_plan(self) -> None:
        turn = self._turn({"intent": "answer", "reply": "这周任务不多，可以留出周末。"})
        self.assertEqual(turn["intent"], "answer")
        self.assertIsNone(turn["proposal"])
        self.assertEqual(turn["reply"], "这周任务不多，可以留出周末。")

    def test_answer_wins_even_if_model_also_returned_items(self) -> None:
        turn = self._turn({**self.plan_raw, "intent": "answer", "reply": "建议先休息"})
        self.assertIsNone(turn["proposal"])

    def test_plan_intent_returns_proposal(self) -> None:
        turn = self._turn(self.plan_raw)
        self.assertEqual(turn["intent"], "plan")
        self.assertEqual([i["source_id"] for i in turn["proposal"]["items"]], [3])

    def test_missing_or_unknown_intent_falls_back_to_answer(self) -> None:
        for raw in ({"summary": "只有总结", "items": self.plan_raw["items"]}, {"intent": "???"}):
            turn = self._turn(raw)
            self.assertEqual(turn["intent"], "answer")
            self.assertIsNone(turn["proposal"])
        self.assertEqual(self._turn({})["reply"], ANSWER_FALLBACK)

    def test_plan_intent_with_no_usable_items_becomes_answer(self) -> None:
        turn = self._turn({"intent": "plan", "summary": "需要更多信息", "items": []})
        self.assertEqual(turn["intent"], "answer")
        self.assertEqual(turn["reply"], "需要更多信息")

    def test_force_plan_always_plans(self) -> None:
        turn = self._turn({"intent": "answer", "reply": "x", "items": []}, force_plan=True)
        self.assertEqual(turn["intent"], "plan")
        self.assertEqual(turn["proposal"]["items"], [])

    def test_prompt_lets_model_answer_only_when_not_forced(self) -> None:
        args = (self.ideas, self.tasks, self.reference, "Asia/Shanghai", "考研英语怎么复习", None, [])
        free = _build_plan_prompt(*args, force_plan=False)
        forced = _build_plan_prompt(*args, force_plan=True)
        self.assertIn('intent = "answer"', free)
        self.assertIn("拿不准时选", free)
        self.assertIn('intent 必须是 "plan"', forced)
        self.assertNotIn("拿不准时选", forced)
        # Existing planning rules are still part of both prompts.
        for prompt in (free, forced):
            self.assertIn("午餐 12:00-13:00", prompt)
            self.assertNotIn("附带了", prompt)

    def test_prompt_mentions_images_only_when_attached(self) -> None:
        args = (self.ideas, self.tasks, self.reference, "Asia/Shanghai", "按课表排", None, [])
        prompt = _build_plan_prompt(*args, force_plan=False, image_count=2)
        self.assertIn("附带了 2 张图片", prompt)
        self.assertIn("不要编造", prompt)


if __name__ == "__main__":
    unittest.main()
