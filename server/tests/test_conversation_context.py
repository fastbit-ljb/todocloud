import unittest

from app.conversation_context import reference_context
from app.api.conversations import ConversationWrite
from pydantic import ValidationError


class ConversationContextTests(unittest.TestCase):
    def test_empty_references_leave_prompt_unchanged(self):
        self.assertEqual(reference_context([]), "")

    def test_references_are_bounded_and_marked_read_only(self):
        records = [{"title": f"会话 {i}", "messages": [{"role": "user", "content": "x" * 5000}] * 30} for i in range(10)]
        context = reference_context(records)
        self.assertIn("只读参考资料", context)
        self.assertIn("当前任务为准", context)
        self.assertLess(len(context), 18_200)
        self.assertNotIn("会话 3", context)

    def test_conversation_write_validates_role_reference_count_and_size(self):
        for messages in (
            [{"role": "system", "content": "x"}],
            [{"role": "user", "content": "x" * 32_001}],
            [{"role": "user", "content": "x", "references": [{"id": "ffffffff-ffff-ffff-ffff-ffffffffffff", "title": "x"}] * 4}],
            [{"role": "user", "content": "x" * 32_000}] * 32,
        ):
            with self.assertRaises(ValidationError):
                ConversationWrite(title="x", messages=messages)
