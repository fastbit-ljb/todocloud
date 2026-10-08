from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai_parser import AiNotConfiguredError, AiParseError
from app.api.dependencies import get_current_user
from app.api.tasks import TaskResponse
from app.core.config import settings
from app.core.rate_limit import enforce_rate_limit
from app.db.models import AiPlan, Idea, Task, User
from app.db.session import get_db
from app.plan_ai import generate_plan


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


class PlanAcceptResponse(BaseModel):
    plan: PlanResponse
    tasks: list[TaskResponse]


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
    payload: PlanGenerateRequest | PlanReviseRequest,
    previous_proposal: dict | None = None,
    messages: list[dict] | None = None,
) -> dict:
    try:
        return await generate_plan(
            [serialize_idea(item) for item in ideas],
            [serialize_task(item) for item in tasks],
            payload.reference_at,
            payload.timezone_name,
            payload.message.strip(),
            previous_proposal,
            messages,
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
    proposal = await run_planner(ideas, tasks, payload)
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


@router.post("/{plan_id}/messages", response_model=PlanResponse)
async def revise_plan(
    plan_id: int,
    payload: PlanReviseRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiPlan:
    await enforce_rate_limit("ai-plan-user", str(user.id), settings.ai_rate_limit_per_minute)
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == plan_id, AiPlan.user_id == user.id))
    if plan is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="计划不存在")
    if plan.status != "draft":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="已采用的计划不能继续修改")
    ideas, tasks = await planning_sources(user.id, db)
    history = list(plan.messages or [])
    history.append({"role": "user", "content": payload.message.strip()})
    proposal = await run_planner(ideas, tasks, payload, dict(plan.proposal or {}), history)
    history.append({"role": "assistant", "content": proposal["summary"]})
    plan.proposal = proposal
    plan.messages = history
    await db.commit()
    await db.refresh(plan)
    return plan


@router.post("/{plan_id}/accept", response_model=PlanAcceptResponse)
async def accept_plan(
    plan_id: int,
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
    for item in proposal.items:
        if item.source_type == "task":
            task = await db.scalar(
                select(Task).where(Task.id == item.source_id, Task.user_id == user.id)
            )
            if task is None or task.completed:
                continue
            task.start_at = item.start_at
            task.due_at = item.due_at
            task.duration_minutes = item.duration_minutes
            if item.reminder_offset_minutes is not None:
                task.reminder_offset_minutes = item.reminder_offset_minutes
            if item.steps:
                task.steps = [{"title": step.title, "completed": False} for step in item.steps]
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
            )
            db.add(task)
            await db.flush()
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
        )
        db.add(task)
        await db.flush()
        idea.converted_task_id = task.id
        changed_tasks.append(task)

    plan.status = "accepted"
    plan.accepted_at = datetime.now(timezone.utc)
    await db.commit()
    for task in changed_tasks:
        await db.refresh(task)
    await db.refresh(plan)
    return PlanAcceptResponse(plan=PlanResponse.model_validate(plan), tasks=changed_tasks)
