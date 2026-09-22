import asyncio
import json
from datetime import datetime

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.ai_parser import AiNotConfiguredError, AiParseError, parse_screenshot
from app.api.dependencies import get_current_user
from app.core.config import settings
from app.db.models import AiParseRecord, Attachment, User
from app.db.session import get_db
from app.storage import store_image


router = APIRouter(prefix="/ai")
MAX_IMAGE_BYTES = 10 * 1024 * 1024


class AiTaskCandidate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=10_000)
    due_at: datetime | None = None
    reminder_offset_minutes: int | None = Field(default=None, ge=0, le=10_080)
    confidence: float = Field(default=0, ge=0, le=1)
    source_text: str | None = None


class AiParseResponse(BaseModel):
    attachment_id: int
    parse_id: int
    candidates: list[AiTaskCandidate]


@router.post("/parse-screenshot", response_model=AiParseResponse)
async def parse_screenshot_endpoint(
    file: UploadFile = File(...),
    reference_at: str | None = Form(default=None),
    timezone_name: str = Form(default="Asia/Shanghai"),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AiParseResponse:
    if file.content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, detail="只支持 JPG、PNG 或 WebP 图片")
    if not settings.ai_api_key:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="服务端尚未配置 AI_API_KEY")
    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="图片内容为空")
    if len(image_bytes) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail="图片不能超过 10 MB")

    try:
        object_key = await asyncio.to_thread(
            store_image,
            user.id,
            file.filename or "screenshot.png",
            file.content_type,
            image_bytes,
        )
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="图片存储服务暂不可用") from exc

    attachment = Attachment(
        user_id=user.id,
        object_key=object_key,
        original_name=file.filename or "screenshot.png",
        content_type=file.content_type,
        size_bytes=len(image_bytes),
    )
    db.add(attachment)
    await db.flush()

    try:
        raw = await parse_screenshot(image_bytes, file.content_type, reference_at, timezone_name)
        candidates = [AiTaskCandidate.model_validate(item) for item in raw.get("tasks", [])]
    except AiNotConfiguredError as exc:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    except (AiParseError, ValueError, TypeError) as exc:
        record = AiParseRecord(
            user_id=user.id,
            attachment_id=attachment.id,
            status="failed",
            error_message=str(exc),
        )
        db.add(record)
        await db.commit()
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)) from exc

    record = AiParseRecord(
        user_id=user.id,
        attachment_id=attachment.id,
        status="completed",
        candidates_json=json.dumps([item.model_dump(mode="json") for item in candidates]),
    )
    db.add(record)
    await db.commit()
    await db.refresh(record)
    return AiParseResponse(
        attachment_id=attachment.id,
        parse_id=record.id,
        candidates=candidates,
    )
