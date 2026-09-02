"""Tests for SignalClassifier domain service."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from uuid import uuid4

from src.domain.services.signal_classifier import SignalClassifier
from src.domain.entities.user_interaction import UserInteraction
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.event_type import EventType


def make_interaction(
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


class TestSignalClassifier:
    @pytest.fixture
    def sut(self) -> SignalClassifier:
        return SignalClassifier(signal_weights=DEFAULT_WEIGHTS)

    def test_impression_read(self, sut: SignalClassifier) -> None:
        """IMPRESSION with duration_ms >= 2000 -> weight +0.15"""
        interaction = make_interaction("IMPRESSION", duration_ms=3500)
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(0.15)
        assert signals[0].event_type == EventType.IMPRESSION

    def test_impression_skip(self, sut: SignalClassifier) -> None:
        """IMPRESSION with duration_ms < 2000 -> weight -0.05"""
        interaction = make_interaction("IMPRESSION", duration_ms=800)
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(-0.05)

    def test_close_fast_bounce(self, sut: SignalClassifier) -> None:
        """CLOSE with duration_ms < 3000 -> weight -0.2 (checked first)"""
        interaction = make_interaction("CLOSE", duration_ms=1000, max_scroll_pct=0.9)
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(-0.2)

    def test_close_full_read(self, sut: SignalClassifier) -> None:
        """CLOSE with max_scroll_pct >= 0.85 AND duration_ms >= 15000 -> +0.5"""
        interaction = make_interaction("CLOSE", duration_ms=20000, max_scroll_pct=0.9)
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(0.5)

    def test_close_half_read(self, sut: SignalClassifier) -> None:
        """CLOSE with max_scroll_pct >= 0.5 AND duration_ms >= 10000 -> +0.4"""
        interaction = make_interaction("CLOSE", duration_ms=12000, max_scroll_pct=0.6)
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(0.4)

    def test_close_other(self, sut: SignalClassifier) -> None:
        """CLOSE fallback -> +0.1"""
        interaction = make_interaction("CLOSE", duration_ms=5000, max_scroll_pct=0.3)
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(0.1)

    def test_close_full_checked_before_half(self, sut: SignalClassifier) -> None:
        """post with scroll=0.9, duration=20000 gets +0.5 not +0.4"""
        interaction = make_interaction("CLOSE", duration_ms=20000, max_scroll_pct=0.9)
        signals = sut.classify_batch([interaction])
        assert signals[0].weight == pytest.approx(0.5)

    def test_like(self, sut: SignalClassifier) -> None:
        """LIKE -> weight +0.6"""
        interaction = make_interaction("LIKE")
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(0.6)
        assert signals[0].event_type == EventType.LIKE

    def test_dislike(self, sut: SignalClassifier) -> None:
        """DISLIKE -> weight -0.7"""
        interaction = make_interaction("DISLIKE")
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(-0.7)

    def test_bookmark(self, sut: SignalClassifier) -> None:
        """BOOKMARK -> weight +0.8"""
        interaction = make_interaction("BOOKMARK")
        signals = sut.classify_batch([interaction])
        assert len(signals) == 1
        assert signals[0].weight == pytest.approx(0.8)

    def test_open_with_paired_close(self, sut: SignalClassifier) -> None:
        """OPEN gets +0.1 when CLOSE exists within timeout"""
        t = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
        from datetime import timedelta
        open_int = UserInteraction(
            id=1, event_id=uuid4(), user_id="user1", post_id=5,
            event_type="OPEN", duration_ms=None, scroll_pct=None, max_scroll_pct=None,
            created_at=t, processed=False,
        )
        close_int = UserInteraction(
            id=2, event_id=uuid4(), user_id="user1", post_id=5,
            event_type="CLOSE", duration_ms=30000, scroll_pct=0.9, max_scroll_pct=0.9,
            created_at=t + timedelta(minutes=2), processed=False,
        )
        signals = sut.classify_batch([open_int, close_int])
        open_signals = [s for s in signals if s.event_type == EventType.OPEN]
        assert len(open_signals) == 1
        assert open_signals[0].weight == pytest.approx(0.1)

    def test_open_orphan(self, sut: SignalClassifier) -> None:
        """OPEN without CLOSE after timeout -> weight 0.0"""
        interaction = make_interaction("OPEN", post_id=99)
        signals = sut.classify_batch([interaction])
        open_signals = [s for s in signals if s.event_type == EventType.OPEN]
        assert len(open_signals) == 1
        assert open_signals[0].weight == pytest.approx(0.0)

    def test_batch_classification(self, sut: SignalClassifier) -> None:
        """classify_batch returns list of Signal objects"""
        interactions = [
            make_interaction("LIKE", post_id=1),
            make_interaction("DISLIKE", post_id=2),
            make_interaction("BOOKMARK", post_id=3),
        ]
        signals = sut.classify_batch(interactions)
        assert len(signals) == 3
        assert all(isinstance(s, Signal) for s in signals)
        weights = {s.post_id: s.weight for s in signals}
        assert weights[1] == pytest.approx(0.6)
        assert weights[2] == pytest.approx(-0.7)
        assert weights[3] == pytest.approx(0.8)

    def test_configurable_thresholds(self) -> None:
        """signal_weights config overrides defaults"""
        custom_weights = dict(DEFAULT_WEIGHTS)
        custom_weights = {**DEFAULT_WEIGHTS}
        custom_weights["like"] = {"weight": 0.9}
        sut = SignalClassifier(signal_weights=custom_weights)
        interaction = make_interaction("LIKE")
        signals = sut.classify_batch([interaction])
        assert signals[0].weight == pytest.approx(0.9)
