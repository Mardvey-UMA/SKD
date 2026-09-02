"""Add views column to posts_raw table.

Revision ID: 006
Revises: 005
Create Date: 2026-03-31

The posts_raw ORM model includes a views column for Telegram post view counts.
This migration adds it to the Kotlin-owned posts_raw table.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "006"
down_revision: Union[str, None] = "005"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "posts_raw",
        sa.Column("views", sa.Integer(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("posts_raw", "views")
