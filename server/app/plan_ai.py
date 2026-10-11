import asyncio
import base64
import json
import logging
import time
from datetime import datetime
from urllib import error as urlerror
from urllib import request

from app.ai_parser import (
    AiNotConfiguredError,
    AiParseError,
    _extract_json,
    _localize_reference,
    _prepare_image_for_ai,
)
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
    force_plan: bool = True,
    image_count: int = 0,
) -> str:
    previous_text = json.dumps(previous_proposal, ensure_ascii=False) if previous_proposal else "无"
    conversation_text = json.dumps(conversation[-12:], ensure_ascii=False)
    if force_plan:
        intent_rules = '用户已明确要求生成计划：intent 必须是 "plan"。'
    else:
        intent_rules = """先判断用户这次的意图，再决定输出：
1. intent = "plan"：用户明确要你安排、规划、排期、生成或调整计划（例如“帮我排一下明天”“把阅读挪到晚上”“按这些生成计划”）。此时按下面的规则输出 items。
2. intent = "answer"：用户在提问、咨询、征求意见、闲聊，或只是想了解信息（例如“我这周任务多吗”“考研英语怎么复习”“这个计划是不是太满了”）。此时不要输出 items，在 reply 中直接、具体地回答，可以结合收件匣和任务给建议；如果适合，最后用一句话提示用户可以让你把它排成计划。
3. 拿不准时选 "answer"，并在 reply 中问清楚用户是否需要你直接安排。
4. 上一版计划存在时，intent = "answer" 表示保持原计划不变，只回答问题。"""
    image_rules = ""
    if image_count:
        image_rules = f"""
用户这次附带了 {image_count} 张图片（可能是课程表、待办清单、聊天记录、通知或日程截图）。
请仔细读取图片内容并结合文字理解用户意图：回答问题时引用图片中的信息；生成计划时，把图片中识别出的、尚不在收件匣和任务里的具体事项作为 prompt 新事项安排，图片中的固定时间（上课、会议、考试、车次）要严格遵守。
看不清或无法判断的内容不要编造，在 reply 或 note 中说明。
"""
    return f"""
你是有生活常识、会留白的个人计划助手，帮助有一堆待办但懒得规划的用户。你既能回答关于时间安排的问题，也能在用户需要时把收件匣中的想法和现有未完成任务整理成一份现实可执行的计划。

{intent_rules}
{image_rules}
当前用户当地时间：{reference_at.isoformat(timespec='seconds')}
IANA 时区：{timezone_name}
图片或文字里只写了月日、没写年份的日期（例如“10月13日”“13号”），按当前时间之后最近的那一天理解，年份通常就是今年。
“2027届”“2023级”“2026年级”这类是届别、年级或群名，不是日期，不能拿来当年份。日期旁边写了星期（如“下周二（10月13日）”）时，选出的年份必须让日期和星期对得上。

必须遵守的生活边界：
1. 普通工作不得安排在早餐 07:30-08:30、午餐 12:00-13:00、晚餐 18:00-19:00。
2. 普通工作不得安排在睡眠时段 23:00-次日 07:00。
3. 只有吃饭本身、旅行/交通、赶火车或飞机、就医、明确且真正紧急的固定事项，才可以占用上述保护时段。
4. 尊重每条内容已有的开始时间、截止时间和持续时间；不要制造冲突，不要把任务排到截止时间之后。
5. 没有持续时间时可以结合内容合理估计；如果无法安全判断，可以保留 start_at 和 due_at 为 null，并在 note 中说明需要用户补充，不要假装精确。
6. 安排休息与切换余量，不要把一天塞满。已明确固定时间的任务优先，紧急任务优先。
7. 想法不是承诺。只选择适合现在执行的想法；暂不安排的想法不要强行加入计划。

目标展开规则：
1. “帮我制定……计划”“给我安排……”是在要求你直接产出并排好可执行内容，不是让你创建一个名为“制定计划”或“安排计划”的任务。
2. 每个 item 都必须是用户可以在该时间段直接执行、并实际推进目标的行动。除非用户明确要求只做前期规划，否则禁止输出“制定规划、列出科目、细化计划、整理计划、确定学习时长”等元任务。
3. 例如用户要求“一天的考研学习计划”，应直接安排英语、数学或专业课等具体复习/练习时段，而不是安排“制定考研复习全局规划”。
4. 用户没有给出全部细节时，可以基于常见场景做保守且明确的默认假设，在 summary 或 note 中说明，并允许用户继续调整；不要用准备计划代替真正执行。
5. 标题使用“动作 + 具体对象”，步骤写本时段真正要完成的内容，避免空泛表述。

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
  "intent": "plan 或 answer",
  "reply": "intent 为 answer 时的中文回答；intent 为 plan 时可为空",
  "summary": "intent 为 plan 时，一两句话说明这版计划的思路",
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


ANSWER_FALLBACK = "我没太确定你是想咨询还是想让我直接安排。如果需要排成计划，告诉我“帮我安排”就好。"


def _normalize_turn(
    raw: dict,
    ideas: list[dict],
    tasks: list[dict],
    reference_at: datetime,
    force_plan: bool,
    allow_prompt_items: bool,
) -> dict:
    """Decide between answering and planning from the model output.

    Without force_plan, anything other than an explicit "plan" intent with at
    least one usable item is treated as an answer, so a question never turns
    into a plan by accident.
    """
    intent = str(raw.get("intent") or "").strip().lower()
    if force_plan or intent == "plan":
        proposal = _normalize_proposal(raw, ideas, tasks, reference_at, allow_prompt_items)
        if force_plan or proposal["items"]:
            return {"intent": "plan", "reply": proposal["summary"], "proposal": proposal}
    reply = str(raw.get("reply") or raw.get("summary") or "").strip() or ANSWER_FALLBACK
    return {"intent": "answer", "reply": reply[:4000], "proposal": None}


def _call_plan_model(prompt: str, image_parts: list[dict] | None = None) -> dict:
    if not settings.ai_api_key:
        raise AiNotConfiguredError("服务端尚未配置 AI_API_KEY")
    endpoint = settings.ai_api_base_url.rstrip("/") + "/chat/completions"
    models = [settings.ai_model]
    fallback = settings.ai_fallback_model.strip()
    if fallback and fallback not in models:
        models.append(fallback)
    user_content: object = (
        [{"type": "text", "text": prompt}, *image_parts] if image_parts else prompt
    )

    last_error: Exception | None = None
    for model_index, model in enumerate(models):
        payload = {
            "model": model,
            "max_tokens": 8192,
            "messages": [
                {"role": "system", "content": "你是尊重生活节律、善于留白的个人计划助手。"},
                {"role": "user", "content": user_content},
            ],
        }
        if "generativelanguage.googleapis.com" in settings.ai_api_base_url and settings.ai_reasoning_effort:
            payload["reasoning_effort"] = settings.ai_reasoning_effort
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

    # HTTPError is a subclass of URLError, so the HTTP cases must come first;
    # otherwise a quota (429) or bad model name (400) is reported as a timeout.
    if isinstance(last_error, urlerror.HTTPError) and last_error.code == 429:
        raise AiParseError("AI 计划请求较多，请稍后再试") from last_error
    if isinstance(last_error, urlerror.HTTPError) and last_error.code in {400, 404}:
        raise AiParseError("AI 计划模型配置无效，请检查服务端配置") from last_error
    if isinstance(last_error, (TimeoutError, urlerror.URLError)) and not isinstance(
        last_error, urlerror.HTTPError
    ):
        raise AiParseError("AI 生成计划超时，请稍后重试") from last_error
    if isinstance(last_error, AiParseError):
        raise last_error
    raise AiParseError("AI 计划服务暂时不可用，请稍后重试") from last_error


async def plan_turn(
    ideas: list[dict],
    tasks: list[dict],
    reference_at: str | None,
    timezone_name: str,
    user_message: str,
    previous_proposal: dict | None = None,
    conversation: list[dict] | None = None,
    force_plan: bool = False,
    images: list[tuple[bytes, str]] | None = None,
) -> dict:
    """One planning-conversation turn: {"intent", "reply", "proposal" (None for answers)}.

    ``images`` are (bytes, content_type) pairs sent with this turn only.
    """
    reference = _localize_reference(reference_at, timezone_name)
    prompt = _build_plan_prompt(
        ideas,
        tasks,
        reference,
        timezone_name,
        user_message,
        previous_proposal,
        conversation or [],
        force_plan=force_plan,
        image_count=len(images or []),
    )
    if images:
        parts = []
        for image_bytes, content_type in images:
            data, data_type = await asyncio.to_thread(_prepare_image_for_ai, image_bytes, content_type)
            encoded = base64.b64encode(data).decode("ascii")
            parts.append({"type": "image_url", "image_url": {"url": f"data:{data_type};base64,{encoded}"}})
        raw = await asyncio.to_thread(_call_plan_model, prompt, parts)
    else:
        raw = await asyncio.to_thread(_call_plan_model, prompt)
    return _normalize_turn(
        raw,
        ideas,
        tasks,
        reference,
        force_plan=force_plan,
        allow_prompt_items=bool(user_message.strip()),
    )


async def generate_plan(
    ideas: list[dict],
    tasks: list[dict],
    reference_at: str | None,
    timezone_name: str,
    user_message: str,
    previous_proposal: dict | None = None,
    conversation: list[dict] | None = None,
) -> dict:
    """Always produce a plan (the pre-1.3.2 behaviour of /plans/generate)."""
    turn = await plan_turn(
        ideas,
        tasks,
        reference_at,
        timezone_name,
        user_message,
        previous_proposal,
        conversation,
        force_plan=True,
    )
    return turn["proposal"]
