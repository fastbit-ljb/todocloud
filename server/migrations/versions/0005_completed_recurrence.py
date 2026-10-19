"""Remember the period of completed repeats without generating duplicate copies."""
from alembic import op
import sqlalchemy as sa

revision = "0005_completed_recurrence"
down_revision = "0004_conversation_deletion"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Old completions have already lost their rule; do not guess from task titles.
    op.add_column("tasks", sa.Column("completed_recurrence", sa.String(16), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "completed_recurrence")
