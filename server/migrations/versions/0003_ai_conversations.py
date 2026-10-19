"""Persist AI conversations separately from generated task plans."""
from alembic import op
import sqlalchemy as sa

revision = "0003_ai_conversations"
down_revision = "0002_task_recurrence"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "ai_conversations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("title", sa.String(120), nullable=False),
        sa.Column("messages", sa.JSON(), server_default=sa.text("'[]'"), nullable=False),
        sa.Column("plan_id", sa.Integer(), sa.ForeignKey("ai_plans.id", ondelete="SET NULL"), nullable=True),
        sa.Column("revision", sa.Integer(), server_default="1", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_ai_conversations_user_id", "ai_conversations", ["user_id"])
    # Keep every existing plan, not just the ten returned by the old list endpoint.
    op.execute("""
        INSERT INTO ai_conversations (id, user_id, title, messages, plan_id, created_at, updated_at)
        SELECT CAST(CAST(md5('todocloud-ai-plan-' || id::text) AS uuid) AS text), user_id,
               LEFT(COALESCE(NULLIF(messages->0->>'content', ''), title), 120),
               messages, id, created_at, updated_at
        FROM ai_plans
    """)


def downgrade() -> None:
    op.drop_table("ai_conversations")
