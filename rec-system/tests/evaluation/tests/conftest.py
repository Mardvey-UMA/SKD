"""Testcontainers fixtures for evaluation integration tests.

Design:
- All session-scoped fixtures are SYNCHRONOUS to avoid asyncpg event-loop
  isolation issues between session-scoped setup and function-scoped tests.
- eval_session_factory is function-scoped (creates a fresh engine per test),
  matching the pattern in tests/integration/conftest.py.
- Corpus seeding (session-scoped) uses psycopg2 (sync) directly.
"""
from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Callable
from urllib.parse import urlparse

import psycopg2
import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from testcontainers.postgres import PostgresContainer

from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.application.use_cases.update_profile import UpdateProfileUseCase
from src.domain.services.onboarding_service import OnboardingService
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.signal_classifier import SignalClassifier
from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
from src.infrastructure.persistence.pg_content_repository import PgContentRepository
from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository
from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository
from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository
from tests.evaluation.behavior_simulator import BehaviorSimulator

PROJECT_ROOT = Path(__file__).parent.parent.parent.parent
ALEMBIC_INI = PROJECT_ROOT / "alembic.ini"

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

_PROFILE_CONFIG = {
    "learning_rate": 0.08,
    "entity_min_weight": 0.4,
    "entity_max_per_post": 5,
    "entity_cleanup_days": 30,
    "entity_decay_factor": 0.95,
}

_ONBOARDING_CONFIG = {
    "baseline": 0.01,
    "min_topics": 3,
    "max_topics": 5,
}

# Synthetic corpus: (topic_1, score_1, topic_2, score_2, topic_3, score_3, count)
_CORPUS_TOPICS = [
    ("технологии", 0.85, "наука", 0.10, "бизнес", 0.05, 30),
    ("наука", 0.80, "технологии", 0.15, "образование", 0.05, 15),
    ("политика", 0.82, "общество", 0.12, "международные новости", 0.06, 15),
    ("спорт", 0.88, None, None, None, None, 10),
    ("бизнес", 0.78, "экономика", 0.15, "финансы", 0.07, 10),
    ("международные новости", 0.80, "политика", 0.15, "общество", 0.05, 8),
    ("развлечения", 0.83, "культура", 0.12, None, None, 6),
    ("здоровье", 0.86, "наука", 0.09, None, None, 3),
    ("образование", 0.80, "наука", 0.12, "технологии", 0.08, 3),
]

_FIXED_SOURCE_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _psycopg2_conn(sync_url: str):
    """Open a psycopg2 connection from a SQLAlchemy sync URL."""
    parsed = urlparse(sync_url.replace("postgresql+psycopg2://", "postgresql://"))
    return psycopg2.connect(
        host=parsed.hostname,
        port=parsed.port,
        dbname=parsed.path.lstrip("/"),
        user=parsed.username,
        password=parsed.password,
    )


# ---------------------------------------------------------------------------
# Session-scoped container + migrations (all sync)
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def eval_pg_container():
    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="session")
def eval_sync_db_url(eval_pg_container):
    url = eval_pg_container.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


@pytest.fixture(scope="session")
def eval_async_db_url(eval_pg_container):
    url = eval_pg_container.get_connection_url()
    url = url.replace("postgresql+psycopg2://", "postgresql://", 1)
    url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
    return url


