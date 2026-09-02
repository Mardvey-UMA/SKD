"""Add data_flow.rec_user_blocked_sources — per-user source blacklist (Phase 1 user-sources).

Revision ID: 012
Revises: 011
Create Date: 2026-04-16

rec-system owns this table (DDL + full CRUD). GenerateFeedUseCase merges the
blacklist into the exclude set on every /recommendations call. No FK to
config.sources (catalog lives in another service) or rec_profiles (blocking
can happen before the cold-start profile row is materialised).
"""

from typing import Sequence, Union

from alembic import op

revision: str = "012"
down_revision: Union[str, None] = "011"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.rec_user_blocked_sources (
            user_id    UUID NOT NULL,
            source_id  UUID NOT NULL,
            blocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (user_id, source_id)
        )
    """)

    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_blocked_sources_user
            ON data_flow.rec_user_blocked_sources (user_id)
    """)


def downgrade() -> None:
    op.execute(
        "DROP INDEX IF EXISTS data_flow.idx_blocked_sources_user"
    )
    op.execute("DROP TABLE IF EXISTS data_flow.rec_user_blocked_sources")
