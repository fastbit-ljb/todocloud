"""Real model streaming; only complete, normalized turns may become saved plans."""
import asyncio
import base64
import json
import logging
import time
from contextlib import aclosing

import httpx

from app.ai_parser import AiNotConfiguredError, AiParseError, _extract_json, _localize_reference, _prepare_image_for_ai
from app.core.config import settings
from app.plan_ai import _build_plan_prompt, _normalize_turn

logger = logging.getLogger("uvicorn.error")
MAX_STREAM_CHARS = 1_000_000
STREAM_BUDGET_SECONDS = 110


class PlanTextProjection:
    """Incrementally decode *top-level* reply/summary, never JSON or task fields.

    Handles fences, escaped quotes and split Unicode escapes without guessing a
    partial JSON object or exposing a provider's reasoning_content.
    """
    def __init__(self):
        self.depth = 0
        self.in_string = False
        self.expect_key = False
        self.want_value = False
        self.mode = ""
        self.key = ""
        self.key_chars = []
        self.values = {}
        self.escaped = False
        self.unicode_digits = None
        self.high_surrogate = None

    def _append(self, char):
        if self.mode == "key":
            self.key_chars.append(char)
        elif self.mode == "value":
            code = ord(char)
            if 0xD800 <= code <= 0xDBFF:
                self.high_surrogate = code
                return
            if self.high_surrogate is not None:
                if 0xDC00 <= code <= 0xDFFF:
                    char = chr(0x10000 + ((self.high_surrogate - 0xD800) << 10) + code - 0xDC00)
                self.high_surrogate = None
            if not 0xDC00 <= ord(char) <= 0xDFFF:
                self.values[self.key].append(char)

    def feed(self, text):
        for char in text:
            if self.in_string:
                if self.unicode_digits is not None:
                    self.unicode_digits += char
                    if len(self.unicode_digits) == 4:
                        try:
                            self._append(chr(int(self.unicode_digits, 16)))
                        except ValueError as exc:
                            raise AiParseError("AI 返回的文字格式无效") from exc
                        self.unicode_digits = None
                elif self.escaped:
                    self.escaped = False
                    if char == "u":
                        self.unicode_digits = ""
                    else:
                        self._append({"n": "\n", "r": "\r", "t": "\t", "b": "\b", "f": "\f"}.get(char, char))
                elif char == "\\":
                    self.escaped = True
                elif char == '"':
                    self.in_string = False
                    if self.mode == "key":
                        self.key = "".join(self.key_chars)
                        self.expect_key = False
                    else:
                        self.want_value = False
                    self.mode = ""
                    self.high_surrogate = None
                else:
                    self._append(char)
                continue
            if char in "{[":
                self.depth += 1
                if self.depth == 1:
                    self.expect_key = True
            elif char in "}]":
                self.depth -= 1
            elif char == '"' and self.depth > 0:
                self.in_string = True
                self.mode = "key" if self.depth == 1 and self.expect_key else ""
                if self.mode == "key":
                    self.key_chars = []
                elif self.depth == 1 and self.want_value and self.key in {"intent", "reply", "summary"}:
                    self.mode = "value"
                    self.values[self.key] = []
            elif self.depth == 1 and char == ":":
                self.want_value = True
            elif self.depth == 1 and char == ",":
                self.expect_key = True
                self.want_value = False

    @property
    def text(self):
        value = lambda key: "".join(self.values.get(key, []))
        if value("intent").strip().lower() == "plan":
            return value("summary")[:1000]
        return (value("reply") or value("summary"))[:4000]


async def sse_data(lines):
    """SSE framing, including comments, multi-line data and an EOF final event."""
    data = []
    size = 0
    async for line in lines:
        if not line:
            if data:
                yield "\n".join(data)
            data, size = [], 0
        elif line.startswith("data:"):
            value = line[5:].removeprefix(" ")
            size += len(value)
            if size > MAX_STREAM_CHARS:
                raise AiParseError("AI 返回内容过长")
            data.append(value)
    if data:
        yield "\n".join(data)


