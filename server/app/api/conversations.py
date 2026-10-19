"""Account-scoped conversation history, independent of AI provider choice."""
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.api.plans import PlanResponse
from app.db.models import AiConversation, AiPlan, User
from app.db.session import get_db

router = APIRouter(prefix="/conversations")


async def lock_conversation(db: AsyncSession, conversation_id: UUID) -> None:
    # Row locks cannot lock a missing UUID. A transaction-level advisory lock
    # serializes first PUT/DELETE and makes deletion idempotent under races.
    await db.execute(select(func.pg_advisory_xact_lock(func.hashtextextended(str(conversation_id), 0))))


class ConversationReference(BaseModel):
    id: UUID
    title: str = Field(max_length=120)


class ConversationMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=32_000)
    references: list[ConversationReference] = Field(default_factory=list, max_length=3)


class ConversationWrite(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    messages: list[ConversationMessage] = Field(max_length=1000)
    plan_id: int | None = Field(default=None, gt=0)
    revision: int = Field(default=0, ge=0)

    @model_validator(mode="after")
    def bounded_history(self):
        if sum(len(item.content) for item in self.messages) > 1_000_000:
            raise ValueError("会话过长，请新建会话继续")
        return self


class ConversationSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    title: str
    updated_at: datetime
    revision: int
    preview: str
    message_count: int
    deleted_at: datetime | None = None


class ConversationResponse(ConversationSummary):
    messages: list[ConversationMessage]
    plan: PlanResponse | None = None


def summary(row: AiConversation) -> dict:
    messages = row.messages or []
    return {
        "id": row.id, "title": row.title, "updated_at": row.updated_at,
        "revision": row.revision, "message_count": len(messages),
        "preview": str(messages[-1].get("content", ""))[:120] if messages else "",
        "deleted_at": row.deleted_at,
    }


async def response(row: AiConversation, db: AsyncSession) -> ConversationResponse:
    plan = await db.scalar(select(AiPlan).where(AiPlan.id == row.plan_id, AiPlan.user_id == row.user_id)) if row.plan_id else None
    return ConversationResponse(**summary(row), messages=row.messages or [], plan=plan)


@router.get("", response_model=list[ConversationSummary])
async def list_conversations(include_deleted: bool = False, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    query = select(AiConversation).where(AiConversation.user_id == user.id)
    if not include_deleted:
        query = query.where(AiConversation.deleted_at.is_(None))
    rows = await db.scalars(query.order_by(AiConversation.updated_at.desc(), AiConversation.id))
    return [summary(row) for row in rows]


@router.get("/{conversation_id}", response_model=ConversationResponse)
async def get_conversation(conversation_id: UUID, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    row = await db.scalar(select(AiConversation).where(AiConversation.id == str(conversation_id), AiConversation.user_id == user.id))
    if row is None:
        raise HTTPException(404, "会话不存在")
    if row.deleted_at is not None:
        raise HTTPException(410, "会话已删除，请新建会话")
    return await response(row, db)


@router.put("/{conversation_id}", response_model=ConversationResponse)
async def save_conversation(conversation_id: UUID, payload: ConversationWrite, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    await lock_conversation(db, conversation_id)
    row = await db.scalar(select(AiConversation).where(AiConversation.id == str(conversation_id)).with_for_update())
    if row is not None and row.user_id != user.id:
        raise HTTPException(404, "会话不存在")
    if row is not None and row.deleted_at is not None:
        raise HTTPException(410, "会话已删除，请新建会话")
    if row is not None and row.revision != payload.revision:
        raise HTTPException(409, "会话已在其他设备更新，请重新打开后继续")
    if row is None and payload.revision != 0:
        raise HTTPException(409, "会话版本已变化，请重新打开后继续")
    if payload.plan_id is not None:
        owned = await db.scalar(select(AiPlan.id).where(AiPlan.id == payload.plan_id, AiPlan.user_id == user.id))
        if owned is None:
            raise HTTPException(404, "计划不存在")
    if row is None:
        row = AiConversation(id=str(conversation_id), user_id=user.id, revision=0)
        db.add(row)
    row.title = payload.title.strip() or "新会话"
    row.messages = [message.model_dump(mode="json") for message in payload.messages]
    row.plan_id = payload.plan_id
    row.revision += 1
    try:
        await db.commit()
    except IntegrityError as exc:
        await db.rollback()
        raise HTTPException(409, "会话已在其他设备更新，请重新打开后继续") from exc
    await db.refresh(row)
    return await response(row, db)


@router.delete("/{conversation_id}", status_code=204)
async def delete_conversation(conversation_id: UUID, plan_id: int | None = None, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    await lock_conversation(db, conversation_id)
    row = await db.scalar(select(AiConversation).where(AiConversation.id == str(conversation_id)).with_for_update())
    if row is not None and row.user_id != user.id:
        # An idempotent response does not reveal whether another account owns
        # an otherwise unguessable UUID.
        return Response(status_code=204)
    attached_plan_id = row.plan_id if row is not None else plan_id
    if row is None:
        # This also deletes an unsynced local draft without permitting a late PUT
        # from another device to recreate its contents.
        row = AiConversation(id=str(conversation_id), user_id=user.id, revision=0)
        db.add(row)
    if row.deleted_at is None:
        row.title = ""
        row.messages = []
        row.plan_id = None  # Never delete the plan or its adopted tasks.
        row.revision += 1
        row.deleted_at = datetime.now(timezone.utc)
        if attached_plan_id is not None:
            plan = await db.scalar(
                select(AiPlan).where(AiPlan.id == attached_plan_id, AiPlan.user_id == user.id).with_for_update()
            )
            if plan is not None:
                another = await db.scalar(
                    select(AiConversation.id).where(
                        AiConversation.user_id == user.id,
                        AiConversation.plan_id == attached_plan_id,
                        AiConversation.id != str(conversation_id),
                        AiConversation.deleted_at.is_(None),
                    ).limit(1)
                )
                if another is None:
                    # Keep proposal/status/tasks but remove duplicated chat text.
                    plan.messages = []
        try:
            await db.commit()
        except IntegrityError as exc:
            await db.rollback()
            raise HTTPException(409, "会话正在同步，请重试删除") from exc
    return Response(status_code=204)
