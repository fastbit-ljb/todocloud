import asyncio
import json
import logging
import time
from datetime import datetime
from urllib import error as urlerror
from urllib import request

from app.ai_parser import AiNotConfiguredError, AiParseError, _extract_json, _localize_reference
from app.core.config import settings


logger = logging.getLogger(__name__)
PLAN_TIMEOUT_SECONDS = 90
PLAN_PRIMARY_ATTEMPTS = 2


def _build_plan_prompt(
    ideas: list[dict],
    tasks: list[dict],
    reference_at: datetime,
    timezone_name: str,
    user_message: str,
    previous_proposal: dict | None,
    conversation: list[dict],
) -> str:
    previous_text = json.dumps(previous_proposal, ensure_ascii=False) if previous_proposal else "无"
    conversation_text = json.dumps(conversation[-12:], ensure_ascii=False)
    return f"""
你是有生活常识、会留白的个人计划助手。请把用户收件匣中的想法和现有未完成任务整理成一份现实可执行的计划。

当前用户当地时间：{reference_at.isoformat(timespec='seconds')}
IANA 时区：{timezone_name}

必须遵守的生活边界：
1. 普通工作不得安排在早餐 07:30-08:30、午餐 12:00-13:00、晚餐 18:00-19:00。
2. 普通工作不得安排在睡眠时段 23:00-次日 07:00。
3. 只有吃饭本身、旅行/交通、赶火车或飞机、就医、明确且真正紧急的固定事项，才可以占用上述保护时段。
4. 尊重每条内容已有的开始时间、截止时间和持续时间；不要制造冲突，不要把任务排到截止时间之后。
5. 没有持续时间时可以结合内容合理估计；如果无法安全判断，可以保留 start_at 和 due_at 为 null，并在 note 中说明需要用户补充，不要假装精确。
6. 安排休息与切换余量，不要把一天塞满。已明确固定时间的任务优先，紧急任务优先。
7. 想法不是承诺。只选择适合现在执行的想法；暂不安排的想法不要强行加入计划。

收件匣想法：
{json.dumps(ideas, ensure_ascii=False)}

现有未完成任务：
{json.dumps(tasks, ensure_ascii=False)}

上一版计划：
{previous_text}

最近计划对话：
{conversation_text}

用户这次的要求：
{user_message}

只返回 JSON，不要附加解释，格式必须是：
{{
  "summary": "一两句话说明这版计划的思路",
  "items": [
    {{
      "source_type": "idea、task 或 prompt",
      "source_id": 1,
      "title": "可执行标题",
      "description": null,
      "start_at": "带 UTC 偏移的 ISO-8601 或 null",
      "due_at": "带 UTC 偏移的 ISO-8601 或 null",
      "duration_minutes": 60,
      "reminder_offset_minutes": 10,
      "steps": [{{"title": "步骤"}}],
      "note": "为什么这样安排或为什么暂未排期"
    }}
  ]
}}
现有任务使用 task 且 source_id 必须来自输入；收件匣想法使用 idea 且 source_id 必须来自输入。
用户在“这次的要求”里直接描述、但不属于输入来源的新事项使用 prompt，source_id 从 1 开始依次编号。
不要把普通安排要求本身误当作新事项，也不要重复同一 source_type + source_id。
所有具体时间必须使用 {timezone_name} 当前对应的 UTC 偏移。标题简短，步骤具体，summary 和 note 使用中文。
""".strip()


def _normalize_iso(value: object, reference_at: datetime) -> str | None:
    if value in (None, "", "null"):
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError as exc:
        raise AiParseError("AI 计划返回了无效时间") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=reference_at.tzinfo)
    return parsed.astimezone(reference_at.tzinfo).isoformat()


def _normalize_proposal(
    raw: dict,
    ideas: list[dict],
    tasks: list[dict],
    reference_at: datetime,
    allow_prompt_items: bool = False,
) -> dict:
    valid_ids = {
        "idea": {int(item["id"]) for item in ideas},
        "task": {int(item["id"]) for item in tasks},
        "prompt": set(range(1, 31)) if allow_prompt_items else set(),
    }
    summary = str(raw.get("summary") or "我整理了一版可执行计划，你可以继续告诉我怎么调整。").strip()
    normalized: list[dict] = []
    seen: set[tuple[str, int]] = set()
    for item in raw.get("items") or []:
        if not isinstance(item, dict):
            continue
        source_type = str(item.get("source_type") or "").strip().lower()
        try:
            source_id = int(item.get("source_id"))
        except (TypeError, ValueError):
            continue
        key = (source_type, source_id)
        if source_type not in valid_ids or source_id not in valid_ids[source_type] or key in seen:
            continue
        title = str(item.get("title") or "").strip()
        if not title:
            continue
        seen.add(key)
        duration = item.get("duration_minutes")
        try:
            duration = int(duration) if duration is not None else None
        except (TypeError, ValueError):
            duration = None
        if duration is not None:
            duration = max(1, min(duration, 525_600))
        reminder = item.get("reminder_offset_minutes")
        try:
            reminder = int(reminder) if reminder is not None else None
        except (TypeError, ValueError):
            reminder = None
        if reminder is not None:
            reminder = max(0, min(reminder, 10_080))
        steps = []
        for step in item.get("steps") or []:
            text = str(step.get("title") if isinstance(step, dict) else step).strip()
            if text:
                steps.append({"title": text[:500]})
        normalized.append(
            {
                "source_type": source_type,
                "source_id": source_id,
                "title": title[:200],
                "description": str(item.get("description") or "").strip() or None,
                "start_at": _normalize_iso(item.get("start_at"), reference_at),
                "due_at": _normalize_iso(item.get("due_at"), reference_at),
                "duration_minutes": duration,
                "reminder_offset_minutes": reminder,
                "steps": steps[:30],
                "note": str(item.get("note") or "").strip()[:1000],
            }
        )
    normalized.sort(key=lambda item: item["start_at"] or item["due_at"] or "9999")
    return {"summary": summary[:1000], "items": normalized}


