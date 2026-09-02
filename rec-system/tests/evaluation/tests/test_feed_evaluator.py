"""Tests for FeedEvaluator (Phase 3 — RED phase).

Unit tests (no DB):
  - test_feed_result_json_roundtrip
  - test_feed_item_snippet_truncation
  - test_feed_item_snippet_no_truncation
  - test_feed_item_age_hours_uses_processed_at_not_published_at

Integration tests (@pytest.mark.integration, testcontainers):
  - test_feed_evaluator_smoke        — tech-geek, count=5
  - test_feed_evaluator_cold_start   — cold-start-empty persona, items >= 1

Fixtures are in tests/evaluation/tests/conftest.py.
`seed_published_content` fixture (added in GREEN phase) inserts published_content rows
so GenerateFeedUseCase can find candidates via its INNER JOIN.
"""
from __future__ import annotations

import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable
from unittest.mock import AsyncMock, MagicMock

import pytest

from tests.evaluation.feed_evaluator import FeedEvaluator, FeedItem, FeedResult
from tests.evaluation.feed_evaluator import _make_snippet

_PERSONA_DIR = Path(__file__).parent.parent / "personas"


# ---------------------------------------------------------------------------
# Unit tests — no DB
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_feed_result_json_roundtrip():
    """FeedResult.to_json() → from_json() must roundtrip all fields losslessly."""
    item = FeedItem(
        position=1,
        post_id=uuid.UUID("11111111-1111-1111-1111-111111111111"),
        raw_content_id=uuid.UUID("22222222-2222-2222-2222-222222222222"),
        title="Test title",
        content_snippet="Short snippet",
        topics=[("технологии", 0.85), ("наука", 0.10)],
        sentiment=("POSITIVE", 0.75),
        entities={"PERSON": ["Иванов"], "ORG": ["Google"]},
        embedding=[0.1] * 312,
        text_metrics={"word_count": 100, "reading_time": 0.5, "complexity": 0.3},
        source_id=uuid.UUID("33333333-3333-3333-3333-333333333333"),
        age_hours=2.5,
        scoring_breakdown=None,
    )
    result = FeedResult(
        user_id=uuid.UUID("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        persona_id="tech-geek",
        version="baseline",
        seed=42,
        run_number=1,
        generated_at=datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc),
        items=[item, item, item],
        request_latency_ms=123.4,
        count=3,
        metadata={"config_hash": "abc123"},
    )

    s = result.to_json()
    assert isinstance(s, str)

    restored = FeedResult.from_json(s)

    assert restored.user_id == result.user_id
    assert restored.persona_id == result.persona_id
    assert restored.version == result.version
    assert restored.seed == result.seed
    assert restored.run_number == result.run_number
    assert restored.generated_at == result.generated_at
    assert restored.count == result.count
    assert restored.request_latency_ms == result.request_latency_ms
    assert restored.metadata == result.metadata
    assert len(restored.items) == 3

    r_item = restored.items[0]
    assert r_item.post_id == item.post_id
    assert r_item.raw_content_id == item.raw_content_id
    assert r_item.title == item.title
    assert r_item.content_snippet == item.content_snippet
    assert r_item.topics == item.topics
    assert r_item.sentiment == item.sentiment
    assert r_item.entities == item.entities
    assert r_item.embedding == item.embedding
    assert r_item.text_metrics == item.text_metrics
    assert r_item.source_id == item.source_id
    assert r_item.age_hours == item.age_hours
    assert r_item.scoring_breakdown is None


@pytest.mark.unit
def test_feed_item_snippet_truncation():
    """Content > 300 chars must be cut at 300 and appended with '…'."""
    long_text = "А" * 500
    snippet = _make_snippet(long_text)
    # "…" is 1 Unicode code point so len() == 1
    assert snippet.endswith("…")
    assert snippet[:-1] == "А" * 300
    assert len(snippet) == 301


@pytest.mark.unit
def test_feed_item_snippet_no_truncation():
    """Content <= 300 chars must be returned unchanged."""
    short = "Короткий текст"
    assert _make_snippet(short) == short


