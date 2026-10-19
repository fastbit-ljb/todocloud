import asyncio
from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Body, Depends, File, Form, HTTPException, UploadFile, status
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai_parser import AiNotConfiguredError, AiParseError
from app.api.dependencies import get_current_user
from app.api.tasks import Recurrence, TaskResponse
from app.core.config import settings
from app.core.rate_limit import enforce_rate_limit
from app.conversation_context import resolve_references
from app.db.models import AiPlan, Attachment, Idea, Task, User
from app.db.session import get_db
from app.plan_ai import plan_turn
from app.storage import store_image


router = APIRouter(prefix="/plans")


class PlanStep(BaseModel):
    title: str = Field(min_length=1, max_length=500)


class PlanItem(BaseModel):
    source_type: str = Field(pattern="^(idea|task|prompt)$")
    source_id: int
    title: str = Field(min_length=1, max_length=200)
    description: str | None = None
    start_at: datetime | None = None
    due_at: datetime | None = None
    duration_minutes: int | None = Field(default=None, ge=1, le=525_600)
    reminder_offset_minutes: int | None = Field(default=None, ge=0, le=10_080)
    steps: list[PlanStep] = Field(default_factory=list, max_length=30)
    note: str = ""
    # Repeat rule for the task this item becomes; only kept when there is a due time.
    recurrence: Recurrence | None = None


def _item_recurrence(item: PlanItem, proposed: dict[tuple[str, int], PlanItem]) -> str | None:
    """The rule to store for an adopted item. Clients from before this field (1.3.6)
    send items without it, so fall back to what the plan proposed for that item."""
    rule = item.recurrence
    if "recurrence" not in item.model_fields_set:
        original = proposed.get((item.source_type, item.source_id))
        rule = original.recurrence if original else None
    return rule if item.due_at is not None else None


class PlanProposal(BaseModel):
    summary: str
    items: list[PlanItem] = Field(default_factory=list)


class PlanMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str


class PlanResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    status: str
    title: str
    proposal: PlanProposal
    messages: list[PlanMessage]
    accepted_at: datetime | None
    created_at: datetime
    updated_at: datetime


class PlanGenerateRequest(BaseModel):
    message: str = Field(default="请结合我的收件匣和现有任务，生成一份现实可执行的计划。", max_length=4000)
    idea_ids: list[int] | None = Field(default=None, max_length=100)
    reference_at: str | None = None
    timezone_name: str = Field(default="Asia/Shanghai", max_length=64)


class PlanReviseRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    reference_at: str | None = None
    timezone_name: str = Field(default="Asia/Shanghai", max_length=64)
    force_plan: bool = False
    reference_ids: list[UUID] = Field(default_factory=list, max_length=3)


class PlanChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    idea_ids: list[int] | None = Field(default=None, max_length=100)
    reference_at: str | None = None
    timezone_name: str = Field(default="Asia/Shanghai", max_length=64)
    # Earlier question/answer turns the client is still showing (not stored server-side).
    history: list[PlanMessage] = Field(default_factory=list, max_length=12)
    force_plan: bool = False
    reference_ids: list[UUID] = Field(default_factory=list, max_length=3)


class PlanChatResponse(BaseModel):
    kind: str  # "answer" or "plan"
    reply: str
    plan: "PlanResponse | None" = None


class PlanAcceptResponse(BaseModel):
    plan: PlanResponse
    tasks: list[TaskResponse]


class PlanAcceptRequest(BaseModel):
    # The items to adopt, possibly with edited times. Omitted = every proposed item (old clients).
    items: list[PlanItem] | None = Field(default=None, max_length=100)


class PlanUndoResponse(BaseModel):
    plan: PlanResponse
    deleted_task_ids: list[int]
    tasks: list[TaskResponse]  # existing tasks whose schedule was put back


# An accepted plan can be undone for this long; the app offers it right after accepting.
PLAN_UNDO_WINDOW = timedelta(hours=24)


def _iso(value: datetime | None) -> str | None:
    return value.isoformat() if value else None


def _parse_dt(value: str | None) -> datetime | None:
    return datetime.fromisoformat(value) if value else None


def serialize_idea(idea: Idea) -> dict:
    return {
        "id": idea.id,
        "content": idea.content,
        "duration_minutes": idea.duration_minutes,
        "start_at": idea.start_at.isoformat() if idea.start_at else None,
        "due_at": idea.due_at.isoformat() if idea.due_at else None,
        "items": idea.items or [],
    }


