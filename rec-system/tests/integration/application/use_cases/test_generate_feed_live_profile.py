"""Integration tests for GenerateFeedUseCase with LiveProfileService (Phase A).

Self-contained: each test manages its own PostgresContainer so there is no
dependency on the shared conftest that is broken by migration 009 FK issue.
The minimal schema is created via raw SQL (no Alembic) using only the tables
that GenerateFeedUseCase and LiveProfileService actually touch.
"""
from __future__ import annotations

import json
import math
import uuid
from datetime import datetime, timezone
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
import sqlalchemy
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from testcontainers.postgres import PostgresContainer

from src.application.dto.generate_feed import GenerateFeedRequest
from src.application.services.live_profile_service import LiveProfileService
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
# Minimal DDL (no FKs that reference tables from other services)
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
    ('feed', '{"max_age_hours": 168, "freshness_candidate_limit": 50, "embedding_candidate_limit": 50}'),
    ('dedup', '{"enable_dedup_clustering": false, "enable_related_spacing": false}'),
    ('live_profile_params', '{"blend": 0.6, "recent_n": 15, "decay_hours": 24.0}')
ON CONFLICT (key) DO NOTHING;
"""


# ---------------------------------------------------------------------------
# Per-test container + engine helpers
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
# Use case factories
# ---------------------------------------------------------------------------


def _make_use_case(
    session_factory,
    *,
    live_profile_service=None,
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
        live_profile_service=live_profile_service,
    )


# ---------------------------------------------------------------------------
# DB seed helpers
# ---------------------------------------------------------------------------


async def _seed_post(session, post_id: uuid.UUID, embedding=None, topic="технологии") -> uuid.UUID:
    """Insert posts_features + published_content row. Returns published_id.

    published_content.content_id = post_id (posts_features.post_id = raw_content.id).
    We skip raw_content insertion since posts_features has no FK in this test schema.
    """
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
    # published_content.content_id references posts_features.post_id
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


async def _seed_like(session, user_id: uuid.UUID, post_id: uuid.UUID) -> None:
    await session.execute(
        text("""
            INSERT INTO data_flow.user_interactions
                (event_id, user_id, post_id, event_type, created_at, processed)
            VALUES (:eid, :uid, :pid, 'LIKE', :now, false)
        """),
        {"eid": uuid.uuid4(), "uid": user_id, "pid": post_id, "now": datetime.now(timezone.utc)},
    )


# ---------------------------------------------------------------------------
# Test 1 — Feature flag OFF: live_profile_service.compute is never called
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_flag_off_live_profile_service_not_called():
    """When REC_FEATURE_LIVE_PROFILE=false, compute() must never be invoked."""
    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        sync_url = _sync_url(pg.get_connection_url())
        _setup_sync_schema(sync_url)
        async_url = _async_url(pg.get_connection_url())
        factory = _make_factory(async_url)

        user_id = uuid.uuid4()
        post_id = uuid.uuid4()

        async with factory() as session:
            await _seed_profile(session, user_id, embedding=[0.1] * _DIM)
            await _seed_post(session, post_id, embedding=[0.1] * _DIM)
            await session.commit()

        mock_live_service = MagicMock()
        mock_live_service.compute = AsyncMock()

        use_case = _make_use_case(factory, live_profile_service=mock_live_service)

        with patch.dict("os.environ", {"REC_FEATURE_LIVE_PROFILE": "false"}):
            request = GenerateFeedRequest(user_id=user_id, feed_size=10)
            await use_case.execute(request)

        mock_live_service.compute.assert_not_called()


# ---------------------------------------------------------------------------
# Test 2 — Flag ON + 0 interactions: compute returns None, profile unchanged
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_flag_on_zero_events_feed_proceeds_normally():
    """With REC_FEATURE_LIVE_PROFILE=true and 0 interactions, compute returns None
    so the profile embedding is not modified and the feed generates as normal.
    """
    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        sync_url = _sync_url(pg.get_connection_url())
        _setup_sync_schema(sync_url)
        async_url = _async_url(pg.get_connection_url())
        factory = _make_factory(async_url)

        user_id = uuid.uuid4()
        post_id = uuid.uuid4()
        stored_emb = [0.1] * _DIM

        async with factory() as session:
            await _seed_profile(session, user_id, embedding=stored_emb)
            await _seed_post(session, post_id, embedding=stored_emb)
            await session.commit()

        # Use REAL LiveProfileService — no interactions so it returns None
        classifier = SignalClassifier(_SIGNAL_WEIGHTS)
        live_service = LiveProfileService(
            interaction_repo=PgInteractionRepository(factory),
            content_repo=PgContentRepository(factory),
            signal_classifier=classifier,
        )
        use_case = _make_use_case(factory, live_profile_service=live_service)

        with patch.dict("os.environ", {"REC_FEATURE_LIVE_PROFILE": "true"}):
            request = GenerateFeedRequest(user_id=user_id, feed_size=10)
            response = await use_case.execute(request)

        # Feed should still be generated (profile was not changed, just passed through)
        assert response is not None
        assert response.feed is not None


# ---------------------------------------------------------------------------
# Test 3 — Flag ON + LIKE events: live embedding shifts toward liked posts
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_flag_on_with_likes_live_embedding_is_applied():
    """With REC_FEATURE_LIVE_PROFILE=true and LIKE events on axis-0 posts,
    the live embedding (axis-0 direction) is blended into the profile and used
    for scoring/retrieval.  We verify that compute() returns a non-None vector
    and that the feed generation completes without error.
    """
    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        sync_url = _sync_url(pg.get_connection_url())
        _setup_sync_schema(sync_url)
        async_url = _async_url(pg.get_connection_url())
        factory = _make_factory(async_url)

        user_id = uuid.uuid4()

        # 3 tech posts (axis-0 embedding direction)
        tech_ids = [uuid.uuid4() for _ in range(3)]
        tech_emb = [1.0] + [0.0] * (_DIM - 1)
        tech_emb_norm = [v / math.sqrt(sum(x * x for x in tech_emb)) for v in tech_emb]

        # Stored profile has neutral embedding (all equal, axis-perpendicular)
        stored_emb = [0.0] * _DIM
        stored_emb[1] = 1.0  # axis-1 direction — different from tech posts

        async with factory() as session:
            await _seed_profile(session, user_id, embedding=stored_emb)
            for tid in tech_ids:
                await _seed_post(session, tid, embedding=tech_emb_norm, topic="технологии")
            # Seed live_profile_params with blend=0.0 so compute returns pure live vector
            # (makes the axis-0 direction assertion unambiguous)
            await session.execute(
                text("""
                    INSERT INTO data_flow.rec_config (key, value)
                    VALUES ('live_profile_params', '{"blend": 0.0, "recent_n": 15, "decay_hours": 24.0}')
                    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """)
            )
            await session.commit()

        # Insert LIKE interactions for all 3 tech posts
        async with factory() as session:
            for tid in tech_ids:
                await _seed_like(session, user_id, tid)
            await session.commit()

        classifier = SignalClassifier(_SIGNAL_WEIGHTS)
        live_service = LiveProfileService(
            interaction_repo=PgInteractionRepository(factory),
            content_repo=PgContentRepository(factory),
            signal_classifier=classifier,
        )

        # Capture the embedding that compute() returns
        original_compute = live_service.compute
        captured_live_emb = {}

        async def _spy_compute(user_id, stored_profile, blend_alpha, recent_n, decay_hours=24.0):
            result = await original_compute(
                user_id, stored_profile, blend_alpha, recent_n, decay_hours
            )
            captured_live_emb["result"] = result
            return result

        live_service.compute = _spy_compute

        use_case = _make_use_case(factory, live_profile_service=live_service)

        with patch.dict("os.environ", {"REC_FEATURE_LIVE_PROFILE": "true"}):
            request = GenerateFeedRequest(user_id=user_id, feed_size=10)
            response = await use_case.execute(request)

        # live compute must have returned a non-None vector
        live_emb = captured_live_emb.get("result")
        assert live_emb is not None, "compute() should have returned a vector for 3 LIKE events"
        assert len(live_emb) == _DIM

        # The live embedding should be pointing in the tech direction (axis-0)
        assert live_emb[0] > live_emb[1], (
            f"Live emb should lean toward tech (axis-0={live_emb[0]:.3f} > axis-1={live_emb[1]:.3f})"
        )

        # Feed generation should complete
        assert response is not None
