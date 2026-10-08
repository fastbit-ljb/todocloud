from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.models import Idea, User
from app.db.session import get_db


router = APIRouter(prefix="/ideas")


class IdeaItem(BaseModel):
    content: str = Field(min_length=1, max_length=500)


class IdeaCreateRequest(BaseModel):
    content: str = Field(min_length=1, max_length=10_000)
    duration_minutes: int | None = Field(default=None, ge=1, le=525_600)
    start_at: datetime | None = None
    due_at: datetime | None = None
    items: list[IdeaItem] = Field(default_factory=list, max_length=30)


class IdeaUpdateRequest(BaseModel):
    content: str | None = Field(default=None, min_length=1, max_length=10_000)
    duration_minutes: int | None = Field(default=None, ge=1, le=525_600)
    start_at: datetime | None = None
    due_at: datetime | None = None
    items: list[IdeaItem] | None = Field(default=None, max_length=30)


class IdeaResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    content: str
    duration_minutes: int | None
    start_at: datetime | None
    due_at: datetime | None
    items: list[IdeaItem] = Field(default_factory=list)
    converted_task_id: int | None
    created_at: datetime
    updated_at: datetime


def validate_window(start_at: datetime | None, due_at: datetime | None) -> None:
    if start_at and due_at and start_at > due_at:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="开始时间不能晚于截止时间",
        )


@router.get("", response_model=list[IdeaResponse])
async def list_ideas(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[Idea]:
    result = await db.execute(
        select(Idea)
        .where(Idea.user_id == user.id, Idea.converted_task_id.is_(None))
        .order_by(Idea.updated_at.desc(), Idea.created_at.desc())
    )
    return list(result.scalars().all())


@router.post("", response_model=IdeaResponse, status_code=status.HTTP_201_CREATED)
async def create_idea(
    payload: IdeaCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> Idea:
    validate_window(payload.start_at, payload.due_at)
    idea = Idea(
        user_id=user.id,
        content=payload.content.strip(),
        duration_minutes=payload.duration_minutes,
        start_at=payload.start_at,
        due_at=payload.due_at,
        items=[item.model_dump() for item in payload.items],
    )
    db.add(idea)
    await db.commit()
    await db.refresh(idea)
    return idea


@router.patch("/{idea_id}", response_model=IdeaResponse)
async def update_idea(
    idea_id: int,
    payload: IdeaUpdateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> Idea:
    idea = await db.scalar(select(Idea).where(Idea.id == idea_id, Idea.user_id == user.id))
    if idea is None or idea.converted_task_id is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="想法不存在")
    for field, value in payload.model_dump(exclude_unset=True).items():
        if field == "content" and value is not None:
            value = value.strip()
        setattr(idea, field, value)
    validate_window(idea.start_at, idea.due_at)
    await db.commit()
    await db.refresh(idea)
    return idea


@router.delete("/{idea_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_idea(
    idea_id: int,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    idea = await db.scalar(select(Idea).where(Idea.id == idea_id, Idea.user_id == user.id))
    if idea is None or idea.converted_task_id is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="想法不存在")
    await db.delete(idea)
    await db.commit()