def serialize_task(task: Task) -> dict:
    return {
        "id": task.id,
        "title": task.title,
        "description": task.description,
        "start_at": task.start_at.isoformat() if task.start_at else None,
        "due_at": task.due_at.isoformat() if task.due_at else None,
        "duration_minutes": task.duration_minutes,
        "steps": task.steps or [],
        "recurrence": task.recurrence,
    }


async def planning_sources(
    user_id: int,
    db: AsyncSession,
    idea_ids: list[int] | None = None,
) -> tuple[list[Idea], list[Task]]:
    ideas_query = select(Idea).where(
        Idea.user_id == user_id,
        Idea.converted_task_id.is_(None),
    )
    if idea_ids is not None:
        ideas_query = ideas_query.where(Idea.id.in_(set(idea_ids)))
    ideas_result = await db.execute(ideas_query.order_by(Idea.updated_at.desc()))
    tasks_result = await db.execute(
        select(Task)
        .where(Task.user_id == user_id, Task.completed.is_(False))
        .order_by(Task.due_at.asc().nulls_last(), Task.created_at.desc())
    )
    return list(ideas_result.scalars().all()), list(tasks_result.scalars().all())


async def run_planner(
    ideas: list[Idea],
    tasks: list[Task],
    payload: PlanGenerateRequest | PlanReviseRequest | PlanChatRequest,
    previous_proposal: dict | None = None,
    messages: list[dict] | None = None,
    force_plan: bool = True,
    images: list[tuple[bytes, str]] | None = None,
    reference_context: str = "",
) -> dict:
    """Returns {"intent", "reply", "proposal"}; proposal is None for an answer."""
    try:
        return await plan_turn(
            [serialize_idea(item) for item in ideas],
            [serialize_task(item) for item in tasks],
            payload.reference_at,
            payload.timezone_name,
            payload.message.strip() + reference_context,
            previous_proposal,
            messages,
            force_plan=force_plan,
            images=images or None,
        )
    except AiNotConfiguredError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    except (AiParseError, ValueError, TypeError) as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)) from exc


@router.get("", response_model=list[PlanResponse])
async def list_plans(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[AiPlan]:
    result = await db.execute(
        select(AiPlan)
        .where(AiPlan.user_id == user.id)
        .order_by(AiPlan.updated_at.desc())
        .limit(10)
    )
    return list(result.scalars().all())


@router.get("/{plan_id}", response_model=PlanResponse)
async def get_plan(plan_id: int, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == plan_id, AiPlan.user_id == user.id))
    if plan is None:
        raise HTTPException(404, "计划不存在")
    return plan


@router.post("/generate", response_model=PlanResponse, status_code=status.HTTP_201_CREATED)
async def create_plan(
    payload: PlanGenerateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiPlan:
    await enforce_rate_limit("ai-plan-user", str(user.id), settings.ai_rate_limit_per_minute)
    ideas, tasks = await planning_sources(user.id, db, payload.idea_ids)
    if not ideas and not tasks and not payload.message.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="请先告诉 AI 你想安排什么",
        )
    proposal = (await run_planner(ideas, tasks, payload))["proposal"]
    messages = [
        {"role": "user", "content": payload.message.strip()},
        {"role": "assistant", "content": proposal["summary"]},
    ]
    plan = AiPlan(
        user_id=user.id,
        status="draft",
        title="我的 AI 计划",
        proposal=proposal,
        messages=messages,
    )
    db.add(plan)
    await db.commit()
    await db.refresh(plan)
    return plan


@router.post("/chat", response_model=PlanChatResponse)
async def chat(
    payload: PlanChatRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> PlanChatResponse:
    """Answer a question, or create a draft plan when the user actually wants one."""
    return await _chat(payload, user, db)


@router.post("/chat/images", response_model=PlanChatResponse)
async def chat_with_images(
    payload: str = Form(..., description="PlanChatRequest as JSON"),
    files: list[UploadFile] = File(...),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> PlanChatResponse:
    """Same as /chat, with up to three images read by the model for this turn."""
    return await _chat(_parse_form_payload(payload, PlanChatRequest), user, db, files)


MAX_PLAN_IMAGES = 3
MAX_PLAN_IMAGE_BYTES = 10 * 1024 * 1024
PLAN_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp"}


async def store_plan_images(
    files: list[UploadFile], user: User, db: AsyncSession
) -> list[tuple[bytes, str]]:
    """Validate and store uploaded images as attachments; returns (bytes, type) for the model."""
    if len(files) > MAX_PLAN_IMAGES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"一次最多上传 {MAX_PLAN_IMAGES} 张图片",
        )
    images: list[tuple[bytes, str, str]] = []
    for upload in files:
        if upload.content_type not in PLAN_IMAGE_TYPES:
            raise HTTPException(
                status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                detail="只支持 JPG、PNG 或 WebP 图片",
            )
        data = await upload.read()
        if not data:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="图片内容为空")
        if len(data) > MAX_PLAN_IMAGE_BYTES:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="每张图片不能超过 10 MB",
            )
        images.append((data, upload.content_type, upload.filename or "plan-image"))
    for data, content_type, filename in images:
        try:
            object_key = await asyncio.to_thread(store_image, user.id, filename, content_type, data)
        except Exception as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="图片存储服务暂不可用"
            ) from exc
        db.add(
            Attachment(
                user_id=user.id,
                object_key=object_key,
                original_name=filename,
                content_type=content_type,
                size_bytes=len(data),
            )
        )
    return [(data, content_type) for data, content_type, _ in images]


