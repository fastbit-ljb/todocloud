import asyncio
import base64
import io
import json
import logging
import re
import time
from datetime import datetime, timezone
from urllib import error as urlerror, request

from PIL import Image

from app.core.config import settings


logger = logging.getLogger(__name__)
MAX_AI_IMAGE_EDGE = 2048
AI_PROVIDER_TIMEOUT_SECONDS = 30
AI_PRIMARY_ATTEMPTS = 2


class AiNotConfiguredError(RuntimeError):
    pass


class AiParseError(RuntimeError):
    pass


def _extract_json(content: str) -> dict:
    if not isinstance(content, str):
        raise AiParseError("AI 返回的任务结果不是有效 JSON")

    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", content, re.DOTALL | re.IGNORECASE)
    candidates = [fenced.group(1)] if fenced else []
    candidates.append(content.strip().lstrip("\ufeff"))
    decoder = json.JSONDecoder()

    for candidate in candidates:
        if not candidate:
            continue
        try:
            parsed = json.loads(candidate)
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError:
            pass

        # Models sometimes add a short sentence before/after JSON. Use the
        # decoder's raw_decode so nested step objects are not cut at the first
        # closing brace, unlike a non-greedy regular expression.
        start = candidate.find("{")
        while start >= 0:
            try:
                parsed, _ = decoder.raw_decode(candidate[start:])
                if isinstance(parsed, dict):
                    return parsed
            except json.JSONDecodeError:
                pass
            start = candidate.find("{", start + 1)

    raise AiParseError("AI 返回的任务结果不是有效 JSON")


def _normalize_unspecified_morning(task: dict) -> None:
    source_text = str(task.get("source_text") or "")
    if re.search(r"上午|下午|中午|晚上|傍晚|凌晨|\b(?:am|pm)\b", source_text, re.IGNORECASE):
        return
    hour_match = re.search(r"(?<!\d)(1[0-2]|[0-9])\s*(?:点|时|[:：])", source_text)
    due_at = task.get("due_at")
    if hour_match is None or not due_at:
        return
    hour = int(hour_match.group(1))
    try:
        parsed = datetime.fromisoformat(str(due_at).replace("Z", "+00:00"))
    except ValueError:
        return
    if parsed.hour == hour + 12:
        task["due_at"] = parsed.replace(hour=hour).isoformat()


def _infer_ordered_steps(text: str) -> list[dict]:
    """Recover explicit ordered actions when a model leaves steps in description."""
    if not text:
        return []

    marker_pattern = re.compile(
        r"(?:^|[，,。；;\n])\s*(先|首先|然后|接着|再|随后|之后|最后|最终)\s*"
    )
    markers = list(marker_pattern.finditer(text))
    if len(markers) < 2:
        return []

    steps: list[dict] = []
    for index, marker in enumerate(markers):
        end = markers[index + 1].start() if index + 1 < len(markers) else len(text)
        title = text[marker.end():end].strip(" \t\r\n，,。；;")
        if title:
            steps.append({"title": title})
    return steps[:30] if len(steps) >= 2 else []


def _normalize_tasks(result: dict) -> dict:
    for task in result.get("tasks", []):
        if isinstance(task, dict):
            _normalize_unspecified_morning(task)
            normalized_steps = []
            for step in task.get("steps") or []:
                if isinstance(step, str) and step.strip():
                    normalized_steps.append({"title": step.strip()})
                elif isinstance(step, dict) and str(step.get("title") or "").strip():
                    normalized_steps.append({"title": str(step["title"]).strip()})
            if not normalized_steps:
                for source in (task.get("description"), task.get("source_text")):
                    inferred_steps = _infer_ordered_steps(str(source or ""))
                    if inferred_steps:
                        normalized_steps = inferred_steps
                        break
            task["steps"] = normalized_steps[:30]
    return result


