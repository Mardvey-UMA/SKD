"""Create rec_entity_interests table.

Revision ID: 004
Revises: 003
Create Date: 2026-03-31

Python-owned table tracking entity interests per user with decay.
Composite primary key: (user_id, entity_type, entity_name).
Indexes for top-N entity lookup and periodic cleanup.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "004"
down_revision: Union[str, None] = "003"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "rec_entity_interests",
        sa.Column("user_id", postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column("entity_type", sa.VARCHAR(), nullable=False),
        sa.Column("entity_name", sa.VARCHAR(), nullable=False),
        sa.Column("weight", sa.Float(), nullable=False, server_default=sa.text("1.0")),
        sa.Column("last_seen", sa.TIMESTAMP(), nullable=False, server_default=sa.text("now()")),
        sa.PrimaryKeyConstraint("user_id", "entity_type", "entity_name"),
    )

    # Top-N entities by weight for scoring
    op.create_index(
        "idx_entity_interests_user",
        "rec_entity_interests",
        [sa.text("user_id"), sa.text("weight DESC")],
    )

    # Cleanup old entities
    op.create_index(
        "idx_entity_interests_last_seen",
        "rec_entity_interests",
        ["last_seen"],
    )


def downgrade() -> None:
    op.drop_index("idx_entity_interests_last_seen", table_name="rec_entity_interests")
    op.drop_index("idx_entity_interests_user", table_name="rec_entity_interests")
    op.drop_table("rec_entity_interests")
