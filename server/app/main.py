from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.core.config import settings
from app.db.migrate import upgrade_to_head
from app.db.session import engine
from app.core.rate_limit import close_rate_limit


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Schema changes live in server/migrations (Alembic); see docs/development.md.
    await upgrade_to_head(engine)
    yield
    await close_rate_limit()
    await engine.dispose()


app = FastAPI(
    title=settings.app_name,
    version="1.0.0",
    description="TodoCloud cloud API",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api/v1")