@pytest.mark.unit
def test_feed_item_snippet_empty():
    """Empty content must return empty string, not raise."""
    assert _make_snippet("") == ""


@pytest.mark.unit
@pytest.mark.asyncio
async def test_feed_item_age_hours_uses_processed_at_not_published_at():
    """FeedItem.age_hours must be derived from posts_features.processed_at,
    not from published_content.published_at.

    Regression test for P2 investigation finding: published_content.published_at
    reflects the original Telegram post date (can be years old), while
    posts_features.processed_at reflects when the content entered the rec
    pipeline (the relevant freshness definition for retrieval alignment).

    Setup:
      - published_content.published_at = 2016-01-01 (≈ 88,000+ hours ago)
      - posts_features.processed_at   = now - 1 hour (≈ 1h)

    Expected: FeedItem.age_hours ≈ 1h (from processed_at)
    Wrong behaviour (before fix): FeedItem.age_hours ≈ 88,000h (from published_at)
    """
    from src.application.dto.generate_feed import GenerateFeedResponse
    from src.domain.entities.content_features import ContentFeatures
    from tests.evaluation.feed_evaluator import FeedEvaluator

    pub_id = uuid.uuid4()
    raw_id = uuid.uuid4()
    user_id = uuid.uuid4()

    now = datetime.now(timezone.utc)
    old_published_at = datetime(2016, 1, 1, tzinfo=timezone.utc)  # ≈ 88,000h ago
    recent_processed_at = now - timedelta(hours=1)               # ≈ 1h ago

    # Mock generate_feed_use_case
    mock_use_case = AsyncMock()
    mock_use_case.execute.return_value = GenerateFeedResponse(
        feed=[{"post_id": str(pub_id), "score": 0.9}],
        meta={"total": 1, "candidates_scored": 1, "profile_events_processed": 0,
              "generation_time_ms": 10, "unmapped_posts": 0},
    )

    # Mock content_repo.get_by_ids → ContentFeatures with recent processed_at
    features = ContentFeatures(
        post_id=raw_id,
        content="test content",
        post_date=recent_processed_at,
        source_id=uuid.uuid4(),
        source_type="telegram",
        topic_1="технологии",
        topic_1_score=0.85,
        embedding=[0.1] * 312,
        processed_at=recent_processed_at,
    )
    mock_content_repo = AsyncMock()
    mock_content_repo.get_by_ids.return_value = [features]

    # Mock session_factory to return published_content with OLD published_at
    # and empty raw_content text.
    # Each async with session_factory() as session → different query results.
    pub_row = MagicMock()
    pub_row.id = pub_id
    pub_row.content_id = raw_id
    pub_row.published_at = old_published_at  # stale original Telegram date

    raw_row = MagicMock()
    raw_row.id = raw_id
    raw_row.raw_data = {}
    raw_row.clean_text = ""

    # _fetch_published_data: uses result.scalars().all() → list of ORM rows
    pub_result = MagicMock()
    pub_result.scalars.return_value.all.return_value = [pub_row]

    # _fetch_raw_content_data: uses result.all() → list of Row objects (attr access)
    raw_result_row = MagicMock()
    raw_result_row.id = raw_id
    raw_result_row.raw_data = {}
    raw_result_row.clean_text = ""
    raw_result = MagicMock()
    raw_result.all.return_value = [raw_result_row]

    mock_session = AsyncMock()
    mock_session.execute.side_effect = [pub_result, raw_result]

    @asynccontextmanager
    async def mock_session_factory():
        yield mock_session

    evaluator = FeedEvaluator(
        generate_feed_use_case=mock_use_case,
        content_repo=mock_content_repo,
        session_factory=mock_session_factory,
    )

    user_state = MagicMock()
    user_state.user_id = user_id
    user_state.persona_id = "test-persona"
    user_state.seed = 42

    result = await evaluator.run(user_state, count=1)

    assert len(result.items) == 1
    item = result.items[0]

    # age_hours must be close to 1h (processed_at), NOT 88,000h (published_at)
    assert item.age_hours < 5, (
        f"age_hours={item.age_hours:.1f}h — expected ≈1h (from processed_at). "
        f"If ≈88,000h, FeedEvaluator is incorrectly using published_content.published_at."
    )


