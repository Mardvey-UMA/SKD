"""Unit tests for LiveProfileService (Phase A — live profile recomputation).

RED phase: imports non-existent service so tests fail at import.
GREEN phase: implement LiveProfileService to pass all tests.
"""
from __future__ import annotations

import math
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock

import pytest

from src.application.services.live_profile_service import LiveProfileService
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_interaction import UserInteraction
from src.domain.entities.user_profile import UserProfile
from src.domain.services.signal_classifier import SignalClassifier
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector

pytestmark = pytest.mark.unit

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

_DIM = 312


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _make_service(interactions=None, features=None):
    """Return (service, interaction_repo_mock, content_repo_mock)."""
    interaction_repo = AsyncMock()
    content_repo = AsyncMock()
    interaction_repo.get_recent.return_value = interactions or []
    content_repo.get_by_ids.return_value = features or []
    classifier = SignalClassifier(_SIGNAL_WEIGHTS)
    service = LiveProfileService(
        interaction_repo=interaction_repo,
        content_repo=content_repo,
        signal_classifier=classifier,
    )
    return service, interaction_repo, content_repo


def _make_profile(embedding=None):
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=uuid.uuid4(),
        topic_vector=TopicVector({}),
        sentiment_prefs=SentimentPrefs({"POSITIVE": 0.4, "NEGATIVE": 0.3, "NEUTRAL": 0.3}),
        format_prefs=FormatPrefs(avg_length=200.0, avg_complexity=0.5),
        interaction_count=0,
        created_at=now,
        last_updated=now,
        embedding=embedding,
    )


def _like(post_id, user_id=None, created_at=None):
    return UserInteraction(
        id=1,
        event_id=uuid.uuid4(),
        user_id=str(user_id or uuid.uuid4()),
        post_id=post_id,
        event_type="LIKE",
        duration_ms=None,
        scroll_pct=None,
        max_scroll_pct=None,
        created_at=created_at or datetime.now(timezone.utc),
        processed=True,
    )


def _dislike(post_id, user_id=None):
    return UserInteraction(
        id=2,
        event_id=uuid.uuid4(),
        user_id=str(user_id or uuid.uuid4()),
        post_id=post_id,
        event_type="DISLIKE",
        duration_ms=None,
        scroll_pct=None,
        max_scroll_pct=None,
        created_at=datetime.now(timezone.utc),
        processed=True,
    )


def _features(post_id, embedding=None):
    return ContentFeatures(
        post_id=post_id,
        content="test",
        post_date=datetime.now(timezone.utc),
        embedding=embedding,
        source_id=uuid.uuid4(),
        source_type="telegram",
    )


def _unit_vec(dim=_DIM, axis=0):
    """Return a unit vector with 1.0 at the given axis."""
    v = [0.0] * dim
    v[axis] = 1.0
    return v


def _l2_norm(v):
    return math.sqrt(sum(x * x for x in v))


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_zero_interactions_returns_none():
    service, _, _ = _make_service(interactions=[])
    profile = _make_profile()
    result = await service.compute(profile.user_id, profile, 0.6, 15)
    assert result is None


