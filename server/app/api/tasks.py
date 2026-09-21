from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.models import Task, User
from app.db.session import get_db


router = APIRouter(prefix="/tasks")


class TaskCreateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=10_000)
    due_at: datetime | None = None


class TaskUpdateRequest(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=10_000)
    due_at: datetime | None = None
    completed: bool | None = None


class TaskResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str | None
    due_at: datetime | None
    completed: bool
    created_at: datetime
    updated_at: datetime


@router.get("", response_model=list[TaskResponse])
async def list_tasks(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
) -> list[Task]:
    result = await db.execute(
        select(Task)
        .where(Task.user_id == user.id)
        .order_by(Task.completed.asc(), Task.due_at.asc().nulls_last(), Task.created_at.desc())
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
        due_at=payload.due_at,
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
    for field, value in payload.model_dump(exclude_unset=True).items():
        if field == "title" and value is not None:
            value = value.strip()
        setattr(task, field, value)
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