def _user_text(message: str, image_count: int) -> str:
    return f"{message}\n（附图 {image_count} 张）" if image_count else message


def _parse_form_payload(raw: str, model: type[BaseModel]):
    try:
        return model.model_validate_json(raw)
    except ValidationError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=exc.errors()
        ) from exc


async def _chat(
    payload: PlanChatRequest,
    user: User,
    db: AsyncSession,
    files: list[UploadFile] | None = None,
) -> PlanChatResponse:
    await enforce_rate_limit("ai-plan-user", str(user.id), settings.ai_rate_limit_per_minute)
    message = payload.message.strip()
    if not message:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="请先告诉 AI 你想问什么或安排什么")
    references = await resolve_references(db, user.id, payload.reference_ids)
    images = await store_plan_images(files or [], user, db)
    ideas, tasks = await planning_sources(user.id, db, payload.idea_ids)
    history = [item.model_dump() for item in payload.history]
    user_text = _user_text(message, len(images))
    turn = await run_planner(
        ideas,
        tasks,
        payload,
        messages=history + [{"role": "user", "content": user_text}],
        force_plan=payload.force_plan,
        images=images,
        reference_context=references,
    )
    if turn["proposal"] is None:
        await db.commit()  # keeps any stored image attachments
        return PlanChatResponse(kind="answer", reply=turn["reply"])

    plan = AiPlan(
        user_id=user.id,
        status="draft",
        title="我的 AI 计划",
        proposal=turn["proposal"],
        messages=[
            {"role": "user", "content": user_text},
            {"role": "assistant", "content": turn["proposal"]["summary"]},
        ],
    )
    db.add(plan)
    await db.commit()
    await db.refresh(plan)
    return PlanChatResponse(
        kind="plan", reply=turn["reply"], plan=PlanResponse.model_validate(plan)
    )


class ImportedMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(max_length=8000)


class PlanImportRequest(BaseModel):
    """A plan the app generated on the phone with the user's own model key."""

    proposal: PlanProposal
    messages: list[ImportedMessage] = Field(default_factory=list, max_length=60)


class PlanImportRevisionRequest(BaseModel):
    # None = the turn only answered a question; the plan stays as it is.
    proposal: PlanProposal | None = None
    messages: list[ImportedMessage] = Field(max_length=60)


def _validated_proposal(proposal: PlanProposal) -> dict:
    if len(proposal.items) > 100:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="计划条目过多")
    data = proposal.model_dump(mode="json")
    data["summary"] = data["summary"][:1000]
    return data


@router.post("/import", response_model=PlanResponse, status_code=status.HTTP_201_CREATED)
async def import_plan(
    payload: PlanImportRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiPlan:
    """Store a draft made on the phone. The server calls no model here; accept checks
    every task and idea against this user, exactly as for a server-made plan."""
    plan = AiPlan(
        user_id=user.id,
        status="draft",
        title="我的 AI 计划",
        proposal=_validated_proposal(payload.proposal),
        messages=[item.model_dump() for item in payload.messages],
    )
    db.add(plan)
    await db.commit()
    await db.refresh(plan)
    return plan


@router.post("/{plan_id}/import-revision", response_model=PlanResponse)
async def import_plan_revision(
    plan_id: int,
    payload: PlanImportRevisionRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiPlan:
    """Store a revision turn the phone ran with the user's own model key."""
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == plan_id, AiPlan.user_id == user.id))
    if plan is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="计划不存在")
    if plan.status != "draft":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="已采用的计划不能继续修改")
    if payload.proposal is not None:
        plan.proposal = _validated_proposal(payload.proposal)
    plan.messages = [item.model_dump() for item in payload.messages]
    await db.commit()
    await db.refresh(plan)
    return plan


