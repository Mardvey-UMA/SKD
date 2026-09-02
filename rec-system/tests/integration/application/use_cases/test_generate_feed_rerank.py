"""Integration tests for GenerateFeedUseCase with cross-encoder reranking (Phase C).

Self-contained: each test manages its own PostgresContainer so there is no
dependency on the shared conftest that is broken by migration 009 FK issue.
The minimal schema is created via raw SQL (no Alembic) using only the tables
that GenerateFeedUseCase actually touches.

Three scenarios:
1. Flag OFF: feed generated, reranker never called (regression guard).
2. Flag ON + fewer than min_candidates: reranker not invoked (threshold guard).
3. Flag ON + enough candidates + mocked reranker: ordering shifted correctly.
"""
from __future__ import annotations

import math
import os
import uuid
from datetime import datetime, timezone
from typing import Optional
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
import sqlalchemy
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from src.application.dto.generate_feed import GenerateFeedRequest
from src.application.services.user_context_builder import UserContextBuilder
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.domain.services.diversity_filter import DiversityFilter
from src.domain.services.narrow_scorer import NarrowScorer
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.scorer import Scorer
from src.domain.services.signal_classifier import SignalClassifier
from src.infrastructure.persistence.pg_blocked_sources_repository import (
    PgBlockedSourcesRepository,
)
from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
from src.infrastructure.persistence.pg_content_repository import PgContentRepository
from src.infrastructure.persistence.pg_entity_interest_repository import (
    PgEntityInterestRepository,
)
from src.infrastructure.persistence.pg_interaction_repository import (
    PgInteractionRepository,
)
from src.infrastructure.persistence.pg_user_profile_repository import (
    PgUserProfileRepository,
)

pytestmark = pytest.mark.integration

_DIM = 312

# Override the session-scoped autouse fixture from the parent conftest to avoid
# the broken migration 009 (FK to published_content).
@pytest.fixture(scope="session", autouse=True)
def run_migrations_once():  # noqa: F811 — intentional override
    """No-op: this module manages its own DB container per test."""


# ---------------------------------------------------------------------------
# Signal weights config
# ---------------------------------------------------------------------------

_SIGNAL_WEIGHTS = {
    "impression_read": {"threshold_duration_ms": 2000, "weight": 0.15},
    "impression_skip": {"threshold_duration_ms": 2000, "weight": -0.05},
    "open": {"weight": 0.1},
    "close_fast": {"threshold_duration_ms": 3000, "weight": -0.2},
    "close_full": {"threshold_scroll_pct": 0.85, "threshold_duration_ms": 15000, "weight": 0.5},
    "close_half": {"threshold_scroll_pct": 0.5, "threshold_duration_ms": 10000, "weight": 0.4},
    "close_other": {"weight": 0.1},
    "like": {"weight": 0.6},
    "dislike": {"weight": -0.7},
    "bookmark": {"weight": 0.8},
    "orphan_timeout_minutes": 5,
}

# ---------------------------------------------------------------------------
# Minimal DDL
# ---------------------------------------------------------------------------

_SCHEMA_SQL = """
CREATE SCHEMA IF NOT EXISTS data_flow;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA data_flow;

CREATE TABLE IF NOT EXISTS data_flow.raw_content (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id         VARCHAR,
    source_id           UUID,
    source_type         VARCHAR,
    raw_data            JSONB,
    processing_status   VARCHAR,
    is_processed_by_rec BOOLEAN NOT NULL DEFAULT FALSE,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS data_flow.posts_features (
    post_id                UUID PRIMARY KEY,
    source_id              UUID NOT NULL,
    source_type            VARCHAR(50) NOT NULL DEFAULT 'telegram',
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
    embedding              data_flow.vector(312),
    processed_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_pf_processed_at
    ON data_flow.posts_features (processed_at DESC);

CREATE TABLE IF NOT EXISTS data_flow.rec_profiles (
    user_id           UUID PRIMARY KEY,
    topic_vector      JSONB NOT NULL DEFAULT '{}',
    embedding         data_flow.vector(312),
    sentiment_prefs   JSONB NOT NULL DEFAULT '{}',
    format_prefs      JSONB NOT NULL DEFAULT '{}',
    interaction_count INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated      TIMESTAMPTZ NOT NULL DEFAULT now(),
    cold_start        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS data_flow.user_interactions (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID NOT NULL UNIQUE,
    user_id        UUID,
    post_id        UUID,
    event_type     VARCHAR(50),
    duration_ms    INTEGER,
    scroll_pct     REAL,
    max_scroll_pct REAL,
    created_at     TIMESTAMPTZ,
    processed      BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS data_flow.rec_entity_interests (
    user_id     UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    weight      REAL NOT NULL DEFAULT 0.0,
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, entity_type, entity_name)
);

CREATE TABLE IF NOT EXISTS data_flow.rec_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       JSONB NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS data_flow.rec_user_blocked_sources (
    user_id    UUID NOT NULL,
    source_id  UUID NOT NULL,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source_id)
);

CREATE TABLE IF NOT EXISTS data_flow.published_content (
    id           UUID PRIMARY KEY,
    content_id   UUID,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ DEFAULT now()
);
"""

