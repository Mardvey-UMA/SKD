"""Create posts_features table.

Revision ID: 002
Revises: 001
Create Date: 2026-03-31

Python-owned table storing extracted ML features for each post.
Requires pgvector extension (001) and posts_raw FK target from 005.
FK to posts_raw is added in migration 005 after posts_raw is created.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "002"
down_revision: Union[str, None] = "001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "posts_features",
        sa.Column("post_id", sa.BigInteger(), nullable=False),
        sa.Column("text_length", sa.Integer(), nullable=True),
        sa.Column("word_count", sa.Integer(), nullable=True),
        sa.Column("reading_time", sa.Float(), nullable=True),
        sa.Column("complexity", sa.Float(), nullable=True),
        sa.Column("is_short_form", sa.Boolean(), nullable=True),
        sa.Column("is_long_form", sa.Boolean(), nullable=True),
        sa.Column("topic_1", sa.VARCHAR(), nullable=True),
        sa.Column("topic_1_score", sa.Float(), nullable=True),
        sa.Column("topic_2", sa.VARCHAR(), nullable=True),
        sa.Column("topic_2_score", sa.Float(), nullable=True),
        sa.Column("topic_3", sa.VARCHAR(), nullable=True),
        sa.Column("topic_3_score", sa.Float(), nullable=True),
        sa.Column("sentiment", sa.VARCHAR(), nullable=True),
        sa.Column("sentiment_score", sa.Float(), nullable=True),
        sa.Column("entities_persons", sa.JSON().with_variant(sa.dialects.postgresql.JSONB(), "postgresql"), nullable=True),
        sa.Column("entities_organizations", sa.JSON().with_variant(sa.dialects.postgresql.JSONB(), "postgresql"), nullable=True),
        sa.Column("entities_locations", sa.JSON().with_variant(sa.dialects.postgresql.JSONB(), "postgresql"), nullable=True),
        sa.Column("embedding", sa.Text(), nullable=True),  # VECTOR(312) stored as text placeholder; real type via raw SQL below
        sa.Column("processed_at", sa.TIMESTAMP(), nullable=True, server_default=sa.text("now()")),
        sa.PrimaryKeyConstraint("post_id"),
    )

    # Replace embedding column with proper VECTOR(312) type
    op.execute("ALTER TABLE posts_features DROP COLUMN embedding")
    op.execute("ALTER TABLE posts_features ADD COLUMN embedding vector(312)")

    # Index for post_id lookups
    op.create_index("idx_features_post_id", "posts_features", ["post_id"])


def downgrade() -> None:
    op.drop_index("idx_features_post_id", table_name="posts_features")
    op.drop_table("posts_features")