@router.post("/{plan_id}/messages", response_model=PlanResponse)
async def revise_plan(
    plan_id: int,
    payload: PlanReviseRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiPlan:
    return await _revise(plan_id, payload, user, db)


@router.post("/{plan_id}/messages/images", response_model=PlanResponse)
async def revise_plan_with_images(
    plan_id: int,
    payload: str = Form(..., description="PlanReviseRequest as JSON"),
    files: list[UploadFile] = File(...),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiPlan:
    return await _revise(plan_id, _parse_form_payload(payload, PlanReviseRequest), user, db, files)


async def _revise(
    plan_id: int,
    payload: PlanReviseRequest,
    user: User,
    db: AsyncSession,
    files: list[UploadFile] | None = None,
) -> AiPlan:
    await enforce_rate_limit("ai-plan-user", str(user.id), settings.ai_rate_limit_per_minute)
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == plan_id, AiPlan.user_id == user.id))
    if plan is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="计划不存在")
    if plan.status != "draft":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="已采用的计划不能继续修改")
    references = await resolve_references(db, user.id, payload.reference_ids)
    images = await store_plan_images(files or [], user, db)
    ideas, tasks = await planning_sources(user.id, db)
    history = list(plan.messages or [])
    history.append({"role": "user", "content": _user_text(payload.message.strip(), len(images))})
    turn = await run_planner(
        ideas,
        tasks,
        payload,
        dict(plan.proposal or {}),
        history,
        force_plan=payload.force_plan,
        images=images,
        reference_context=references,
    )
    if turn["proposal"] is None:
        # A question about the plan: answer it and leave the plan untouched.
        history.append({"role": "assistant", "content": turn["reply"]})
    else:
        history.append({"role": "assistant", "content": turn["proposal"]["summary"]})
        plan.proposal = turn["proposal"]
    plan.messages = history
    await db.commit()
    await db.refresh(plan)
    return plan


@router.post("/{plan_id}/accept", response_model=PlanAcceptResponse)
async def accept_plan(
    plan_id: int,
    payload: PlanAcceptRequest | None = Body(default=None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> PlanAcceptResponse:
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == plan_id, AiPlan.user_id == user.id))
    if plan is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="计划不存在")
    if plan.status != "draft":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="这份计划已经采用")

    changed_tasks: list[Task] = []
    proposal = PlanProposal.model_validate(plan.proposal)
    selected = proposal.items
    if payload is not None and payload.items is not None:
        # Only items that are in this plan may be adopted; their times may be edited.
        proposed_keys = {(item.source_type, item.source_id) for item in proposal.items}
        if any((item.source_type, item.source_id) not in proposed_keys for item in payload.items):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="包含不属于这份计划的安排"
            )
        if not payload.items:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="请至少选择一项安排")
        selected = payload.items
    # What accepting changed, so /undo can put it back exactly.
    record: dict = {"created_task_ids": [], "converted_idea_ids": [], "task_snapshots": []}
    proposed_by_key = {(item.source_type, item.source_id): item for item in proposal.items}
    for item in selected:
        rule = _item_recurrence(item, proposed_by_key)
        if item.source_type == "task":
            task = await db.scalar(
                select(Task).where(Task.id == item.source_id, Task.user_id == user.id)
            )
            if task is None or task.completed:
                continue
            record["task_snapshots"].append(
                {
                    "id": task.id,
                    "start_at": _iso(task.start_at),
                    "due_at": _iso(task.due_at),
                    "duration_minutes": task.duration_minutes,
                    "reminder_offset_minutes": task.reminder_offset_minutes,
                    "steps": list(task.steps or []),
                    "recurrence": task.recurrence,
                }
            )
            task.start_at = item.start_at
            task.due_at = item.due_at
            task.duration_minutes = item.duration_minutes
            if item.reminder_offset_minutes is not None:
                task.reminder_offset_minutes = item.reminder_offset_minutes
            if item.steps:
                task.steps = [{"title": step.title, "completed": False} for step in item.steps]
            if rule is not None:
                task.recurrence = rule
            elif task.due_at is None:
                task.recurrence = None  # a rule needs a due time
            changed_tasks.append(task)
            continue

        if item.source_type == "prompt":
            task = Task(
                user_id=user.id,
                title=item.title,
                description=item.description,
                start_at=item.start_at,
                due_at=item.due_at,
                duration_minutes=item.duration_minutes,
                reminder_offset_minutes=item.reminder_offset_minutes,
                steps=[{"title": step.title, "completed": False} for step in item.steps],
                recurrence=rule,
            )
            db.add(task)
            await db.flush()
            record["created_task_ids"].append(task.id)
            changed_tasks.append(task)
            continue

        idea = await db.scalar(
            select(Idea).where(
                Idea.id == item.source_id,
                Idea.user_id == user.id,
                Idea.converted_task_id.is_(None),
            )
        )
        if idea is None:
            continue
        proposed_steps = [{"title": step.title, "completed": False} for step in item.steps]
        idea_steps = [
            {"title": str(value.get("content") or "").strip(), "completed": False}
            for value in (idea.items or [])
            if isinstance(value, dict) and str(value.get("content") or "").strip()
        ]
        task = Task(
            user_id=user.id,
            title=item.title,
            description=item.description or (idea.content if item.title != idea.content else None),
            start_at=item.start_at,
            due_at=item.due_at,
            duration_minutes=item.duration_minutes or idea.duration_minutes,
            reminder_offset_minutes=item.reminder_offset_minutes,
            steps=proposed_steps or idea_steps,
            recurrence=rule,
        )
        db.add(task)
        await db.flush()
        idea.converted_task_id = task.id
        record["created_task_ids"].append(task.id)
        record["converted_idea_ids"].append(idea.id)
        changed_tasks.append(task)

    # The plan keeps what was actually adopted; the full proposal is kept for /undo.
    record["original_items"] = [item.model_dump(mode="json") for item in proposal.items]
    plan.proposal = {
        "summary": proposal.summary,
        "items": [item.model_dump(mode="json") for item in selected],
        "accept_record": record,
    }
    plan.status = "accepted"
    plan.accepted_at = datetime.now(timezone.utc)
    await db.commit()
    for task in changed_tasks:
        await db.refresh(task)
    await db.refresh(plan)
    return PlanAcceptResponse(plan=PlanResponse.model_validate(plan), tasks=changed_tasks)