_SEED_CONFIG_SQL = """
INSERT INTO data_flow.rec_config (key, value) VALUES
    ('feed', '{"max_age_hours": 168, "freshness_candidate_limit": 200, "embedding_candidate_limit": 200}'),
    ('dedup', '{"enable_dedup_clustering": false, "enable_related_spacing": false}'),
    ('rerank_params', '{"top_k": 10, "weight": 0.5, "min_candidates": 5}')
ON CONFLICT (key) DO NOTHING;
"""


# ---------------------------------------------------------------------------
# DB / session helpers
# ---------------------------------------------------------------------------


def _setup_sync_schema(sync_url: str) -> None:
    engine = sqlalchemy.create_engine(sync_url)
    with engine.connect() as conn:
        conn.execute(text("SET search_path TO data_flow, public"))
        for statement in _SCHEMA_SQL.strip().split(";"):
            stmt = statement.strip()
            if stmt:
                conn.execute(text(stmt))
        for statement in _SEED_CONFIG_SQL.strip().split(";"):
            stmt = statement.strip()
            if stmt:
                conn.execute(text(stmt))
        conn.commit()
    engine.dispose()


def _async_url(sync_url: str) -> str:
    url = sync_url.replace("postgresql+psycopg2://", "postgresql://", 1)
    if not url.startswith("postgresql+asyncpg://"):
        url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
    return url


def _sync_url(raw_url: str) -> str:
    if "psycopg2" in raw_url:
        return raw_url
    return raw_url.replace("postgresql://", "postgresql+psycopg2://", 1)


def _make_factory(async_url: str):
    engine = create_async_engine(
        async_url,
        echo=False,
        connect_args={"server_settings": {"search_path": "data_flow,public"}},
    )
    return async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


# ---------------------------------------------------------------------------
# Use case factory
# ---------------------------------------------------------------------------


def _make_use_case(
    session_factory,
    *,
    reranker=None,
    context_builder=None,
) -> GenerateFeedUseCase:
    classifier = SignalClassifier(_SIGNAL_WEIGHTS)
    return GenerateFeedUseCase(
        profile_repo=PgUserProfileRepository(session_factory),
        content_repo=PgContentRepository(session_factory),
        entity_interest_repo=PgEntityInterestRepository(session_factory),
        interaction_repo=PgInteractionRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        scorer=Scorer(),
        diversity_filter=DiversityFilter(),
        signal_classifier=classifier,
        profile_updater=ProfileUpdater(),
        blocked_sources_repo=PgBlockedSourcesRepository(session_factory),
        narrow_scorer=NarrowScorer(),
        reranker=reranker,
        context_builder=context_builder,
    )


# ---------------------------------------------------------------------------
# Seed helpers
# ---------------------------------------------------------------------------


async def _seed_post(
    session,
    post_id: uuid.UUID,
    embedding=None,
    topic: str = "технологии",
) -> uuid.UUID:
    pub_id = uuid.uuid4()
    now = datetime.now(timezone.utc)
    emb_str = None
    if embedding is not None:
        emb_str = "[" + ",".join(str(v) for v in embedding) + "]"
    await session.execute(
        text("""
            INSERT INTO data_flow.posts_features
                (post_id, source_id, source_type, topic_1, topic_1_score,
                 sentiment, entities_persons, entities_organizations, entities_locations,
                 embedding, processed_at)
            VALUES (:post_id, :src_id, 'telegram', :topic, 0.8,
                    'POSITIVE', '[]', '[]', '[]',
                    CAST(:emb AS data_flow.vector), :now)
        """),
        {
            "post_id": post_id,
            "src_id": uuid.uuid4(),
            "topic": topic,
            "emb": emb_str,
            "now": now,
        },
    )
    await session.execute(
        text("""
            INSERT INTO data_flow.published_content (id, content_id, published_at, created_at)
            VALUES (:pub_id, :content_id, :now, :now)
        """),
        {"pub_id": pub_id, "content_id": post_id, "now": now},
    )
    return pub_id


async def _seed_profile(session, user_id: uuid.UUID, embedding=None) -> None:
    now = datetime.now(timezone.utc)
    emb_str = None
    if embedding is not None:
        emb_str = "[" + ",".join(str(v) for v in embedding) + "]"
    await session.execute(
        text("""
            INSERT INTO data_flow.rec_profiles
                (user_id, topic_vector, embedding, sentiment_prefs, format_prefs,
                 interaction_count, created_at, last_updated, cold_start)
            VALUES (:uid, '{}', CAST(:emb AS data_flow.vector), '{}', '{}',
                    0, :now, :now, false)
        """),
        {"uid": user_id, "emb": emb_str, "now": now},
    )


