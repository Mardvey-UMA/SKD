"""Create Kotlin-owned tables for test support + rec_config seed data.

Revision ID: 005
Revises: 004
Create Date: 2026-03-31

These tables are owned by the Kotlin service in production.
This migration creates them in the test/dev database so that Python
integration and E2E tests can run without the Kotlin service.

Tables created:
- posts_raw          — raw Telegram posts (Kotlin parser writes)
- user_interactions  — user event log (Kotlin writes, Python reads)
- user_disliked_posts— dislike cache (Kotlin writes, Python reads)
- user_profiles      — static user data (Kotlin writes, Python reads)
- rec_config         — recommendation config (admin updates, Python reads)

Seed data: rec_config is populated with all default values from design/09-configuration.md.
"""

import json
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "005"
down_revision: Union[str, None] = "004"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


# ---------------------------------------------------------------------------
# Default rec_config seed values (from design/09-configuration.md)
# ---------------------------------------------------------------------------

_SEED_CONFIG = [
    {
        "key": "signal_weights",
        "value": {
            "impression_read":  {"threshold_duration_ms": 2000, "weight": 0.15},
            "impression_skip":  {"threshold_duration_ms": 2000, "weight": -0.05},
            "open":             {"weight": 0.1},
            "close_fast":       {"threshold_duration_ms": 3000, "weight": -0.2},
            "close_full":       {"threshold_scroll_pct": 0.85, "threshold_duration_ms": 15000, "weight": 0.5},
            "close_half":       {"threshold_scroll_pct": 0.5,  "threshold_duration_ms": 10000, "weight": 0.4},
            "close_other":      {"weight": 0.1},
            "like":             {"weight": 0.6},
            "dislike":          {"weight": -0.7},
            "bookmark":         {"weight": 0.8},
        },
        "description": "Event-to-weight mapping for profile updates",
    },
    {
        "key": "scoring_weights",
        "value": {
            "topic_match":     0.30,
            "embedding_sim":   0.25,
            "entity_match":    0.15,
            "sentiment_match": 0.05,
            "freshness":       0.15,
            "format_match":    0.10,
        },
        "description": "Weights for the 6-component scoring formula (must sum to 1.0)",
    },
    {
        "key": "profile_params",
        "value": {
            "learning_rate":                  0.08,
            "entity_min_weight":              0.4,
            "entity_max_per_post":            5,
            "entity_cleanup_days":            30,
            "entity_decay_factor":            0.95,
            "job_interval_minutes":           5,
            "job_batch_threshold":            20,
            "open_orphan_timeout_minutes":    5,
        },
        "description": "Parameters controlling profile update behaviour",
    },
    {
        "key": "ranking_params",
        "value": {
            "freshness_halflife_hours":   48,
            "candidate_freshness_limit":  300,
            "candidate_embedding_limit":  200,
            "feed_size":                  200,
            "page_size":                  30,
            "max_topic_streak":           3,
            "max_topic_ratio":            0.40,
            "candidate_max_age_days":     7,
        },
        "description": "Parameters for candidate retrieval and feed ranking",
    },
    {
        "key": "onboarding_params",
        "value": {
            "baseline_weight": 0.01,
            "min_topics":      3,
            "max_topics":      5,
        },
        "description": "Parameters for the onboarding topic selection flow",
    },
]