@router.post("/{plan_id}/undo", response_model=PlanUndoResponse)
async def undo_accept(
    plan_id: int,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> PlanUndoResponse:
    """Reverse an accept: delete the tasks it created, give ideas back to the inbox,
    restore the schedules it changed, and make the plan a draft again."""
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == plan_id, AiPlan.user_id == user.id))
    if plan is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="计划不存在")
    record = (plan.proposal or {}).get("accept_record")
    if plan.status != "accepted" or not isinstance(record, dict):
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="这份计划不能撤回")
    if plan.accepted_at and datetime.now(timezone.utc) - plan.accepted_at > PLAN_UNDO_WINDOW:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="采用超过 24 小时的计划不能撤回")

    idea_ids = [int(value) for value in record.get("converted_idea_ids", [])]
    if idea_ids:
        ideas = await db.execute(select(Idea).where(Idea.user_id == user.id, Idea.id.in_(idea_ids)))
        for idea in ideas.scalars():
            idea.converted_task_id = None

    deleted_ids: list[int] = []
    created_ids = [int(value) for value in record.get("created_task_ids", [])]
    if created_ids:
        created = await db.execute(select(Task).where(Task.user_id == user.id, Task.id.in_(created_ids)))
        for task in created.scalars():
            deleted_ids.append(task.id)
            await db.delete(task)

    restored: list[Task] = []
    for snapshot in record.get("task_snapshots", []):
        task = await db.scalar(
            select(Task).where(Task.id == int(snapshot["id"]), Task.user_id == user.id)
        )
        if task is None:
            continue
        task.start_at = _parse_dt(snapshot.get("start_at"))
        task.due_at = _parse_dt(snapshot.get("due_at"))
        task.duration_minutes = snapshot.get("duration_minutes")
        task.reminder_offset_minutes = snapshot.get("reminder_offset_minutes")
        task.steps = list(snapshot.get("steps") or [])
        if "recurrence" in snapshot:  # records from before recurrence did not change it
            task.recurrence = snapshot.get("recurrence")
        restored.append(task)

    plan.proposal = {
        "summary": (plan.proposal or {}).get("summary", ""),
        "items": record.get("original_items") or (plan.proposal or {}).get("items", []),
    }
    plan.status = "draft"
    plan.accepted_at = None
    await db.commit()
    for task in restored:
        await db.refresh(task)
    await db.refresh(plan)
    return PlanUndoResponse(
        plan=PlanResponse.model_validate(plan), deleted_task_ids=deleted_ids, tasks=restored
    )