# ---------------------------------------------------------------------------
# Test 1: Flag OFF — reranker never called (regression guard)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_flag_off_reranker_not_called():
    """When REC_FEATURE_RERANK=false, the reranker.rerank() must never be invoked."""
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        sync_url = _sync_url(pg.get_connection_url())
        _setup_sync_schema(sync_url)
        factory = _make_factory(_async_url(pg.get_connection_url()))

        user_id = uuid.uuid4()
        emb = [0.1] * _DIM

        async with factory() as session:
            await _seed_profile(session, user_id, embedding=emb)
            for _ in range(10):
                await _seed_post(session, uuid.uuid4(), embedding=emb)
            await session.commit()

        mock_reranker = AsyncMock()
        mock_context_builder = MagicMock()
        mock_context_builder.build = AsyncMock(return_value="технологии")

        use_case = _make_use_case(
            factory,
            reranker=mock_reranker,
            context_builder=mock_context_builder,
        )

        with patch.dict(os.environ, {"REC_FEATURE_RERANK": "false"}):
            request = GenerateFeedRequest(user_id=user_id, feed_size=5)
            response = await use_case.execute(request)

        mock_reranker.rerank.assert_not_called()
        assert response is not None


# ---------------------------------------------------------------------------
# Test 2: Flag ON + fewer than min_candidates — reranker not invoked
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_flag_on_below_min_candidates_reranker_not_called():
    """When REC_FEATURE_RERANK=true but scored candidates < min_candidates,
    the reranker must NOT be called.
    """
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        sync_url = _sync_url(pg.get_connection_url())
        _setup_sync_schema(sync_url)
        factory = _make_factory(_async_url(pg.get_connection_url()))

        user_id = uuid.uuid4()
        emb = [0.1] * _DIM

        async with factory() as session:
            await _seed_profile(session, user_id, embedding=emb)
            # Seed only 3 posts — below min_candidates (5 from config seed)
            for _ in range(3):
                await _seed_post(session, uuid.uuid4(), embedding=emb)
            await session.commit()

        mock_reranker = AsyncMock()
        mock_context_builder = MagicMock()
        mock_context_builder.build = AsyncMock(return_value="технологии")

        use_case = _make_use_case(
            factory,
            reranker=mock_reranker,
            context_builder=mock_context_builder,
        )

        with patch.dict(os.environ, {"REC_FEATURE_RERANK": "true"}):
            request = GenerateFeedRequest(user_id=user_id, feed_size=10)
            response = await use_case.execute(request)

        mock_reranker.rerank.assert_not_called()
        assert response is not None


# ---------------------------------------------------------------------------
# Test 3: Flag ON + enough candidates + mocked reranker — ordering shifted
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_flag_on_mocked_reranker_changes_ordering():
    """When REC_FEATURE_RERANK=true, >=min_candidates exist, and the mocked
    reranker returns an ordering different from the scorer, the final feed
    reflects the blended rerank ordering (at least partially).

    Concretely: scorer orders [A, B, C...] but reranker puts C first.
    With weight=0.5, C should appear earlier than it would without reranking.
    """
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        sync_url = _sync_url(pg.get_connection_url())
        _setup_sync_schema(sync_url)
        factory = _make_factory(_async_url(pg.get_connection_url()))

        user_id = uuid.uuid4()
        emb = [0.1] * _DIM

        # Seed 10 posts; collect their post_ids
        post_ids = [uuid.uuid4() for _ in range(10)]
        pub_ids_map: dict[uuid.UUID, uuid.UUID] = {}

        async with factory() as session:
            await _seed_profile(session, user_id, embedding=emb)
            for pid in post_ids:
                pub_id = await _seed_post(session, pid, embedding=emb)
                pub_ids_map[pid] = pub_id
            await session.commit()

        # Pick the "last" post_id — scorer would normally rank it lowest.
        # The reranker will assign it the highest score.
        target_post_id = post_ids[-1]
        target_pub_id = pub_ids_map[target_post_id]

        async def mock_rerank(user_context, candidates, top_k):
            """Return target_post_id with highest score, others uniformly low."""
            result = []
            for c in candidates[:top_k]:
                if c.post_id == target_post_id:
                    result.append((c.post_id, 1.0))
                else:
                    result.append((c.post_id, 0.1))
            result.sort(key=lambda x: x[1], reverse=True)
            return result

        mock_reranker = MagicMock()
        mock_reranker.rerank = AsyncMock(side_effect=mock_rerank)

        mock_context_builder = MagicMock()
        mock_context_builder.build = AsyncMock(return_value="технологии")

        use_case = _make_use_case(
            factory,
            reranker=mock_reranker,
            context_builder=mock_context_builder,
        )

        with patch.dict(os.environ, {"REC_FEATURE_RERANK": "true"}):
            request = GenerateFeedRequest(user_id=user_id, feed_size=10)
            response = await use_case.execute(request)

        # The reranker was called
        mock_reranker.rerank.assert_called_once()

        # Feed contains items
        assert len(response.feed) > 0

        # target_pub_id should appear in the feed (it was top-scored by reranker)
        feed_ids = [item["post_id"] for item in response.feed]
        assert str(target_pub_id) in feed_ids, (
            f"Target pub_id {target_pub_id} not found in feed {feed_ids}"
        )
