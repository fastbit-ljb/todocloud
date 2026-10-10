"""Alembic environment.

Two entry points:
- The API server runs migrations on startup through app.db.migrate, which
  hands over an already-open connection in ``config.attributes["connection"]``.
- ``alembic upgrade head`` / ``alembic revision`` from the CLI, which opens its
  own async connection from DATABASE_URL.
"""
import asyncio
from logging.config import fileConfig

from alembic import context
from sqlalchemy.ext.asyncio import create_async_engine

from app.core.config import settings
from app.db import models  # noqa: F401  (registers tables on Base.metadata)
from app.db.base import Base

config = context.config
if config.config_file_name is not None and not config.attributes.get("skip_logging"):
    fileConfig(config.config_file_name, disable_existing_loggers=False)

target_metadata = Base.metadata


def _run(connection) -> None:
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()


async def _run_async() -> None:
    engine = create_async_engine(settings.database_url)
    async with engine.connect() as connection:
        await connection.run_sync(_run)
    await engine.dispose()


if context.is_offline_mode():
    context.configure(url=settings.database_url, target_metadata=target_metadata, literal_binds=True)
    with context.begin_transaction():
        context.run_migrations()
elif (connection := config.attributes.get("connection")) is not None:
    _run(connection)
else:
    asyncio.run(_run_async())
