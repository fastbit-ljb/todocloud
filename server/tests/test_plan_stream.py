import asyncio
import json
import unittest
from unittest import mock

import httpx

from app.ai_parser import AiParseError
from app.api.plans import PlanChatResponse, _stream_response
from app.core.config import settings
from app.plan_stream import PlanTextProjection, plan_turn_stream, sse_data


def packet(content="", **choice):
    return ("data: " + json.dumps({"choices": [{"delta": {"content": content}, **choice}]}, ensure_ascii=False) + "\n\n").encode()


class Chunks(httpx.AsyncByteStream):
    def __init__(self, parts, gate=None, broken=False):
        self.parts, self.gate, self.broken = parts, gate, broken
        self.closed = False

    async def __aiter__(self):
        for index, part in enumerate(self.parts):
            if index == 1 and self.gate:
                await self.gate.wait()
            yield part
        if self.broken:
            raise httpx.ReadError("simulated disconnect")

    async def aclose(self):
        self.closed = True


class ProjectionTests(unittest.TestCase):
    def test_all_single_character_splits_decode_escapes_and_not_nested_fields(self):
        raw = r'```json' + '\n' + r'{"intent":"answer","items":[{"reply":"不能泄漏"}],"reply":"先做\n\u4e00件\"小事\"\ud83d\udca1","summary":"不用展示"}'
        projection = PlanTextProjection()
        for char in raw:
            projection.feed(char)
            self.assertNotIn("不能泄漏", projection.text)
            self.assertFalse(any(0xD800 <= ord(c) <= 0xDFFF for c in projection.text))
        self.assertEqual(projection.text, '先做\n一件"小事"💡')

    def test_plan_shows_summary_only(self):
        projection = PlanTextProjection()
        projection.feed('{"intent":"plan","reply":"其他说明","summary":"先测试登录","items":[{"title":"不显示 JSON"}]}')
        self.assertEqual(projection.text, "先测试登录")


