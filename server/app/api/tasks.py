from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import case, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.models import Task, User
from app.db.session import get_db
from app.recurrence import next_occurrence, user_zone


router = APIRouter(prefix="/tasks")

Recurrence = Literal["daily", "weekdays", "weekly", "monthly"]


class TaskStep(BaseModel):
    title: str = Field(min_length=1, max_length=500)
    completed: bool = False


class TaskCreateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=10_000)
    start_at: datetime | None = None
    due_at: datetime | None = None
    duration_minutes: int | None = Field(default=None, ge=1, le=525_600)
    reminder_offset_minutes: int | None = Field(default=None, ge=0, le=10_080)
    steps: list[TaskStep] = Field(default_factory=list, max_length=30)
    recurrence: Recurrence | None = None


class TaskUpdateRequest(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=10_000)
    start_at: datetime | None = None
    due_at: datetime | None = None
    duration_minutes: int | None = Field(default=None, ge=1, le=525_600)
    reminder_offset_minutes: int | None = Field(default=None, ge=0, le=10_080)
    completed: bool | None = None
    steps: list[TaskStep] | None = Field(default=None, max_length=30)
    recurrence: Recurrence | None = None


class TaskResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str | None
    start_at: datetime | None
    due_at: datetime | None
    duration_minutes: int | None
    reminder_offset_minutes: int | None
    completed: bool
    completed_at: datetime | None
    created_at: datetime
    updated_at: datetime
    steps: list[TaskStep] = Field(default_factory=list)
    recurrence: str | None = None
    completed_recurrence: str | None = None


def _check_recurrence(task: Task) -> None:
    if task.recurrence and task.due_at is None:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="重复任务需要设置截止时间",
        )


def _next_occurrence_of(task: Task, zone_name: str | None, now: datetime) -> Task:
    """The copy that replaces a just-completed recurring task, steps reset."""
    zone = user_zone(zone_name)
    new_due = next_occurrence(task.due_at, task.recurrence, zone, now)
    shift = new_due - task.due_at
    return Task(
        user_id=task.user_id,
        title=task.title,
        description=task.description,
        start_at=task.start_at + shift if task.start_at else None,
        due_at=new_due,
        duration_minutes=task.duration_minutes,
        reminder_offset_minutes=task.reminder_offset_minutes,
        steps=[{**step, "completed": False} for step in (task.steps or [])],
        recurrence=task.recurrence,
    )


@router.get("", response_model=list[TaskResponse])
async def list_tasks(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
) -> list[Task]:
    result = await db.execute(
        select(Task)
        .where(Task.user_id == user.id)
        .order_by(
            case((Task.completed.is_(False), 0), else_=1),
            case((Task.completed.is_(False), Task.due_at), else_=None).asc().nulls_last(),
            case((Task.completed.is_(True), Task.completed_at), else_=None).desc().nulls_last(),
            Task.created_at.desc(),
        )
    )
    return list(result.scalars().all())


@router.post("", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
async def create_task(
    payload: TaskCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> Task:
    task = Task(
        user_id=user.id,
        title=payload.title.strip(),
        description=payload.description,
        start_at=payload.start_at,
        due_at=payload.due_at,
        duration_minutes=payload.duration_minutes,
        reminder_offset_minutes=payload.reminder_offset_minutes,
        steps=[step.model_dump() for step in payload.steps],
        recurrence=payload.recurrence,
    )
    _check_recurrence(task)
    if task.start_at and task.due_at and task.start_at > task.due_at:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="开始时间不能晚于截止时间",
        )
    db.add(task)
    await db.commit()
    await db.refresh(task)
    return task


@router.patch("/{task_id}", response_model=TaskResponse)
async def update_task(
    task_id: int,
    payload: TaskUpdateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> Task:
    task = await db.scalar(select(Task).where(Task.id == task_id, Task.user_id == user.id))
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务不存在")
    just_completed = False
    for field, value in payload.model_dump(exclude_unset=True).items():
        if field == "title" and value is not None:
            value = value.strip()
        if field == "completed":
            value = bool(value)
            if value and not task.completed:
                task.completed_at = datetime.now(timezone.utc)
                just_completed = True
            elif not value:
                task.completed_at = None
        setattr(task, field, value)
    if task.start_at and task.due_at and task.start_at > task.due_at:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="开始时间不能晚于截止时间",
        )
    _check_recurrence(task)
    if just_completed and task.recurrence:
        # The rule moves to the next occurrence, so un-checking and re-checking
        # this one later does not spawn a second copy.
        db.add(_next_occurrence_of(task, user.timezone, task.completed_at))
        task.completed_recurrence = task.recurrence
        task.recurrence = None
    await db.commit()
    await db.refresh(task)
    return task


@router.delete("/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_task(
    task_id: int,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    task = await db.scalar(select(Task).where(Task.id == task_id, Task.user_id == user.id))
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务不存在")
    await db.delete(task)
    await db.commit()