async def _model_stream(prompt, image_parts):
    if not settings.ai_api_key:
        raise AiNotConfiguredError("服务端尚未配置 AI_API_KEY")
    models = list(dict.fromkeys(filter(None, [settings.ai_model, settings.ai_fallback_model.strip()])))
    last_code = None
    last_error = None
    started = time.monotonic()
    async with httpx.AsyncClient(timeout=httpx.Timeout(90, connect=15)) as client:
        for model_index, model in enumerate(models):
            for attempt in range(2 if model_index == 0 else 1):
                received_content = False
                payload = {
                    "model": model, "stream": True, "max_tokens": 8192,
                    "messages": [
                        {"role": "system", "content": "你是尊重生活节律、善于留白的个人计划助手。"},
                        {"role": "user", "content": ([{"type": "text", "text": prompt}, *image_parts] if image_parts else prompt)},
                    ],
                }
                if "generativelanguage.googleapis.com" in settings.ai_api_base_url and settings.ai_reasoning_effort:
                    payload["reasoning_effort"] = settings.ai_reasoning_effort
                try:
                    async with client.stream(
                        "POST", settings.ai_api_base_url.rstrip("/") + "/chat/completions",
                        headers={"Authorization": f"Bearer {settings.ai_api_key}"}, json=payload,
                    ) as response:
                        last_code = response.status_code
                        response.raise_for_status()
                        if "text/event-stream" not in response.headers.get("content-type", ""):
                            # Some compatible providers ignore stream=true. Do not issue a second request.
                            body = await response.aread()
                            if len(body) > MAX_STREAM_CHARS * 4:
                                raise AiParseError("AI 返回内容过长")
                            yield {"raw": _extract_json(json.loads(body)["choices"][0]["message"]["content"])}
                            return
                        content = []
                        length = 0
                        projection = PlanTextProjection()
                        sent = ""
                        last_sent = 0.0
                        finished = False
                        first_text = None
                        async with aclosing(sse_data(response.aiter_lines())) as events:
                            async for data in events:
                                if data == "[DONE]":
                                    finished = True
                                    break
                                chunk = json.loads(data)
                                if chunk.get("error"):
                                    raise AiParseError("AI 回复中断，请重试")
                                choices = chunk.get("choices") or []
                                if not choices:
                                    continue  # usage-only packet
                                choice = choices[0]
                                reason = choice.get("finish_reason")
                                if reason and reason != "stop":
                                    raise AiParseError("AI 回复未完成，请重试")
                                finished |= reason == "stop"
                                delta = choice.get("delta") or {}
                                text = delta.get("content") or ""
                                if not isinstance(text, str):
                                    raise AiParseError("AI 返回的文字格式无效")
                                if not text:
                                    continue  # role-only / reasoning_content packet
                                received_content = True
                                length += len(text)
                                if length > MAX_STREAM_CHARS:
                                    raise AiParseError("AI 返回内容过长")
                                content.append(text)
                                projection.feed(text)
                                shown = projection.text
                                now = time.monotonic()
                                if shown and shown != sent and now - last_sent >= 0.05:
                                    if first_text is None:
                                        first_text = now - started
                                    yield {"text": shown}
                                    sent, last_sent = shown, now
                        if not finished:
                            raise AiParseError("AI 回复中断，请重试")
                        if projection.text and projection.text != sent:
                            yield {"text": projection.text}
                        raw = _extract_json("".join(content))
                        logger.info("AI planning stream completed model=%s first_text=%s total=%.2fs", model, first_text, time.monotonic() - started)
                        yield {"raw": raw}
                        return
                except httpx.HTTPStatusError as exc:
                    last_code = exc.response.status_code
                    logger.warning("AI planning stream HTTP %s model=%s elapsed=%.2fs", last_code, model, time.monotonic() - started)
                    if last_code in {401, 403}:
                        raise AiParseError("AI 服务鉴权失败，请检查服务端配置") from exc
                    if last_code not in {408, 429, 500, 502, 503, 504}:
                        break
                except (httpx.RequestError, AiParseError, ValueError, KeyError, IndexError, TypeError) as exc:
                    if received_content:
                        # Never silently replay a generation once partial output has been shown.
                        raise AiParseError("AI 回复中断，未保存新计划，请重试") from exc
                    last_error = exc
                if model_index == 0 and attempt == 0:
                    await asyncio.sleep(1)
    if last_code == 429:
        raise AiParseError("AI 计划请求较多，请稍后再试")
    if last_code in {400, 404}:
        raise AiParseError("AI 计划模型配置无效，请检查服务端配置")
    raise AiParseError("AI 计划服务暂时不可用，请稍后重试") from last_error


async def plan_turn_stream(ideas, tasks, reference_at, timezone_name, user_message,
                           previous_proposal=None, conversation=None, force_plan=False, images=None):
    reference = _localize_reference(reference_at, timezone_name)
    prompt = _build_plan_prompt(ideas, tasks, reference, timezone_name, user_message,
                                previous_proposal, conversation or [], force_plan, len(images or []))
    parts = []
    for image_bytes, content_type in images or []:
        data, kind = await asyncio.to_thread(_prepare_image_for_ai, image_bytes, content_type)
        parts.append({"type": "image_url", "image_url": {"url": f"data:{kind};base64,{base64.b64encode(data).decode('ascii')}"}})
    try:
        async with asyncio.timeout(STREAM_BUDGET_SECONDS):
            async with aclosing(_model_stream(prompt, parts)) as events:
                async for event in events:
                    if "text" in event:
                        yield event
                    else:
                        turn = _normalize_turn(event["raw"], ideas, tasks, reference, force_plan, bool(user_message.strip()))
                        yield {"turn": turn}
    except TimeoutError as exc:
        raise AiParseError("AI 回复等待超时，未保存新计划，请重试") from exc
