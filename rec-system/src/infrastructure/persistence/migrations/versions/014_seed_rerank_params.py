"""Seed rerank_params in data_flow.rec_config for Phase C cross-encoder reranking.

Revision ID: 014
Revises: 013
Create Date: 2026-04-19

Adds three tunable parameters that control the cross-encoder reranking step:
  top_k           - number of top candidates passed to the cross-encoder
  weight          - blend coefficient (0=pure original score, 1=pure rerank score)
  min_candidates  - minimum candidate count required to engage the reranker
"""

from typing import Sequence, Union

from alembic import op

revision: str = "014"
down_revision: Union[str, None] = "013"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        INSERT INTO data_flow.rec_config (key, value, description, updated_at)
        VALUES (
            'rerank_params',
            '{"top_k": 100, "weight": 0.5, "min_candidates": 30}',
            'Cross-encoder reranking: top_k candidates scored, blend weight, min candidates to engage',
            NOW()
        )
        ON CONFLICT (key) DO NOTHING
    """)


def downgrade() -> None:
    op.execute("DELETE FROM data_flow.rec_config WHERE key = 'rerank_params'")