# ---------------------------------------------------------------------------
# Integration tests — testcontainers DB
# ---------------------------------------------------------------------------


@pytest.mark.integration
async def test_feed_evaluator_smoke(
    eval_session_factory,
    simulator,
    seed_published_content,
):
    """tech-geek persona → FeedResult with items >= 1, each with embedding and valid age."""
    from tests.evaluation.persona_loader import PersonaLoader

    persona = PersonaLoader(_PERSONA_DIR).load("tech-geek")
    user_id = uuid.uuid4()
    state = await simulator.simulate(persona, user_id, seed=77)

    evaluator = _make_evaluator(eval_session_factory)
    result = await evaluator.run(state, count=5, version="baseline", run_number=1)

    assert isinstance(result, FeedResult)
    assert result.user_id == user_id
    assert result.persona_id == "tech-geek"
    assert result.version == "baseline"
    assert result.run_number == 1
    assert result.request_latency_ms > 0
    assert result.count == len(result.items)
    assert len(result.items) >= 1

    for item in result.items:
        assert item.position >= 1
        assert len(item.embedding) > 0, f"item {item.position} has no embedding"
        assert item.age_hours >= 0.0
        assert isinstance(item.post_id, uuid.UUID)
        assert isinstance(item.raw_content_id, uuid.UUID)


@pytest.mark.integration
async def test_feed_evaluator_cold_start(
    eval_session_factory,
    simulator,
    seed_published_content,
):
    """Cold-start user (no interactions) → feed still produced from freshness alone."""
    from tests.evaluation.persona_loader import PersonaLoader

    persona = PersonaLoader(_PERSONA_DIR).load("cold-start-empty")
    user_id = uuid.uuid4()
    state = await simulator.simulate(persona, user_id, seed=13)

    assert state.interactions == [], "cold-start-empty must have 0 interactions"

    evaluator = _make_evaluator(eval_session_factory)
    result = await evaluator.run(state, count=5)

    assert isinstance(result, FeedResult)
    assert len(result.items) >= 1
    assert result.count == len(result.items)
    for item in result.items:
        assert item.age_hours >= 0.0


# ---------------------------------------------------------------------------
# Helper — wire FeedEvaluator with all live dependencies
# ---------------------------------------------------------------------------


def _make_evaluator(session_factory: Callable) -> FeedEvaluator:
    from src.application.use_cases.generate_feed import GenerateFeedUseCase
    from src.domain.services.diversity_filter import DiversityFilter
    from src.domain.services.narrow_scorer import NarrowScorer
    from src.domain.services.profile_updater import ProfileUpdater
    from src.domain.services.scorer import Scorer
    from src.domain.services.signal_classifier import SignalClassifier
    from src.infrastructure.persistence.pg_blocked_sources_repository import PgBlockedSourcesRepository
    from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
    from src.infrastructure.persistence.pg_content_repository import PgContentRepository
    from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository
    from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository
    from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

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

    use_case = GenerateFeedUseCase(
        profile_repo=PgUserProfileRepository(session_factory),
        content_repo=PgContentRepository(session_factory),
        entity_interest_repo=PgEntityInterestRepository(session_factory),
        interaction_repo=PgInteractionRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        scorer=Scorer(),
        diversity_filter=DiversityFilter(),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        profile_updater=ProfileUpdater(),
        blocked_sources_repo=PgBlockedSourcesRepository(session_factory),
        narrow_scorer=NarrowScorer(),
    )
    content_repo = PgContentRepository(session_factory)
    return FeedEvaluator(
        generate_feed_use_case=use_case,
        content_repo=content_repo,
        session_factory=session_factory,
    )
