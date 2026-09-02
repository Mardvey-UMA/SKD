"""Drop user_disliked_posts table — redundant with recommendation_history.

Revision ID: 011
Revises: 010
Create Date: 2026-04-03

Disliked content exclusion is handled via recommendation_history.
The table is no longer read by the recommendation engine.
"""

from typing import Sequence, Union

from alembic import op

revision: str = "011"
down_revision: Union[str, None] = "010"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("DROP TABLE IF EXISTS data_flow.user_disliked_posts")


def downgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.user_disliked_posts (
            user_id UUID NOT NULL,
            post_id UUID NOT NULL,
            PRIMARY KEY (user_id, post_id)
        )
    """)
