"""Inspect compatible request bodies using fixtures, never real APIs."""
import io
import json
import unittest
from unittest import mock

from app.ai_parser import AiParseError, _call_model, _localize_reference
from app.core.config import settings
from app.plan_ai import _call_plan_model


class ProviderRequestTests(unittest.TestCase):
    providers = (("https://open.bigmodel.cn/api/paas/v4/", "glm-4.6v-flash"),
                 ("https://api.deepseek.com/", "deepseek-flash"))

    def config(self, base_url="https://api.deepseek.com/", model="deepseek-flash"):
        return mock.patch.multiple(
            settings, ai_api_base_url=base_url,
            ai_model=model, ai_fallback_model="",
            ai_api_key="fixture-key-not-real", ai_thinking_mode="disabled",
            ai_reasoning_effort="low", ai_max_output_tokens=4096, ai_primary_attempts=1,
        )

    def response(self, result):
        return io.BytesIO(json.dumps({"choices": [{
            "finish_reason": "stop",
            "message": {"content": json.dumps(result, ensure_ascii=False)},
        }]}).encode())

    def assert_provider_request(self, call, base_url, model):
        req = call.call_args.args[0]
        self.assertEqual(req.full_url, base_url.rstrip("/") + "/chat/completions")
        payload = json.loads(req.data)
        self.assertEqual(payload["model"], model)
        self.assertEqual(payload["max_tokens"], 4096)
        self.assertEqual(payload["thinking"], {"type": "disabled"})
        self.assertNotIn("reasoning_effort", payload)
        call.assert_called_once()
        return payload

    def test_nonstream_plan_supports_text_and_images_without_thinking(self):
        image = {"type": "image_url", "image_url": {"url": "data:image/png;base64,dGVzdA=="}}
        for base_url, model in self.providers:
            for images in (None, [image]):
                with self.subTest(model=model, images=bool(images)), self.config(base_url, model), mock.patch(
                    "app.plan_ai.request.urlopen",
                    return_value=self.response({"intent": "answer", "reply": "合成测试"}),
                ) as call:
                    self.assertEqual(_call_plan_model("测试", images)["reply"], "合成测试")
                    payload = self.assert_provider_request(call, base_url, model)
                    content = payload["messages"][1]["content"]
                    self.assertEqual(content, [{"type": "text", "text": "测试"}, image] if images else "测试")

    def test_task_extraction_supports_text_and_images_without_thinking(self):
        reference = _localize_reference("2026-10-20T14:00:00Z", "Asia/Shanghai")
        image = {"type": "image_url", "image_url": {"url": "data:image/png;base64,dGVzdA=="}}
        for base_url, model in self.providers:
            for content in ("明天整理合成测试资料", image):
                with self.subTest(model=model, image=isinstance(content, dict)), self.config(base_url, model), mock.patch(
                    "app.ai_parser.request.urlopen", return_value=self.response({"tasks": []}),
                ) as call:
                    self.assertEqual(_call_model(content, reference, "Asia/Shanghai")["tasks"], [])
                    payload = self.assert_provider_request(call, base_url, model)
                    if isinstance(content, dict):
                        self.assertEqual(payload["messages"][1]["content"][1], image)

    def test_payment_error_never_retries_or_uses_fallback(self):
        from urllib.error import HTTPError
        reference = _localize_reference("2026-10-20T14:00:00Z", "Asia/Shanghai")
        for caller in (lambda: _call_plan_model("fixture"),
                       lambda: _call_model("fixture", reference, "Asia/Shanghai")):
            with self.subTest(caller=caller), self.config(), mock.patch.multiple(
                settings, ai_primary_attempts=2, ai_fallback_model="must-not-call"), mock.patch(
                "urllib.request.urlopen", side_effect=HTTPError("https://api.deepseek.com", 402, "Payment Required", {}, io.BytesIO(b"{}"))) as call:
                with self.assertRaisesRegex(AiParseError, "额度不足"):
                    caller()
                call.assert_called_once()

    def test_one_attempt_does_not_replay_a_transient_failure(self):
        with self.config(), mock.patch("app.plan_ai.request.urlopen", side_effect=TimeoutError()) as call:
            with self.assertRaises(AiParseError):
                _call_plan_model("fixture")
            call.assert_called_once()

    def test_parser_truncation_does_not_exceed_cap_or_repeat_same_output(self):
        response = self.response({"tasks": []})
        body = json.loads(response.getvalue())
        body["choices"][0]["finish_reason"] = "length"
        reference = _localize_reference("2026-10-20T14:00:00Z", "Asia/Shanghai")
        with self.config(), mock.patch.object(settings, "ai_primary_attempts", 2), mock.patch(
            "app.ai_parser.request.urlopen", return_value=io.BytesIO(json.dumps(body).encode())) as call:
            with self.assertRaises(AiParseError):
                _call_model("fixture", reference, "Asia/Shanghai")
            call.assert_called_once()
            self.assertEqual(json.loads(call.call_args.args[0].data)["max_tokens"], 4096)

    def test_truncated_plan_is_not_accepted_even_if_json_parses(self):
        body = {"choices": [{"finish_reason": "length", "message": {
            "content": '{"intent":"plan","summary":"partial","items":[]}'}}]}
        with self.config(), mock.patch("app.plan_ai.request.urlopen", return_value=io.BytesIO(json.dumps(body).encode())) as call:
            with self.assertRaisesRegex(AiParseError, "过长"):
                _call_plan_model("fixture")
            call.assert_called_once()

