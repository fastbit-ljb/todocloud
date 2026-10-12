"""Recurring tasks: tasks.recurrence holds the repeat rule.

NULL means a one-off task (every existing row). Allowed values are enforced by
the API: daily, weekdays, weekly, monthly.

Revision ID: 0002_task_recurrence
Revises: 0001_baseline
Create Date: 2026-10-12
"""
from alembic import op

revision = "0002_task_recurrence"
down_revision = "0001_baseline"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence VARCHAR(16)")


def downgrade() -> None:
    op.execute("ALTER TABLE tasks DROP COLUMN IF EXISTS recurrence")