def _build_prompt(reference_at: str, timezone_name: str, source_type: str) -> str:
    source = "聊天截图" if source_type == "image" else "聊天文字"
    return (
        f"请从这份{source}中提取需要完成的待办事项。只返回 JSON，不要解释。"
        "格式必须是 {\"tasks\":[{\"title\":\"...\",\"description\":null,"
        "\"due_at\":\"ISO-8601 或 null\",\"reminder_offset_minutes\":null,"
        "\"confidence\":0.0,\"source_text\":\"原文片段\","
        "\"steps\":[{\"title\":\"按顺序执行的步骤\"}]}]}。"
        f"当前参考时间是 {reference_at}，用户时区是 {timezone_name}。"
        "如果截图中能辨认出聊天消息旁的发送日期或时间，优先用该消息时间作为‘明天、下周一’等相对时间的基准；"
        "截图没有可辨认的聊天时间时，才使用当前参考时间。不要把手机状态栏时间当成聊天消息时间。"
        "聊天中出现‘明天、下周一、今晚’等相对时间时，必须以参考时间和用户时区换算成 ISO-8601；"
        "如果只写‘6点’、‘8点’等时间而没有明确上午、下午、晚上、中午、凌晨或 24 小时制，默认按上午处理；"
        "例如‘明天6点把word给我’必须是次日 06:00，不得擅自解释成 18:00；只有‘下午6点’或‘晚上6点’才是 18:00。"
        "没有明确时间就填 null。标题要简短可执行，不要把聊天寒暄当成任务。"
        "如果同一个任务包含多个有先后顺序的动作（例如‘先打开开发者选项，然后打开 USB 调试，再打开 OEM 解锁，最后连接电脑’），"
        "请把主任务放在 title，把每个独立动作按原顺序拆成 steps 数组；每个步骤只保留一个可执行动作。"
        "没有明确的多步骤关系时 steps 必须返回空数组，不要把普通描述强行拆成步骤。"
        "只有确实包含待办行动的内容才提取；不要猜测被遮挡/看不清的字，不要补造任务、对象或时间。"
        "同一事项即使在聊天中重复出现或只是不同说法，也只返回一条，优先保留信息更完整的一条；"
        "但不要合并不同内容或不同截止时间的任务。"
    )


def _prepare_image_for_ai(image_bytes: bytes, content_type: str) -> tuple[bytes, str]:
    try:
        with Image.open(io.BytesIO(image_bytes)) as source:
            if source.format == "JPEG" and max(source.size) <= MAX_AI_IMAGE_EDGE:
                return image_bytes, content_type
            image = source.convert("RGB")
            image.thumbnail((MAX_AI_IMAGE_EDGE, MAX_AI_IMAGE_EDGE), Image.Resampling.LANCZOS)
            output = io.BytesIO()
            image.save(output, format="JPEG", quality=92, optimize=True)
            optimized = output.getvalue()
            original_size = source.size
        if len(optimized) < len(image_bytes) or max(original_size) > MAX_AI_IMAGE_EDGE:
            return optimized, "image/jpeg"
    except Exception:
        logger.warning("Could not optimize screenshot for AI; using original image")
    return image_bytes, content_type


