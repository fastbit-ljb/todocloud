import unittest

from alembic.autogenerate import compare_metadata
from alembic.migration import MigrationContext
from sqlalchemy.ext.asyncio import create_async_engine

from app.db.base import Base
from app.db.migrate import upgrade_to, upgrade_to_head
from tests import pg


@pg.requires_postgres
class MigrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        await pg.reset_schema()
        self.engine = create_async_engine(pg.TEST_DATABASE_URL)

    async def asyncTearDown(self) -> None:
        await self.engine.dispose()

    async def _version(self) -> str:
        conn = await pg.connect()
        try:
            return await conn.fetchval("SELECT version_num FROM alembic_version")
        finally:
            await conn.close()

    async def test_fresh_database_matches_production_schema(self) -> None:
        # The baseline revision is exactly what production ran before Alembic.
        await upgrade_to(self.engine, "0001_baseline")
        self.assertEqual(await self._version(), "0001_baseline")
        fresh = await pg.schema_snapshot()

        await pg.reset_schema()
        await pg.run_sql_file("prod_schema_2026_10.sql")
        production = await pg.schema_snapshot()

        self.assertEqual(fresh, production)

    async def test_production_database_upgrades_without_changes(self) -> None:
        await pg.run_sql_file("prod_schema_2026_10.sql")
        conn = await pg.connect()
        try:
            user_id = await conn.fetchval(
                "INSERT INTO public.users (email, password_hash, timezone) "
                "VALUES ('a@example.com', 'x', 'Asia/Shanghai') RETURNING id"
            )
            await conn.execute(
                "INSERT INTO public.tasks (user_id, title, steps, completed) "
                "VALUES ($1, 'keep me', '[{\"title\": \"s\", \"completed\": false}]', false)",
                user_id,
            )
        finally:
            await conn.close()
        before = await pg.schema_snapshot()

        await upgrade_to_head(self.engine)
        await upgrade_to_head(self.engine)  # a second start is a no-op

        # Existing tables only gain recurrence; conversations are an additive table.
        after = await pg.schema_snapshot()
        added = sorted(set(after["columns"]) - set(before["columns"]))
        self.assertEqual(
            [row for row in added if row[0] != "ai_conversations"], [("tasks", "recurrence", "character varying", 16, "YES", None)]
        )
        self.assertEqual(sorted(set(before["columns"]) - set(after["columns"])), [])
        self.assertTrue(any(row[0] == "ai_conversations" for row in added))
        self.assertEqual([row for row in after["indexes"] if row[0] != "ai_conversations"], before["indexes"])
        self.assertEqual([row for row in after["constraints"] if row[0] != "ai_conversations"], before["constraints"])
        self.assertEqual(await self._version(), "0003_ai_conversations")
        conn = await pg.connect()
        try:
            row = await conn.fetchrow("SELECT title, steps, recurrence FROM public.tasks")
        finally:
            await conn.close()
        self.assertEqual(row["title"], "keep me")
        self.assertIn('"s"', row["steps"])
        self.assertIsNone(row["recurrence"])

    async def test_old_database_gets_missing_columns_and_jsonb_steps(self) -> None:
        # A database from before the startup ALTERs, created with steps as JSON
        # the way the old create_all would have made a fresh tasks table.
        conn = await pg.connect()
        try:
            await conn.execute(
                """
                CREATE TABLE users (
                    id SERIAL PRIMARY KEY, email VARCHAR(320) NOT NULL,
                    password_hash VARCHAR(512) NOT NULL, display_name VARCHAR(80),
                    timezone VARCHAR(64) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now());
                CREATE TABLE tasks (
                    id SERIAL PRIMARY KEY,
                    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                    title VARCHAR(200) NOT NULL, description TEXT, due_at TIMESTAMPTZ,
                    completed BOOLEAN NOT NULL DEFAULT false,
                    steps JSON NOT NULL DEFAULT '[]'::json,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
                INSERT INTO users (email, password_hash, timezone) VALUES ('b@example.com', 'x', 'UTC');
                INSERT INTO tasks (user_id, title, completed) VALUES (1, 'done long ago', true);
                """
            )
        finally:
            await conn.close()

        await upgrade_to(self.engine, "0001_baseline")
        upgraded = await pg.schema_snapshot()

        await pg.reset_schema()
        await pg.run_sql_file("prod_schema_2026_10.sql")
        production = await pg.schema_snapshot()

        # Column ORDER differs (ALTERs append), but types/defaults must match production.
        self.assertEqual(sorted(upgraded["columns"]), sorted(production["columns"]))

    async def test_models_match_migrated_schema(self) -> None:
        await upgrade_to_head(self.engine)

        def diff(sync_conn):
            context = MigrationContext.configure(sync_conn, opts={"compare_type": True})
            return compare_metadata(context, Base.metadata)

        async with self.engine.connect() as conn:
            differences = await conn.run_sync(diff)
        self.assertEqual(differences, [], "models drifted from migrations; add a migration")


if __name__ == "__main__":
    unittest.main()
