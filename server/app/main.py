from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.api.router import api_router
from app.core.config import settings
from app.db.base import Base
from app.db.session import engine
from app.core.rate_limit import close_rate_limit


@asynccontextmanager
async def lifespan(_: FastAPI):
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.execute(
            text(
                "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS "
                "reminder_offset_minutes INTEGER"
            )
        )
        await connection.execute(
            text(
                "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS "
                "completed_at TIMESTAMPTZ"
            )
        )
        await connection.execute(
            text(
                "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS "
                "steps JSONB NOT NULL DEFAULT '[]'::jsonb"
            )
        )
        await connection.execute(
            text(
                "UPDATE tasks SET completed_at = updated_at "
                "WHERE completed = TRUE AND completed_at IS NULL"
            )
        )
    yield
    await close_rate_limit()
    await engine.dispose()


app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
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