def _call_plan_model(prompt: str) -> dict:
    if not settings.ai_api_key:
        raise AiNotConfiguredError("服务端尚未配置 AI_API_KEY")
    endpoint = settings.ai_api_base_url.rstrip("/") + "/chat/completions"
    models = [settings.ai_model]
    fallback = settings.ai_fallback_model.strip()
    if fallback and fallback not in models:
        models.append(fallback)

    last_error: Exception | None = None
    for model_index, model in enumerate(models):
        payload = {
            "model": model,
            "max_tokens": 8192,
            "messages": [
                {"role": "system", "content": "你是尊重生活节律、善于留白的个人计划助手。"},
                {"role": "user", "content": prompt},
            ],
        }
        if "generativelanguage.googleapis.com" in settings.ai_api_base_url:
            payload["reasoning_effort"] = "minimal"
        attempts = PLAN_PRIMARY_ATTEMPTS if model_index == 0 else 1
        for attempt in range(attempts):
            http_request = request.Request(
                endpoint,
                data=json.dumps(payload).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {settings.ai_api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            started_at = time.monotonic()
            try:
                with request.urlopen(http_request, timeout=PLAN_TIMEOUT_SECONDS) as response:
                    result = json.loads(response.read().decode("utf-8"))
                content = result["choices"][0]["message"]["content"]
                if isinstance(content, list):
                    content = "".join(
                        part.get("text", "") for part in content if isinstance(part, dict)
                    )
                logger.info(
                    "AI planning returned with %s in %.2fs",
                    model,
                    time.monotonic() - started_at,
                )
                return _extract_json(content)
            except urlerror.HTTPError as exc:
                last_error = exc
                logger.warning(
                    "AI planning provider returned HTTP %s for %s after %.2fs",
                    exc.code,
                    model,
                    time.monotonic() - started_at,
                )
                if exc.code in {401, 403}:
                    raise AiParseError("AI 服务鉴权失败，请检查服务端配置") from exc
                if exc.code in {400, 404}:
                    break
                if exc.code not in {408, 429, 500, 502, 503, 504}:
                    break
                if attempt + 1 < attempts:
                    time.sleep(1)
                    continue
                break
            except (TimeoutError, urlerror.URLError) as exc:
                last_error = exc
                if attempt + 1 < attempts:
                    time.sleep(1)
                    continue
                break
            except (AiParseError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
                last_error = exc
                if attempt + 1 < attempts:
                    continue
                break

    if isinstance(last_error, (TimeoutError, urlerror.URLError)):
        raise AiParseError("AI 生成计划超时，请稍后重试") from last_error
    if isinstance(last_error, urlerror.HTTPError) and last_error.code == 429:
        raise AiParseError("AI 计划请求较多，请稍后再试") from last_error
    if isinstance(last_error, urlerror.HTTPError) and last_error.code in {400, 404}:
        raise AiParseError("AI 计划模型配置无效，请检查服务端配置") from last_error
    if isinstance(last_error, AiParseError):
        raise last_error
    raise AiParseError("AI 计划服务暂时不可用，请稍后重试") from last_error


async def generate_plan(
    ideas: list[dict],
    tasks: list[dict],
    reference_at: str | None,
    timezone_name: str,
    user_message: str,
    previous_proposal: dict | None = None,
    conversation: list[dict] | None = None,
) -> dict:
    reference = _localize_reference(reference_at, timezone_name)
    prompt = _build_plan_prompt(
        ideas,
        tasks,
        reference,
        timezone_name,
        user_message,
        previous_proposal,
        conversation or [],
    )
    raw = await asyncio.to_thread(_call_plan_model, prompt)
    return _normalize_proposal(
        raw,
        ideas,
        tasks,
        reference,
        allow_prompt_items=bool(user_message.strip()),
    )
