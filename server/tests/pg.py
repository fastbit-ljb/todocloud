"""Helpers for tests that need a real PostgreSQL.

Set TEST_DATABASE_URL (e.g. postgresql+asyncpg://postgres:postgres@localhost:5432/todocloud_test).
Tests are skipped without it. The database name must end in ``_test``
because these helpers drop and recreate its ``public`` schema.
"""
import os
import unittest
from pathlib import Path
from urllib.parse import urlparse

import asyncpg

TEST_DATABASE_URL = os.environ.get("TEST_DATABASE_URL", "")
FIXTURES = Path(__file__).parent / "fixtures"

requires_postgres = unittest.skipUnless(
    TEST_DATABASE_URL, "set TEST_DATABASE_URL to run PostgreSQL-backed tests"
)


def _dsn() -> str:
    name = urlparse(TEST_DATABASE_URL).path.lstrip("/")
    if not name.endswith("_test"):
        raise RuntimeError(f"refusing to reset non-test database {name!r}")
    return TEST_DATABASE_URL.replace("postgresql+asyncpg://", "postgresql://", 1)


async def connect() -> asyncpg.Connection:
    return await asyncpg.connect(_dsn())


async def reset_schema() -> None:
    conn = await connect()
    try:
        await conn.execute("DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;")
    finally:
        await conn.close()


async def run_sql_file(name: str) -> None:
    conn = await connect()
    try:
        await conn.execute((FIXTURES / name).read_text(encoding="utf-8"))
        # pg_dump empties search_path for its session; this connection is closed right after.
    finally:
        await conn.close()


async def schema_snapshot() -> dict:
    """Columns, indexes and constraints of the public schema, minus alembic's table."""
    conn = await connect()
    try:
        columns = await conn.fetch(
            """
            SELECT table_name, column_name, data_type, character_maximum_length,
                   is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name <> 'alembic_version'
            ORDER BY table_name, column_name
            """
        )
        indexes = await conn.fetch(
            """
            SELECT tablename, indexname, indexdef FROM pg_indexes
            WHERE schemaname = 'public' AND tablename <> 'alembic_version'
            ORDER BY tablename, indexname
            """
        )
        constraints = await conn.fetch(
            """
            SELECT conrelid::regclass::text AS table_name, conname,
                   pg_get_constraintdef(oid) AS definition
            FROM pg_constraint
            WHERE connamespace = 'public'::regnamespace
              AND conrelid::regclass::text <> 'alembic_version'
            ORDER BY 1, 2
            """
        )
        return {
            "columns": [tuple(r) for r in columns],
            "indexes": [tuple(r) for r in indexes],
            "constraints": [tuple(r) for r in constraints],
        }
    finally:
        await conn.close()
