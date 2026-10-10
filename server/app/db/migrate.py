"""Run Alembic migrations from inside the running application."""
from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import text
from sqlalchemy.engine import Connection
from sqlalchemy.ext.asyncio import AsyncEngine

SERVER_ROOT = Path(__file__).resolve().parents[2]
# Arbitrary constant: serialises concurrent starters (several API replicas) so
# only one runs the migrations; the others wait and then find nothing to do.
_MIGRATION_LOCK_ID = 7_046_193_221


def alembic_config() -> Config:
    config = Config(str(SERVER_ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(SERVER_ROOT / "migrations"))
    # Keep the app's own logging setup; alembic.ini would reconfigure the root logger.
    config.attributes["skip_logging"] = True
    return config


def _upgrade(connection: Connection) -> None:
    connection.execute(text("SELECT pg_advisory_xact_lock(:id)"), {"id": _MIGRATION_LOCK_ID})
    config = alembic_config()
    config.attributes["connection"] = connection
    command.upgrade(config, "head")


async def upgrade_to_head(engine: AsyncEngine) -> None:
    async with engine.begin() as connection:
        await connection.run_sync(_upgrade)