@pytest.mark.asyncio
async def test_only_negative_interactions_returns_none():
    """DISLIKE-only batch yields no positive signals → None."""
    post_id = uuid.uuid4()
    interactions = [_dislike(post_id)]
    features = [_features(post_id, embedding=[0.1] * _DIM)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile()
    result = await service.compute(profile.user_id, profile, 0.6, 15)
    assert result is None


@pytest.mark.asyncio
async def test_positive_interactions_no_embeddings_returns_none():
    """Positive signal but content has no embedding → None."""
    post_id = uuid.uuid4()
    interactions = [_like(post_id)]
    features = [_features(post_id, embedding=None)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile()
    result = await service.compute(profile.user_id, profile, 0.6, 15)
    assert result is None


@pytest.mark.asyncio
async def test_single_like_cold_profile_returns_live_vector():
    """Single LIKE, stored profile has no embedding → returns normalised live vector."""
    post_id = uuid.uuid4()
    emb = [2.0] + [0.0] * (_DIM - 1)  # non-unit, will be normalised
    interactions = [_like(post_id)]
    features = [_features(post_id, embedding=emb)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile(embedding=None)

    result = await service.compute(profile.user_id, profile, 0.6, 15)

    assert result is not None
    assert len(result) == _DIM
    # Should be L2-normalised
    assert abs(_l2_norm(result) - 1.0) < 1e-6
    # First component should be dominant
    assert result[0] > 0.99


@pytest.mark.asyncio
async def test_multiple_positives_weighted_mean():
    """Multiple LIKE events with known embeddings → weighted sum then normalise."""
    pid1, pid2 = uuid.uuid4(), uuid.uuid4()
    # axis-0 and axis-1 unit vectors
    emb1 = _unit_vec(axis=0)
    emb2 = _unit_vec(axis=1)

    interactions = [_like(pid1), _like(pid2)]
    features = [_features(pid1, embedding=emb1), _features(pid2, embedding=emb2)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile(embedding=None)

    result = await service.compute(profile.user_id, profile, 0.6, 15, decay_hours=24.0)

    assert result is not None
    # Both posts have the same weight (same signal strength, same age ≈ 0)
    # so result[0] ≈ result[1] and normalised
    assert abs(result[0] - result[1]) < 0.05
    assert abs(_l2_norm(result) - 1.0) < 1e-6


@pytest.mark.asyncio
async def test_blend_alpha_zero_returns_live_only():
    """blend_alpha=0.0 → result equals live_avg (stored profile ignored)."""
    post_id = uuid.uuid4()
    live_emb = _unit_vec(axis=0)
    stored_emb = _unit_vec(axis=1)  # different direction
    interactions = [_like(post_id)]
    features = [_features(post_id, embedding=live_emb)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile(embedding=stored_emb)

    result = await service.compute(profile.user_id, profile, blend_alpha=0.0, recent_n=15)

    assert result is not None
    # Result should be the live_avg (axis-0 direction)
    assert result[0] > 0.99
    assert abs(result[1]) < 0.01


@pytest.mark.asyncio
async def test_blend_alpha_one_returns_stored_only():
    """blend_alpha=1.0 → result equals normalised stored embedding."""
    post_id = uuid.uuid4()
    live_emb = _unit_vec(axis=0)
    stored_emb = _unit_vec(axis=1)
    interactions = [_like(post_id)]
    features = [_features(post_id, embedding=live_emb)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile(embedding=stored_emb)

    result = await service.compute(profile.user_id, profile, blend_alpha=1.0, recent_n=15)

    assert result is not None
    # Result should be the stored embedding direction (axis-1)
    assert result[1] > 0.99
    assert abs(result[0]) < 0.01


@pytest.mark.asyncio
async def test_age_decay_older_events_have_less_weight():
    """Recent interaction should contribute more to the live vector than an old one."""
    pid_recent = uuid.uuid4()
    pid_old = uuid.uuid4()

    now = datetime.now(timezone.utc)
    recent_ts = now - timedelta(hours=1)
    old_ts = now - timedelta(hours=48)

    # Recent event: axis-0 direction; old event: axis-1 direction
    emb_recent = _unit_vec(axis=0)
    emb_old = _unit_vec(axis=1)

    interactions = [
        _like(pid_recent, created_at=recent_ts),
        _like(pid_old, created_at=old_ts),
    ]
    features = [
        _features(pid_recent, embedding=emb_recent),
        _features(pid_old, embedding=emb_old),
    ]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile(embedding=None)

    result = await service.compute(
        profile.user_id, profile, blend_alpha=0.0, recent_n=15, decay_hours=24.0
    )

    assert result is not None
    # Recent event (axis-0) should outweigh old event (axis-1)
    assert result[0] > result[1], (
        f"Recent (axis-0={result[0]:.3f}) should dominate old (axis-1={result[1]:.3f})"
    )


@pytest.mark.asyncio
async def test_zero_stored_embedding_treated_as_cold_start():
    """All-zero stored embedding → treated as cold start → return live vector only."""
    post_id = uuid.uuid4()
    live_emb = _unit_vec(axis=0)
    zero_stored = [0.0] * _DIM
    interactions = [_like(post_id)]
    features = [_features(post_id, embedding=live_emb)]
    service, _, _ = _make_service(interactions=interactions, features=features)
    profile = _make_profile(embedding=zero_stored)

    result = await service.compute(profile.user_id, profile, blend_alpha=0.6, recent_n=15)

    assert result is not None
    # Should return live only because stored is all-zero
    assert result[0] > 0.99
