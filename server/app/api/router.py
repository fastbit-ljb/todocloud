from fastapi import APIRouter

from app.api.auth import router as auth_router
from app.api.ai import router as ai_router
from app.api.health import router as health_router
from app.api.ideas import router as ideas_router
from app.api.plans import router as plans_router
from app.api.tasks import router as tasks_router


api_router = APIRouter()
api_router.include_router(health_router, tags=["health"])
api_router.include_router(auth_router, tags=["auth"])
api_router.include_router(ai_router, tags=["ai"])
api_router.include_router(tasks_router, tags=["tasks"])
api_router.include_router(ideas_router, tags=["ideas"])
api_router.include_router(plans_router, tags=["plans"])
