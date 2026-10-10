from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

from app.app_version import VersionUnavailableError, latest_version_info


router = APIRouter(prefix="/app")


class AppVersionResponse(BaseModel):
    latest_version: str
    min_supported_version: str | None = None
    release_notes: str = ""
    release_url: str = ""
    download_url: str | None = None
    published_at: str | None = None


@router.get("/version", response_model=AppVersionResponse)
async def app_version() -> dict:
    """Latest released Android version. Public: the same data is on the GitHub Releases page."""
    try:
        return await latest_version_info()
    except VersionUnavailableError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
