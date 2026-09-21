from datetime import datetime, timezone

from fastapi import APIRouter


router = APIRouter(prefix="/health")


@router.get("")
async def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "todocloud-server",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

