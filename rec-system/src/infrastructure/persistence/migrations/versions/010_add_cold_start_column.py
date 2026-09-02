"""Add cold_start column to data_flow.rec_profiles.

Revision ID: 010
Revises: 009
Create Date: 2026-04-03

Explicitly tracks whether a user has completed onboarding.
- true  (default) = user has NOT completed onboarding (cold start)
- false = user has completed onboarding (warm profile)

Set to true on user.created Kafka event.
Set to false after POST /onboarding completes.
"""

from typing import Sequence, Union

from alembic import op

revision: str = "010"
down_revision: Union[str, None] = "009"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        ALTER TABLE data_flow.rec_profiles
            ADD COLUMN cold_start BOOLEAN NOT NULL DEFAULT true
    """)


def downgrade() -> None:
    op.execute("""
        ALTER TABLE data_flow.rec_profiles
            DROP COLUMN IF EXISTS cold_start
    """)