class StreamTests(unittest.IsolatedAsyncioTestCase):
    def config(self):
        return mock.patch.multiple(settings, ai_api_key="test-key-not-real", ai_api_base_url="https://provider.invalid/v1", ai_model="test-model", ai_fallback_model="")

    def model(self, stream, seen):
        def handle(request):
            seen.append(json.loads(request.content))
            return httpx.Response(200, headers={"Content-Type": "text/event-stream"}, stream=stream)
        return httpx.AsyncClient(transport=httpx.MockTransport(handle))

    def events(self):
        return plan_turn_stream([], [], "2026-10-20T14:00:00Z", "Asia/Shanghai", "给我建议")

    async def test_text_arrives_before_model_finishes_and_plan_is_complete_only_once(self):
        gate = asyncio.Event()
        stream = Chunks([packet('{"intent":"answer","reply":"先做'), packet('一件事"}'), packet(finish_reason="stop"), b'data: [DONE]\n\n'], gate)
        seen = []
        client = self.model(stream, seen)
        with self.config(), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            events = self.events()
            first = await anext(events)
            self.assertEqual(first, {"text": "先做"})
            self.assertFalse(gate.is_set())
            gate.set()
            rest = [event async for event in events]
        self.assertEqual(rest[-1]["turn"], {"intent": "answer", "reply": "先做一件事", "proposal": None})
        self.assertEqual(sum("turn" in e for e in rest), 1)
        self.assertTrue(seen[0]["stream"])
        self.assertTrue(stream.closed)

    async def test_glm_stream_disables_thinking_and_preserves_image_input(self):
        stream = Chunks([packet('{"intent":"answer","reply":"看到了测试图片"}', finish_reason="stop"), b'data: [DONE]\n\n'])
        seen = []
        client = self.model(stream, seen)
        image = {"type": "image_url", "image_url": {"url": "data:image/png;base64,dGVzdA=="}}
        with self.config(), mock.patch.multiple(
            settings, ai_api_base_url="https://open.bigmodel.cn/api/paas/v4/",
            ai_model="glm-4.6v-flash", ai_thinking_mode="disabled", ai_reasoning_effort="low",
        ), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            from app.plan_stream import _model_stream
            events = [event async for event in _model_stream("合成测试", [image])]
        self.assertEqual(events[-1]["raw"]["reply"], "看到了测试图片")
        self.assertEqual(len(seen), 1)
        self.assertEqual(seen[0]["model"], "glm-4.6v-flash")
        self.assertEqual(seen[0]["thinking"], {"type": "disabled"})
        self.assertNotIn("reasoning_effort", seen[0])
        self.assertTrue(seen[0]["stream"])
        self.assertEqual(seen[0]["messages"][1]["content"][1], image)

    async def test_disconnect_after_partial_does_not_retry_or_return_a_plan(self):
        stream = Chunks([packet('{"intent":"answer","reply":"先做')], broken=True)
        seen = []
        client = self.model(stream, seen)
        with self.config(), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            events = self.events()
            self.assertIn("text", await anext(events))
            with self.assertRaises(AiParseError):
                await anext(events)
        self.assertEqual(len(seen), 1)
        self.assertTrue(stream.closed)

    async def test_deepseek_stream_uses_cost_cap_and_disables_thinking(self):
        stream = Chunks([packet('{"intent":"answer","reply":"ok"}', finish_reason="stop"), b'data: [DONE]\n\n'])
        seen = []
        client = self.model(stream, seen)
        with self.config(), mock.patch.multiple(
            settings, ai_api_base_url="https://api.deepseek.com/", ai_model="deepseek-flash",
            ai_thinking_mode="disabled", ai_max_output_tokens=4096, ai_primary_attempts=1,
        ), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            events = [event async for event in self.events()]
        self.assertEqual(events[-1]["turn"]["reply"], "ok")
        self.assertEqual(len(seen), 1)
        self.assertEqual(seen[0]["max_tokens"], 4096)
        self.assertEqual(seen[0]["thinking"], {"type": "disabled"})
        self.assertNotIn("reasoning_effort", seen[0])

    async def test_deepseek_payment_error_never_retries_or_uses_fallback(self):
        seen = []
        def handle(req):
            seen.append(req)
            return httpx.Response(402, json={"error": {"message": "fixture"}})
        client = httpx.AsyncClient(transport=httpx.MockTransport(handle))
        with self.config(), mock.patch.multiple(settings, ai_fallback_model="must-not-call", ai_primary_attempts=2), \
                mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            with self.assertRaisesRegex(AiParseError, "额度不足"):
                _ = [event async for event in self.events()]
        self.assertEqual(len(seen), 1)

    async def test_one_attempt_stream_does_not_retry_or_sleep(self):
        seen = []
        def handle(req):
            seen.append(req)
            return httpx.Response(503, json={"error": {"message": "fixture"}})
        client = httpx.AsyncClient(transport=httpx.MockTransport(handle))
        with self.config(), mock.patch.object(settings, "ai_primary_attempts", 1), \
                mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client), \
                mock.patch("app.plan_stream.asyncio.sleep", new=mock.AsyncMock()) as sleep:
            with self.assertRaises(AiParseError):
                _ = [event async for event in self.events()]
        self.assertEqual(len(seen), 1)
        sleep.assert_not_awaited()

    async def test_length_finish_is_not_a_valid_plan_even_with_parseable_json(self):
        stream = Chunks([packet('{"intent":"answer","reply":"x"}', finish_reason="length"), b'data: [DONE]\n\n'])
        seen = []
        client = self.model(stream, seen)
        with self.config(), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client), mock.patch("app.plan_stream.asyncio.sleep", new=mock.AsyncMock()):
            with self.assertRaises(AiParseError):
                _ = [event async for event in self.events()]

    async def test_reasoning_role_and_usage_packets_are_not_reply_text(self):
        reasoning = b'data: {"choices":[{"delta":{"role":"assistant","reasoning_content":"private reasoning"}}]}\n\n'
        stream = Chunks([reasoning, b'data: {"choices":[],"usage":{}}\n\n', packet('{"intent":"answer","reply":"ok"}', finish_reason="stop"), b'data: [DONE]\n\n'])
        client = self.model(stream, [])
        with self.config(), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            result = [event async for event in self.events()]
        self.assertNotIn("private reasoning", str(result))
        self.assertEqual(result[-1]["turn"]["reply"], "ok")

    async def test_provider_ignoring_stream_does_not_save_truncated_json(self):
        seen = []
        def handle(req):
            seen.append(req)
            return httpx.Response(200, json={"choices": [{"finish_reason": "length", "message": {
                "content": '{"intent":"answer","reply":"incomplete"}'}}]})
        client = httpx.AsyncClient(transport=httpx.MockTransport(handle))
        with self.config(), mock.patch.object(settings, "ai_primary_attempts", 1), \
                mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            with self.assertRaises(AiParseError):
                _ = [event async for event in self.events()]
        self.assertEqual(len(seen), 1)

    async def test_cancel_closes_provider_connection(self):
        stream = Chunks([packet('{"intent":"answer","reply":"先做'), packet('一件事"}')], asyncio.Event())
        client = self.model(stream, [])
        with self.config(), mock.patch("app.plan_stream.httpx.AsyncClient", return_value=client):
            events = self.events()
            await anext(events)
            waiting = asyncio.create_task(anext(events))
            await asyncio.sleep(0)
            waiting.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await waiting
        self.assertTrue(stream.closed)

    async def test_sse_comments_multiline_data_and_eof(self):
        async def lines():
            for line in [": ping", "event: ignored", "data: {", 'data: "a":1}', "", "data: last"]:
                yield line
        self.assertEqual([value async for value in sse_data(lines())], ['{\n"a":1}', 'last'])

    async def test_api_does_not_save_partial_and_closes_producer_on_disconnect(self):
        closed = asyncio.Event()
        async def provider():
            try:
                yield {"text": "先做"}
                await asyncio.Event().wait()
            finally:
                closed.set()
        save = mock.AsyncMock()
        response = _stream_response(provider(), save)
        body = response.body_iterator
        self.assertIn("event: start", await anext(body))
        self.assertIn("event: text", await anext(body))
        save.assert_not_awaited()
        await body.aclose()
        await asyncio.wait_for(closed.wait(), 1)

    async def test_api_has_one_final_result_after_save(self):
        async def provider():
            yield {"text": "先做"}
            yield {"turn": {"intent": "answer", "reply": "先做", "proposal": None}}
        save = mock.AsyncMock(return_value=PlanChatResponse(kind="answer", reply="先做"))
        chunks = [part async for part in _stream_response(provider(), save).body_iterator]
        self.assertEqual(sum("event: result" in part for part in chunks), 1)
        save.assert_awaited_once()

    async def test_api_error_never_saves_a_partial_turn(self):
        async def provider():
            yield {"text": "先做"}
            raise AiParseError("模拟断流")
        save = mock.AsyncMock()
        chunks = [part async for part in _stream_response(provider(), save).body_iterator]
        self.assertIn("event: error", chunks[-1])
        self.assertFalse(any("event: result" in part for part in chunks))
        save.assert_not_awaited()
