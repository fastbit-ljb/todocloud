"""Keep content-free deletion markers for cross-device history sync."""
from alembic import op
import sqlalchemy as sa

revision = "0004_conversation_deletion"
down_revision = "0003_ai_conversations"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("ai_conversations", sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    # Tombstones contain no title/messages and must not reappear in the old API.
    op.execute("DELETE FROM ai_conversations WHERE deleted_at IS NOT NULL")
    op.drop_column("ai_conversations", "deleted_at")