def _call_model(content: object, reference_at: str, timezone_name: str) -> dict:
    if not settings.ai_api_key:
        raise AiNotConfiguredError("服务端尚未配置 AI_API_KEY")

    endpoint = settings.ai_api_base_url.rstrip("/") + "/chat/completions"
    models = [settings.ai_model]
    fallback = settings.ai_fallback_model.strip()
    if fallback and fallback not in models:
        models.append(fallback)

    last_error: Exception | None = None
    for model_index, model in enumerate(models):
        request_payload = {
            "model": model,
            "max_tokens": 4096,
            "messages": [
                {"role": "system", "content": "你是可靠的待办事项抽取助手。"},
                {
                    "role": "user",
                    "content": (
                        [
                            {"type": "text", "text": _build_prompt(reference_at, timezone_name, "image")},
                            content,
                        ]
                        if isinstance(content, dict)
                        else f"{_build_prompt(reference_at, timezone_name, 'text')}\n\n聊天文字：\n{content}"
                    ),
                },
            ],
        }
        if "generativelanguage.googleapis.com" in settings.ai_api_base_url:
            request_payload["reasoning_effort"] = "minimal"
        attempts = AI_PRIMARY_ATTEMPTS if model_index == 0 else 1
        truncated = False
        for attempt in range(attempts):
            if truncated:
                request_payload["max_tokens"] = 8192
            payload = json.dumps(request_payload).encode("utf-8")
            http_request = request.Request(
                endpoint,
                data=payload,
                headers={
                    "Authorization": f"Bearer {settings.ai_api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            started_at = time.monotonic()
            try:
                with request.urlopen(http_request, timeout=AI_PROVIDER_TIMEOUT_SECONDS) as response:
                    result = json.loads(response.read().decode("utf-8"))
                choice = result["choices"][0]
                finish_reason = choice.get("finish_reason")
                logger.info(
                    "AI parse returned with %s in %.2fs (finish_reason=%s)",
                    model,
                    time.monotonic() - started_at,
                    finish_reason,
                )
                if finish_reason == "length":
                    truncated = True
                    raise AiParseError("AI 返回内容被截断")
                content_result = choice["message"]["content"]
                if isinstance(content_result, list):
                    content_result = "".join(
                        part.get("text", "") for part in content_result if isinstance(part, dict)
                    )
                parsed = _extract_json(content_result)
                if not isinstance(parsed.get("tasks"), list):
                    raise AiParseError("AI 返回结果缺少任务列表")
                return _normalize_tasks(parsed)
            except urlerror.HTTPError as exc:
                elapsed = time.monotonic() - started_at
                logger.warning("AI provider returned HTTP %s for %s after %.2fs", exc.code, model, elapsed)
                last_error = exc
                if exc.code in {401, 403}:
                    raise AiParseError("AI 服务鉴权失败，请检查服务端 AI 密钥配置") from exc
                if exc.code in {400, 404}:
                    raise AiParseError("AI 模型请求参数或模型配置无效，请检查服务端模型配置") from exc
                if exc.code not in {408, 429, 500, 502, 503, 504}:
                    break
                if attempt + 1 < attempts:
                    time.sleep(1)
                    continue
                break
            except (TimeoutError, urlerror.URLError) as exc:
                elapsed = time.monotonic() - started_at
                logger.warning("AI provider request failed for %s after %.2fs (%s)", model, elapsed, type(exc).__name__)
                last_error = exc
                if attempt + 1 < attempts:
                    time.sleep(1)
                    continue
                break
            except (AiParseError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
                logger.warning("AI provider returned invalid content for %s (%s)", model, type(exc).__name__)
                last_error = exc
                if attempt + 1 < attempts:
                    continue
                break

    if isinstance(last_error, (TimeoutError, urlerror.URLError)):
        raise AiParseError("AI 识别超时，请稍后重试；如果持续失败，请检查 AI 服务状态") from last_error
    if isinstance(last_error, urlerror.HTTPError) and last_error.code == 429:
        raise AiParseError("AI 服务当前请求较多或额度暂不可用，请稍后重试") from last_error
    if isinstance(last_error, urlerror.HTTPError) and last_error.code >= 500:
        raise AiParseError("AI 服务暂时不可用，请稍后重试") from last_error
    if isinstance(last_error, AiParseError):
        raise last_error
    raise AiParseError("AI 返回结果缺少任务内容，请稍后重试") from last_error


async def parse_screenshot(
    image_bytes: bytes,
    content_type: str,
    reference_at: str | None,
    timezone_name: str,
) -> dict:
    reference = reference_at or datetime.now(timezone.utc).isoformat()
    ai_image_bytes, ai_content_type = await asyncio.to_thread(
        _prepare_image_for_ai,
        image_bytes,
        content_type,
    )
    logger.info(
        "Prepared screenshot for AI: %d -> %d bytes",
        len(image_bytes),
        len(ai_image_bytes),
    )
    return await asyncio.to_thread(
        _call_model,
        {
            "type": "image_url",
            "image_url": {
                "url": f"data:{ai_content_type};base64,{base64.b64encode(ai_image_bytes).decode('ascii')}",
            },
        },
        reference,
        timezone_name,
    )


async def parse_text(
    text: str,
    reference_at: str | None,
    timezone_name: str,
) -> dict:
    reference = reference_at or datetime.now(timezone.utc).isoformat()
    return await asyncio.to_thread(
        _call_model,
        text,
        reference,
        timezone_name,
    )
