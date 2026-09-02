"""Seed live_profile_params in data_flow.rec_config for Phase A live profile blending.

Revision ID: 013
Revises: 012
Create Date: 2026-04-19

Adds three tunable parameters that control the on-request embedding blend:
  blend        - alpha for (stored * blend + live * (1-blend)), default 0.6
  recent_n     - number of recent interactions to consider, default 15
  decay_hours  - exponential decay half-life for interaction age, default 24.0
"""

from typing import Sequence, Union

from alembic import op

revision: str = "013"
down_revision: Union[str, None] = "012"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        INSERT INTO data_flow.rec_config (key, value, description, updated_at)
        VALUES (
            'live_profile_params',
            '{"blend": 0.6, "recent_n": 15, "decay_hours": 24.0}',
            'Live profile recomputation: blend alpha, recent_n interactions, age decay hours',
            NOW()
        )
        ON CONFLICT (key) DO NOTHING
    """)


def downgrade() -> None:
    op.execute("DELETE FROM data_flow.rec_config WHERE key = 'live_profile_params'")
