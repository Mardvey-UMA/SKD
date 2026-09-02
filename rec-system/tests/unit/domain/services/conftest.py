"""Shared fixtures for domain/services unit tests."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from uuid import uuid4

from src.domain.entities.user_interaction import UserInteraction
from src.domain.services.signal_classifier import SignalClassifier

DEFAULT_WEIGHTS = {
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


def _make_interaction(
    event_type: str,
    post_id: int = 1,
    user_id: str = "user1",
    duration_ms: int | None = None,
    max_scroll_pct: float | None = None,
    created_at: datetime | None = None,
) -> UserInteraction:
    return UserInteraction(
        id=1,
        event_id=uuid4(),
        user_id=user_id,
        post_id=post_id,
        event_type=event_type,
        duration_ms=duration_ms,
        scroll_pct=max_scroll_pct,
        max_scroll_pct=max_scroll_pct,
        created_at=created_at or datetime(2026, 1, 1, tzinfo=timezone.utc),
        processed=False,
    )


class _ClassifierProxy:
    """Thin test proxy: wraps SignalClassifier.classify_batch for single-event assertions."""

    def __init__(self, inner: SignalClassifier) -> None:
        self._inner = inner

    def classify(self, inter: UserInteraction) -> float | None:
        signals = self._inner.classify_batch([inter])
        return signals[0].weight if signals else None


@pytest.fixture
def classifier() -> _ClassifierProxy:
    return _ClassifierProxy(SignalClassifier(signal_weights=DEFAULT_WEIGHTS))


@pytest.fixture
def valid_interaction_for():
    """Factory: returns a valid UserInteraction for the given event_type."""
    _defaults: dict[str, dict] = {
        "IMPRESSION": {"duration_ms": 3000},
        "LIKE": {},
        "DISLIKE": {},
        "BOOKMARK": {},
        "CLOSE": {"duration_ms": 5000, "max_scroll_pct": 0.3},
        "OPEN": {},
    }

    def _factory(event_type: str) -> UserInteraction:
        kwargs = _defaults.get(event_type, {})
        return _make_interaction(event_type, **kwargs)

    return _factory


@pytest.fixture
def close_interaction_variants() -> dict[str, UserInteraction]:
    """One UserInteraction per CLOSE branch: fast, full, half, other."""
    return {
        "close_fast": _make_interaction("CLOSE", duration_ms=1000, max_scroll_pct=0.0),
        "close_full": _make_interaction("CLOSE", duration_ms=20000, max_scroll_pct=0.9),
        "close_half": _make_interaction("CLOSE", duration_ms=12000, max_scroll_pct=0.6),
        "close_other": _make_interaction("CLOSE", duration_ms=5000, max_scroll_pct=0.3),
    }
