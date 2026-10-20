"""Inspect GLM request bodies using fixtures, never real credentials or APIs."""
import io
import json
import unittest
from unittest import mock

from app.ai_parser import _call_model, _localize_reference
from app.core.config import settings
from app.plan_ai import _call_plan_model


class GlmRequestTests(unittest.TestCase):
    def config(self):
        return mock.patch.multiple(
            settings, ai_api_base_url="https://open.bigmodel.cn/api/paas/v4/",
            ai_model="glm-4.6v-flash", ai_fallback_model="",
            ai_api_key="fixture-key-not-real", ai_thinking_mode="disabled",
            ai_reasoning_effort="low",
        )

    def response(self, result):
        return io.BytesIO(json.dumps({"choices": [{
            "finish_reason": "stop",
            "message": {"content": json.dumps(result, ensure_ascii=False)},
        }]}).encode())

    def assert_glm_request(self, call):
        req = call.call_args.args[0]
        self.assertEqual(req.full_url, "https://open.bigmodel.cn/api/paas/v4/chat/completions")
        payload = json.loads(req.data)
        self.assertEqual(payload["model"], "glm-4.6v-flash")
        self.assertEqual(payload["thinking"], {"type": "disabled"})
        self.assertNotIn("reasoning_effort", payload)
        call.assert_called_once()
        return payload

    def test_nonstream_plan_supports_text_and_images_without_thinking(self):
        image = {"type": "image_url", "image_url": {"url": "data:image/png;base64,dGVzdA=="}}
        for images in (None, [image]):
            with self.subTest(images=bool(images)), self.config(), mock.patch(
                "app.plan_ai.request.urlopen",
                return_value=self.response({"intent": "answer", "reply": "合成测试"}),
            ) as call:
                self.assertEqual(_call_plan_model("测试", images)["reply"], "合成测试")
                payload = self.assert_glm_request(call)
                content = payload["messages"][1]["content"]
                self.assertEqual(content, [{"type": "text", "text": "测试"}, image] if images else "测试")

    def test_task_extraction_supports_text_and_images_without_thinking(self):
        reference = _localize_reference("2026-10-20T14:00:00Z", "Asia/Shanghai")
        image = {"type": "image_url", "image_url": {"url": "data:image/png;base64,dGVzdA=="}}
        for content in ("明天整理合成测试资料", image):
            with self.subTest(image=isinstance(content, dict)), self.config(), mock.patch(
                "app.ai_parser.request.urlopen", return_value=self.response({"tasks": []}),
            ) as call:
                self.assertEqual(_call_model(content, reference, "Asia/Shanghai")["tasks"], [])
                payload = self.assert_glm_request(call)
                if isinstance(content, dict):
                    self.assertEqual(payload["messages"][1]["content"][1], image)