@pytest.fixture(scope="session", autouse=True)
def eval_run_migrations(eval_sync_db_url):
    """Create cross-service stub tables then run Alembic migrations (all sync)."""
    from alembic import command
    from alembic.config import Config

    conn = _psycopg2_conn(eval_sync_db_url)
    conn.autocommit = True
    cur = conn.cursor()
    # Migration 009 has FK to published_content (owned by parser-service).
    # Create a minimal stub so the FK constraint succeeds in the test DB.
    cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
    cur.execute("CREATE SCHEMA IF NOT EXISTS data_flow")
    cur.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")
    cur.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.published_content (
            id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            content_id   UUID,
            published_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    """)
    cur.close()
    conn.close()

    cfg = Config(str(ALEMBIC_INI))
    cfg.set_main_option("sqlalchemy.url", eval_sync_db_url)
    command.upgrade(cfg, "rec_data_flow@head")


@pytest.fixture(scope="session", autouse=True)
def seed_corpus_and_config(eval_run_migrations, eval_sync_db_url):
    """Seed synthetic corpus + config using psycopg2 (session-scoped, sync).

    raw_content rows are inserted first (posts_features FK). The ORM model for
    raw_content includes T5 columns not in the test schema, so we use raw SQL.
    The embedding is stored as a pgvector literal string.
    """
    embedding_literal = "[" + ",".join(["0.1"] * 312) + "]"
    processed_base = _utcnow() - timedelta(hours=1)

    conn = _psycopg2_conn(eval_sync_db_url)
    conn.autocommit = False
    cur = conn.cursor()

    # Upsert config keys
    for key, value in [
        ("profile", _PROFILE_CONFIG),
        ("onboarding_params", _ONBOARDING_CONFIG),
    ]:
        cur.execute(
            """
            INSERT INTO data_flow.rec_config (key, value)
            VALUES (%s, %s::jsonb)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
            """,
            (key, json.dumps(value)),
        )

    # Pass 1: raw_content (no T5 columns)
    corpus_entries = []
    post_index = 0
    for t1, s1, t2, s2, t3, s3, count in _CORPUS_TOPICS:
        for _ in range(count):
            post_id = uuid.uuid4()
            proc_at = processed_base - timedelta(seconds=post_index * 5)
            corpus_entries.append((post_id, proc_at, t1, s1, t2, s2, t3, s3))
            cur.execute(
                """
                INSERT INTO data_flow.raw_content
                    (id, external_id, source_id, source_type, raw_data,
                     processing_status, is_processed_by_rec, received_at)
                VALUES (%s, %s, %s, %s, %s::jsonb, %s, %s, %s)
                """,
                (
                    str(post_id),
                    str(post_id),
                    _FIXED_SOURCE_ID,
                    "telegram",
                    json.dumps({"title": f"Synthetic post {post_index}", "content": "test"}),
                    "COMPLETED",
                    True,
                    proc_at,
                ),
            )
            post_index += 1

    # Pass 2: posts_features (uses pgvector literal for embedding)
    for post_id, proc_at, t1, s1, t2, s2, t3, s3 in corpus_entries:
        cur.execute(
            """
            INSERT INTO data_flow.posts_features
                (post_id, source_id, source_type, text_length, word_count,
                 reading_time, complexity, is_short_form, is_long_form,
                 topic_1, topic_1_score, topic_2, topic_2_score,
                 topic_3, topic_3_score, sentiment, sentiment_score,
                 entities_persons, entities_organizations, entities_locations,
                 embedding, processed_at)
            VALUES (%s, %s, %s, %s, %s,
                    %s, %s, %s, %s,
                    %s, %s, %s, %s,
                    %s, %s, %s, %s,
                    %s::jsonb, %s::jsonb, %s::jsonb,
                    %s::vector, %s)
            """,
            (
                str(post_id), _FIXED_SOURCE_ID, "telegram", 500, 100,
                0.5, 0.5, True, False,
                t1, s1, t2, s2,
                t3, s3, "NEUTRAL", 0.5,
                "[]", "[]", "[]",
                embedding_literal, proc_at,
            ),
        )

    conn.commit()
    cur.close()
    conn.close()


# ---------------------------------------------------------------------------
# Function-scoped session factory (fresh engine per test — avoids asyncpg
# event-loop isolation issues between session-scoped setup and tests)
# ---------------------------------------------------------------------------


@pytest.fixture
def eval_session_factory(eval_async_db_url):
    """Return an async_sessionmaker bound to the eval container (function-scoped)."""
    engine = create_async_engine(eval_async_db_url, echo=False)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    return factory


# ---------------------------------------------------------------------------
# Repository + use-case helpers
# ---------------------------------------------------------------------------


def _make_update_profile_use_case(factory: Callable) -> UpdateProfileUseCase:
    return UpdateProfileUseCase(
        interaction_repo=PgInteractionRepository(factory),
        profile_repo=PgUserProfileRepository(factory),
        entity_interest_repo=PgEntityInterestRepository(factory),
        config_repo=PgConfigRepository(factory),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        profile_updater=ProfileUpdater(),
        content_repo=PgContentRepository(factory),
    )


def _make_onboard_use_case(factory: Callable) -> OnboardUserUseCase:
    return OnboardUserUseCase(
        profile_repo=PgUserProfileRepository(factory),
        config_repo=PgConfigRepository(factory),
        onboarding_service=OnboardingService(),
        category_repo=None,
        content_repo=None,
        event_publisher=None,
    )


def _make_simulator(factory: Callable) -> BehaviorSimulator:
    return BehaviorSimulator(
        content_repo=PgContentRepository(factory),
        profile_repo=PgUserProfileRepository(factory),
        interactions_repo=PgInteractionRepository(factory),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        update_profile_use_case=_make_update_profile_use_case(factory),
        onboard_use_case=_make_onboard_use_case(factory),
    )


@pytest.fixture
def simulator(eval_session_factory) -> BehaviorSimulator:
    return _make_simulator(eval_session_factory)


@pytest.fixture
def simulator_factory(eval_session_factory):
    """Return a callable that creates a fresh BehaviorSimulator each call."""
    def _factory() -> BehaviorSimulator:
        return _make_simulator(eval_session_factory)
    return _factory


@pytest.fixture
def eval_content_repo(eval_session_factory) -> PgContentRepository:
    return PgContentRepository(eval_session_factory)


# ---------------------------------------------------------------------------
# Phase 3: published_content seeding (non-autouse, requested by feed evaluator tests)
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def seed_published_content(seed_corpus_and_config, eval_sync_db_url):
    """Seed published_content rows + dedup config for feed evaluator tests.

    Three things done here:
    1. Insert one published_content row per corpus post so GenerateFeedUseCase
       finds candidates via its INNER JOIN on data_flow.published_content.
    2. Create minimal stub tables data_flow.articles and data_flow.similarities
       (owned by dedup-system, absent in eval testcontainer DB) so that
       ContentRepository can query them without UndefinedTableError.
    3. Seed rec_config key "dedup" with enable_dedup_clustering=false and
       enable_related_spacing=false so GenerateFeedUseCase skips the dedup
       graph queries entirely (articles/similarities tables are always empty in
       eval environment; dedup is not needed for evaluation correctness).
    """
    import uuid as _uuid

    conn = _psycopg2_conn(eval_sync_db_url)
    conn.autocommit = False
    cur = conn.cursor()

    # 0. Backfill T5/schema columns absent from the Alembic stubs
    cur.execute("""
        ALTER TABLE data_flow.published_content
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ
    """)
    # raw_content was created by migration 007 without T5 column clean_text;
    # RawContentModel maps it so we add it here for the eval environment.
    cur.execute("""
        ALTER TABLE data_flow.raw_content
        ADD COLUMN IF NOT EXISTS clean_text TEXT
    """)

    # 1. Stub dedup-owned tables (dedup-system Alembic migrations are not run here)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.articles (
            id            BIGSERIAL PRIMARY KEY,
            raw_content_id UUID,
            content_hash  TEXT
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS data_flow.similarities (
            id        BIGSERIAL PRIMARY KEY,
            article_a BIGINT,
            article_b BIGINT,
            rel_type  TEXT
        )
    """)

    # 2. Disable dedup clustering to avoid querying empty stub tables
    cur.execute(
        """
        INSERT INTO data_flow.rec_config (key, value)
        VALUES (%s, %s::jsonb)
        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
        """,
        ("dedup", json.dumps({
            "enable_dedup_clustering": False,
            "enable_related_spacing": False,
        })),
    )

    # 3. published_content rows (one per corpus post)
    cur.execute("SELECT post_id, processed_at FROM data_flow.posts_features")
    posts = cur.fetchall()
    for post_id, processed_at in posts:
        pub_at = processed_at or _utcnow()
        cur.execute(
            """
            INSERT INTO data_flow.published_content (id, content_id, published_at)
            VALUES (%s, %s, %s)
            ON CONFLICT (id) DO NOTHING
            """,
            (str(_uuid.uuid4()), str(post_id), pub_at),
        )

    conn.commit()
    cur.close()
    conn.close()
