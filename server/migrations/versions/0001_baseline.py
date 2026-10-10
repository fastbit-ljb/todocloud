"""Baseline: the schema production was running before Alembic (2026-10).

Written to be idempotent so it is safe on every kind of database we have:
- a fresh database: creates everything;
- production and any database built by the old startup code
  (``create_all`` + ``ALTER TABLE ... IF NOT EXISTS``): changes nothing and
  only records this revision;
- a database older than some of those ALTERs: adds the missing columns, as
  the old startup code did.

The DDL mirrors ``pg_dump --schema-only`` of production, including
``tasks.steps`` being JSONB. Every later schema change is a normal migration.

Revision ID: 0001_baseline
Revises:
Create Date: 2026-10-10
"""
from alembic import op

revision = "0001_baseline"
down_revision = None
branch_labels = None
depends_on = None

_STATEMENTS = [
    """
    CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        email VARCHAR(320) NOT NULL,
        password_hash VARCHAR(512) NOT NULL,
        display_name VARCHAR(80),
        timezone VARCHAR(64) NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_email ON users (email)",
    """
    CREATE TABLE IF NOT EXISTS refresh_tokens (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        token_hash VARCHAR(64) NOT NULL,
        expires_at TIMESTAMPTZ NOT NULL,
        revoked_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE UNIQUE INDEX IF NOT EXISTS ix_refresh_tokens_token_hash ON refresh_tokens (token_hash)",
    "CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_id ON refresh_tokens (user_id)",
    """
    CREATE TABLE IF NOT EXISTS tasks (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        title VARCHAR(200) NOT NULL,
        description TEXT,
        due_at TIMESTAMPTZ,
        completed BOOLEAN NOT NULL DEFAULT false,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    # Columns the old startup code added one release at a time.
    "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS reminder_offset_minutes INTEGER",
    "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ",
    "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS steps JSONB NOT NULL DEFAULT '[]'::jsonb",
    "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS start_at TIMESTAMPTZ",
    "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS duration_minutes INTEGER",
    # A database built from scratch by the old create_all got steps as JSON;
    # production got JSONB from the ALTER above. Converge on JSONB.
    """
    DO $$
    BEGIN
        IF (SELECT data_type FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'tasks'
              AND column_name = 'steps') = 'json' THEN
            ALTER TABLE tasks ALTER COLUMN steps DROP DEFAULT;
            ALTER TABLE tasks ALTER COLUMN steps TYPE JSONB USING steps::jsonb;
            ALTER TABLE tasks ALTER COLUMN steps SET DEFAULT '[]'::jsonb;
        END IF;
    END
    $$
    """,
    "UPDATE tasks SET completed_at = updated_at WHERE completed = TRUE AND completed_at IS NULL",
    "CREATE INDEX IF NOT EXISTS ix_tasks_user_id ON tasks (user_id)",
    """
    CREATE TABLE IF NOT EXISTS ideas (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        content TEXT NOT NULL,
        duration_minutes INTEGER,
        start_at TIMESTAMPTZ,
        due_at TIMESTAMPTZ,
        items JSON NOT NULL DEFAULT '[]'::json,
        converted_task_id INTEGER REFERENCES tasks (id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE INDEX IF NOT EXISTS ix_ideas_user_id ON ideas (user_id)",
    "CREATE INDEX IF NOT EXISTS ix_ideas_converted_task_id ON ideas (converted_task_id)",
    """
    CREATE TABLE IF NOT EXISTS ai_plans (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        status VARCHAR(24) NOT NULL DEFAULT 'draft',
        title VARCHAR(120) NOT NULL,
        proposal JSON NOT NULL DEFAULT '{}'::json,
        messages JSON NOT NULL DEFAULT '[]'::json,
        accepted_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE INDEX IF NOT EXISTS ix_ai_plans_user_id ON ai_plans (user_id)",
    """
    CREATE TABLE IF NOT EXISTS attachments (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        object_key VARCHAR(512) NOT NULL UNIQUE,
        original_name VARCHAR(255) NOT NULL,
        content_type VARCHAR(120) NOT NULL,
        size_bytes INTEGER NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE INDEX IF NOT EXISTS ix_attachments_user_id ON attachments (user_id)",
    """
    CREATE TABLE IF NOT EXISTS ai_parse_records (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        attachment_id INTEGER NOT NULL REFERENCES attachments (id) ON DELETE CASCADE,
        status VARCHAR(32) NOT NULL,
        candidates_json TEXT,
        error_message TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
    """,
    "CREATE INDEX IF NOT EXISTS ix_ai_parse_records_user_id ON ai_parse_records (user_id)",
    "CREATE INDEX IF NOT EXISTS ix_ai_parse_records_attachment_id ON ai_parse_records (attachment_id)",
]


def upgrade() -> None:
    for statement in _STATEMENTS:
        op.execute(statement)


def downgrade() -> None:
    raise NotImplementedError("The baseline cannot be downgraded; restore from a backup instead.")
