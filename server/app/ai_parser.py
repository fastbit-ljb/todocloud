import asyncio
import base64
import json
import re
from datetime import datetime, timezone
from urllib import request

from app.core.config import settings


class AiNotConfiguredError(RuntimeError):
    pass


class AiParseError(RuntimeError):
    pass


def _extract_json(content: str) -> dict:
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", content, re.DOTALL)
    candidate = fenced.group(1) if fenced else content.strip()
    try:
        return json.loads(candidate)
    except json.JSONDecodeError as exc:
        raise AiParseError("AI 返回的任务结果不是有效 JSON") from exc


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


def _normalize_tasks(result: dict) -> dict:
    for task in result.get("tasks", []):
        if isinstance(task, dict):
            _normalize_unspecified_morning(task)
    return result


def _build_prompt(reference_at: str, timezone_name: str, source_type: str) -> str:
    source = "聊天截图" if source_type == "image" else "聊天文字"
    return (
        f"请从这份{source}中提取需要完成的待办事项。只返回 JSON，不要解释。"
        "格式必须是 {\"tasks\":[{\"title\":\"...\",\"description\":null,"
        "\"due_at\":\"ISO-8601 或 null\",\"reminder_offset_minutes\":null,"
        "\"confidence\":0.0,\"source_text\":\"原文片段\"}]}。"
        f"当前参考时间是 {reference_at}，用户时区是 {timezone_name}。"
        "聊天中出现‘明天、下周一、今晚’等相对时间时，必须以参考时间和用户时区换算成 ISO-8601；"
        "如果只写‘6点’、‘8点’等时间而没有明确上午、下午、晚上、中午、凌晨或 24 小时制，默认按上午处理；"
        "例如‘明天6点把word给我’必须是次日 06:00，不得擅自解释成 18:00；只有‘下午6点’或‘晚上6点’才是 18:00。"
        "没有明确时间就填 null。标题要简短可执行，不要把聊天寒暄当成任务。"
        "同一事项即使在聊天中出现多次或只是不同说法，也只返回一条任务，优先保留信息更完整的一条。"
    )


def _call_model(content: object, reference_at: str, timezone_name: str) -> dict:
    if not settings.ai_api_key:
        raise AiNotConfiguredError("服务端尚未配置 AI_API_KEY")

    payload = json.dumps(
        {
            "model": settings.ai_model,
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
    ).encode("utf-8")
    endpoint = settings.ai_api_base_url.rstrip("/") + "/chat/completions"
    http_request = request.Request(
        endpoint,
        data=payload,
        headers={
            "Authorization": f"Bearer {settings.ai_api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with request.urlopen(http_request, timeout=90) as response:
            result = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise AiParseError("AI 服务请求失败，请稍后重试") from exc

    try:
        content = result["choices"][0]["message"]["content"]
        if isinstance(content, list):
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))
        return _normalize_tasks(_extract_json(content))
    except (KeyError, IndexError, TypeError) as exc:
        raise AiParseError("AI 返回结果缺少任务内容") from exc


async def parse_screenshot(
    image_bytes: bytes,
    content_type: str,
    reference_at: str | None,
    timezone_name: str,
) -> dict:
    reference = reference_at or datetime.now(timezone.utc).isoformat()
    return await asyncio.to_thread(
        _call_model,
        {
            "type": "image_url",
            "image_url": {
                "url": f"data:{content_type};base64,{base64.b64encode(image_bytes).decode('ascii')}",
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
