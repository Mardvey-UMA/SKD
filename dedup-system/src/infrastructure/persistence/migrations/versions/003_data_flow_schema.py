"""Migrate dedup tables to data_flow schema in content_agg_db.

Revision ID: 003
Revises: 002
Create Date: 2026-04-02
"""
from alembic import op
import sqlalchemy as sa

revision = "003"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Ensure pgvector extension exists (belt-and-suspenders with init-schemas.sh)
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    # Create sequence in data_flow schema
    op.execute("CREATE SEQUENCE IF NOT EXISTS data_flow.batch_seq")

    # Create articles table (dedup-owned)
    # NOTE: raw_content_id is UUID to match parser's raw_content.id
    # raw_content is created by parser-liquibase before dedup-alembic runs
    op.execute("""
        CREATE TABLE data_flow.articles (
            id              BIGSERIAL       PRIMARY KEY,
            raw_content_id  UUID            NOT NULL UNIQUE REFERENCES data_flow.raw_content(id) ON DELETE CASCADE,
            content_hash    TEXT            NOT NULL,
            normalized_text TEXT            NOT NULL,
            embedding       vector(1024),
            source          TEXT,
            created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
        )
    """)
    op.execute("CREATE INDEX idx_articles_hash ON data_flow.articles(content_hash)")
    op.execute("CREATE INDEX idx_articles_created ON data_flow.articles(created_at)")
    op.execute("""
        CREATE INDEX idx_articles_embedding ON data_flow.articles
            USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)
    """)

    # Create similarities table
    op.execute("""
        CREATE TABLE data_flow.similarities (
            article_a   BIGINT NOT NULL REFERENCES data_flow.articles(id),
            article_b   BIGINT NOT NULL REFERENCES data_flow.articles(id),
            score       REAL   NOT NULL CHECK (score BETWEEN 0 AND 1),
            rel_type    TEXT   NOT NULL,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (article_a, article_b),
            CHECK (article_a < article_b)
        )
    """)
    op.execute("CREATE INDEX idx_sim_a ON data_flow.similarities(article_a)")
    op.execute("CREATE INDEX idx_sim_b ON data_flow.similarities(article_b)")
    op.execute("CREATE INDEX idx_sim_type ON data_flow.similarities(rel_type)")

    # Create dedup_config table (renamed from 'config' to avoid ambiguity)
    op.execute("""
        CREATE TABLE data_flow.dedup_config (
            key         TEXT PRIMARY KEY,
            value       TEXT NOT NULL,
            description TEXT,
            updated_at  TIMESTAMPTZ
        )
    """)
    op.execute("""
        INSERT INTO data_flow.dedup_config (key, value, description, updated_at) VALUES
            ('threshold_duplicate', '0.85', 'Score >= this -> DUPLICATE', now()),
            ('threshold_related', '0.70', 'Score >= this (and < dup) -> RELATED', now()),
            ('search_top_k', '20', 'Nearest neighbors per article', now()),
            ('search_window_hours', '72', 'Time window for neighbor search', now()),
            ('batch_size', '64', 'Rows per worker cycle', now()),
            ('encoding_batch_size', '32', 'Batch size for model.encode()', now())
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS data_flow.dedup_config CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.similarities CASCADE")
    op.execute("DROP TABLE IF EXISTS data_flow.articles CASCADE")
    op.execute("DROP SEQUENCE IF EXISTS data_flow.batch_seq CASCADE")