def upgrade() -> None:
    # ------------------------------------------------------------------
    # posts_raw — raw Telegram posts, owned by Kotlin
    # ------------------------------------------------------------------
    op.create_table(
        "posts_raw",
        sa.Column("id", sa.BigInteger(), nullable=False, autoincrement=True),
        sa.Column("channel_id", sa.BigInteger(), nullable=True),
        sa.Column("content", sa.Text(), nullable=True),
        sa.Column("post_date", sa.TIMESTAMP(), nullable=True),
        sa.Column("is_processed", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("created_at", sa.TIMESTAMP(), nullable=True, server_default=sa.text("now()")),
        sa.PrimaryKeyConstraint("id"),
    )

    # Partial index for unprocessed posts (content processing job)
    op.execute(
        "CREATE INDEX idx_posts_raw_unprocessed ON posts_raw(created_at) WHERE is_processed = false"
    )

    # ------------------------------------------------------------------
    # user_interactions — event log, Kotlin writes / Python reads
    # ------------------------------------------------------------------
    op.create_table(
        "user_interactions",
        sa.Column("id", sa.BigInteger(), nullable=False, autoincrement=True),
        sa.Column("event_id", postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=False), nullable=True),
        sa.Column("post_id", sa.BigInteger(), nullable=True),
        sa.Column("event_type", sa.VARCHAR(), nullable=True),
        sa.Column("duration_ms", sa.Integer(), nullable=True),
        sa.Column("scroll_pct", sa.Float(), nullable=True),
        sa.Column("max_scroll_pct", sa.Float(), nullable=True),
        sa.Column("created_at", sa.TIMESTAMP(), nullable=True, server_default=sa.text("now()")),
        sa.Column("processed", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("event_id", name="uq_user_interactions_event_id"),
    )

    # Partial index for unprocessed events (profile update job)
    op.execute(
        "CREATE INDEX idx_interactions_unprocessed ON user_interactions(user_id) WHERE processed = false"
    )
    # Lookup by user+post
    op.create_index("idx_interactions_user_post", "user_interactions", ["user_id", "post_id"])

    # ------------------------------------------------------------------
    # user_disliked_posts — dislike cache, Kotlin writes / Python reads
    # ------------------------------------------------------------------
    op.create_table(
        "user_disliked_posts",
        sa.Column("user_id", postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column("post_id", sa.BigInteger(), nullable=False),
        sa.PrimaryKeyConstraint("user_id", "post_id"),
    )

    op.create_index("idx_disliked", "user_disliked_posts", ["user_id"])

    # ------------------------------------------------------------------
    # user_profiles — static user data, Kotlin writes / Python reads
    # ------------------------------------------------------------------
    op.create_table(
        "user_profiles",
        sa.Column("user_id", postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column("display_name", sa.VARCHAR(), nullable=True),
        sa.Column("email", sa.VARCHAR(), nullable=True),
        sa.Column("avatar_url", sa.VARCHAR(), nullable=True),
        sa.Column("created_at", sa.TIMESTAMP(), nullable=True, server_default=sa.text("now()")),
        sa.Column("onboarding_done", sa.Boolean(), nullable=True, server_default=sa.text("false")),
        sa.Column("last_active_at", sa.TIMESTAMP(), nullable=True),
        sa.PrimaryKeyConstraint("user_id"),
    )

    # ------------------------------------------------------------------
    # rec_config — tunable parameters, admin updates / Python reads
    # ------------------------------------------------------------------
    op.create_table(
        "rec_config",
        sa.Column("key", sa.VARCHAR(), nullable=False),
        sa.Column("value", postgresql.JSONB(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("updated_at", sa.TIMESTAMP(), nullable=True, server_default=sa.text("now()")),
        sa.PrimaryKeyConstraint("key"),
    )

    # Seed default config values
    rec_config = sa.table(
        "rec_config",
        sa.column("key", sa.VARCHAR()),
        sa.column("value", postgresql.JSONB()),
        sa.column("description", sa.Text()),
    )
    op.bulk_insert(
        rec_config,
        [
            {
                "key": row["key"],
                "value": row["value"],
                "description": row["description"],
            }
            for row in _SEED_CONFIG
        ],
    )

    # ------------------------------------------------------------------
    # FK: posts_features.post_id -> posts_raw.id
    # Now that posts_raw exists we can add the FK constraint.
    # ------------------------------------------------------------------
    op.create_foreign_key(
        "fk_posts_features_post_id",
        "posts_features",
        "posts_raw",
        ["post_id"],
        ["id"],
        ondelete="CASCADE",
    )

    # ------------------------------------------------------------------
    # FK: user_interactions.post_id -> posts_raw.id
    # ------------------------------------------------------------------
    op.create_foreign_key(
        "fk_user_interactions_post_id",
        "user_interactions",
        "posts_raw",
        ["post_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    # Drop FK constraints first
    op.drop_constraint("fk_user_interactions_post_id", "user_interactions", type_="foreignkey")
    op.drop_constraint("fk_posts_features_post_id", "posts_features", type_="foreignkey")

    op.drop_index("idx_disliked", table_name="user_disliked_posts")
    op.drop_table("user_disliked_posts")

    op.drop_index("idx_interactions_user_post", table_name="user_interactions")
    op.execute("DROP INDEX IF EXISTS idx_interactions_unprocessed")
    op.drop_table("user_interactions")

    op.execute("DROP INDEX IF EXISTS idx_posts_raw_unprocessed")
    op.drop_table("posts_raw")

    op.drop_table("user_profiles")
    op.drop_table("rec_config")
