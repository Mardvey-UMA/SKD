"""Add data_flow.recommendation_history table for never-re-recommend guarantee.

Revision ID: 009
Revises: 008
Create Date: 2026-04-03

Tracks which published_content IDs have been returned to each user.
POST /recommendations writes returned IDs here; feed generation excludes them.
Cleanup job deletes entries older than 30 days.
"""

from typing import Sequence, Union

from alembic import op

revision: str = "009"
down_revision: Union[str, None] = "008"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE data_flow.recommendation_history (
            user_id        UUID NOT NULL,
            content_id     UUID NOT NULL,
            recommended_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (user_id, content_id),
            FOREIGN KEY (user_id) REFERENCES data_flow.rec_profiles(user_id) ON DELETE CASCADE,
            FOREIGN KEY (content_id) REFERENCES data_flow.published_content(id) ON DELETE CASCADE
        )
    """)

    op.execute("""
        CREATE INDEX ix_rec_history_user_recommended_at
            ON data_flow.recommendation_history (user_id, recommended_at)
    """)


def downgrade() -> None:
    op.execute(
        "DROP INDEX IF EXISTS data_flow.ix_rec_history_user_recommended_at"
    )
    op.execute("DROP TABLE IF EXISTS data_flow.recommendation_history CASCADE")
