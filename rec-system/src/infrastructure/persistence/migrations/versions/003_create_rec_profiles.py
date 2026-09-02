"""Create rec_profiles table.

Revision ID: 003
Revises: 002
Create Date: 2026-03-31

Python-owned table storing the recommendation profile for each user.
Contains topic_vector (JSONB), embedding (VECTOR(312) nullable), and preference JSONB fields.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "003"
down_revision: Union[str, None] = "002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "rec_profiles",
        sa.Column("user_id", postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column("topic_vector", postgresql.JSONB(), nullable=False),
        sa.Column("sentiment_prefs", postgresql.JSONB(), nullable=False),
        sa.Column("format_prefs", postgresql.JSONB(), nullable=False),
        sa.Column("interaction_count", sa.Integer(), nullable=False, server_default=sa.text("0")),
        sa.Column("created_at", sa.TIMESTAMP(), nullable=True, server_default=sa.text("now()")),
        sa.Column("last_updated", sa.TIMESTAMP(), nullable=True),
        sa.PrimaryKeyConstraint("user_id"),
    )

    # Add VECTOR(312) column via raw SQL (pgvector type)
    op.execute("ALTER TABLE rec_profiles ADD COLUMN embedding vector(312)")


def downgrade() -> None:
    op.drop_table("rec_profiles")
