"""Create rec tables in data_flow schema for content_agg_db integration.

Revision ID: 007
Revises: None
Create Date: 2026-04-03

ROOT MIGRATION for content_agg_db -- independent of 001-006 rec_db chain.

This migration creates rec-system-owned tables in the shared data_flow schema.
Run against content_agg_db (not rec_db). Set DATABASE_URL to content_agg_db URL.

Migration order:
1. content-aggregation-system Liquibase migration adds is_processed_by_rec column
2. This migration (007) creates rec tables in data_flow schema
3. rec-system code deployment wires up the new repository and scheduler
"""

from typing import Sequence, Union

from alembic import op

revision: str = "007"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = "rec_data_flow"
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Create data_flow schema if it does not yet exist
    # (may already exist if dedup-worker migration ran first, or if content-parser created it)
    op.execute("CREATE SCHEMA IF NOT EXISTS data_flow")

    # Enable pgvector extension (may already exist from dedup-worker migration)
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    # raw_content: incoming content items owned by content-parser-service (Kotlin).
    # rec-system reads from this table; parser-service owns DDL in production.
    # This stub is created here for test/dev environments where the Kotlin service is absent.
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.raw_content (
            id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            external_id         VARCHAR,
            source_id           UUID,
            source_type         VARCHAR,
            raw_data            JSONB,
            processing_status   VARCHAR,
            is_processed_by_rec BOOLEAN NOT NULL DEFAULT FALSE,
            received_at         TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    """)

    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_raw_content_unprocessed
            ON data_flow.raw_content (received_at)
            WHERE processing_status = 'COMPLETED' AND is_processed_by_rec = FALSE
    """)

    # posts_features: NLP-processed content features.
    # Phase 1 user-sources: source_id/source_type propagate from raw_content so feed
    # filtering by source is an index lookup (no JOIN). NOT NULL: rec-worker refuses
    # to process raw_content rows without a source.
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.posts_features (
            post_id                UUID NOT NULL REFERENCES data_flow.raw_content(id) ON DELETE CASCADE,
            source_id              UUID NOT NULL,
            source_type            VARCHAR(50) NOT NULL,
            text_length            INTEGER,
            word_count             INTEGER,
            reading_time           REAL,
            complexity             REAL,
            is_short_form          BOOLEAN,
            is_long_form           BOOLEAN,
            topic_1                VARCHAR(100),
            topic_1_score          REAL,
            topic_2                VARCHAR(100),
            topic_2_score          REAL,
            topic_3                VARCHAR(100),
            topic_3_score          REAL,
            sentiment              VARCHAR(20),
            sentiment_score        REAL,
            entities_persons       JSONB,
            entities_organizations JSONB,
            entities_locations     JSONB,
            embedding              vector(312),
            processed_at           TIMESTAMPTZ,
            PRIMARY KEY (post_id)
        )
    """)

    # HNSW index for ANN embedding similarity search (pgvector)
    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_posts_features_embedding
            ON data_flow.posts_features
            USING hnsw (embedding vector_cosine_ops)
            WITH (m = 16, ef_construction = 64)
    """)

    # Index for freshness-based candidate retrieval (ORDER BY processed_at DESC)
    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_posts_features_processed
            ON data_flow.posts_features (processed_at DESC)
    """)

    # Source-filtered candidate retrieval (Phase 1 user-sources).
    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_posts_features_source_id
            ON data_flow.posts_features (source_id)
    """)

    # Composite index for "source + freshness" candidate shape.
    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_posts_features_source_processed
            ON data_flow.posts_features (source_id, processed_at DESC)
    """)

    # rec_profiles: user preference profiles (EMA-updated)
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.rec_profiles (
            user_id           UUID PRIMARY KEY,
            topic_vector      JSONB NOT NULL DEFAULT '{}',
            embedding         vector(312),
            sentiment_prefs   JSONB NOT NULL DEFAULT '{}',
            format_prefs      JSONB NOT NULL DEFAULT '{}',
            interaction_count INTEGER NOT NULL DEFAULT 0,
            created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
            last_updated      TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    """)

    # rec_entity_interests: per-user entity interest weights (persons, orgs, locations)
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.rec_entity_interests (
            user_id     UUID NOT NULL,
            entity_type VARCHAR(50) NOT NULL,
            entity_name VARCHAR(255) NOT NULL,
            weight      REAL NOT NULL DEFAULT 0.0,
            last_seen   TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (user_id, entity_type, entity_name),
            FOREIGN KEY (user_id) REFERENCES data_flow.rec_profiles(user_id) ON DELETE CASCADE
        )
    """)

    op.execute("""
        CREATE INDEX IF NOT EXISTS idx_rec_entity_interests_user
            ON data_flow.rec_entity_interests (user_id)
    """)

    # rec_config: recommendation configuration (key-value, all weights/thresholds tunable)
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.rec_config (
            key         VARCHAR(100) PRIMARY KEY,
            value       JSONB NOT NULL,
            description TEXT,
            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    """)

    # user_interactions: user interaction events (Kotlin-owned in production, rec-system reads)
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.user_interactions (
            id            BIGSERIAL PRIMARY KEY,
            event_id      UUID NOT NULL UNIQUE,
            user_id       UUID,
            post_id       UUID,
            event_type    VARCHAR(50),
            duration_ms   INTEGER,
            scroll_pct    REAL,
            max_scroll_pct REAL,
            created_at    TIMESTAMPTZ,
            processed     BOOLEAN DEFAULT FALSE
        )
    """)

    # user_disliked_posts: per-user disliked post IDs (Kotlin-owned in production, rec-system reads)
    op.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.user_disliked_posts (
            user_id UUID NOT NULL,
            post_id UUID NOT NULL,
            PRIMARY KEY (user_id, post_id)
        )
    """)

    # Seed default configuration matching design.md section "Configuration"
    op.execute("""
        INSERT INTO data_flow.rec_config (key, value, description) VALUES
            ('signal_weights',
             '{"impression_read": 0.15, "impression_skip": -0.05, "close_bounce": -0.20, "close_read_full": 0.50, "close_read_half": 0.40, "close_other": 0.10, "like": 0.60, "dislike": -0.70, "bookmark": 0.80}',
             'Signal classification weights'),
            ('scoring_weights',
             '{"topic": 0.30, "embedding": 0.25, "entity": 0.15, "sentiment": 0.05, "freshness": 0.15, "format": 0.10}',
             'Scoring component weights'),
            ('profile_params',
             '{"lr": 0.08, "entity_decay": 0.95, "cleanup_days": 30, "job_interval_minutes": 5, "batch_threshold": 20}',
             'Profile update parameters'),
            ('ranking_params',
             '{"halflife_hours": 48, "freshness_limit": 300, "embedding_limit": 200, "feed_size": 200, "page_size": 30, "streak": 3, "ratio": 0.40, "max_age_days": 7}',
             'Ranking and diversity parameters'),
            ('onboarding_params',
             '{"baseline": 0.01, "min_topics": 3, "max_topics": 5}',
             'Onboarding configuration'),
            ('narrow_ranking_weights',
             '{"cosine": 0.7, "freshness": 0.3, "halflife_hours": 48}',
             'Narrow ranking mode weights (cosine+recency only, no diversity)')
        ON CONFLICT (key) DO NOTHING
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS data_flow.user_disliked_posts CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.user_interactions CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.rec_config CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.rec_entity_interests CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.rec_profiles CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.posts_features CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.raw_content CASCADE")
