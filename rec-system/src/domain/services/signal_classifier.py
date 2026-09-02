"""SignalClassifier domain service."""
from __future__ import annotations

from datetime import timedelta
from typing import Dict, Any, List

from src.domain.entities.user_interaction import UserInteraction
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.event_type import EventType


class SignalClassifier:
    """Converts a batch of UserInteraction entities into Signal value objects.

    Implements the signal classification table from design/03-user-signals.md.
    CLOSE conditions are checked in strict order (fast first).
    OPEN events get weight 0.0 (orphan) unless a paired CLOSE exists within timeout.
    """

    def __init__(self, signal_weights: Dict[str, Any]) -> None:
        self._weights = signal_weights

    def classify_batch(self, interactions: List[UserInteraction]) -> List[Signal]:
        """Classify a list of UserInteraction objects into Signal value objects."""
        # Build a lookup: (user_id, post_id) -> list of CLOSE interactions
        close_by_key: Dict[tuple, List[UserInteraction]] = {}
        for inter in interactions:
            if inter.event_type == "CLOSE":
                key = (inter.user_id, inter.post_id)
                close_by_key.setdefault(key, []).append(inter)

        signals = []
        for inter in interactions:
            signal = self._classify_single(inter, close_by_key)
            if signal is not None:
                signals.append(signal)
        return signals

    def _classify_single(
        self,
        inter: UserInteraction,
        close_by_key: Dict[tuple, List[UserInteraction]],
    ) -> Signal | None:
        event_type = inter.event_type

        if event_type == "IMPRESSION":
            return self._classify_impression(inter)
        elif event_type == "LIKE":
            weight = self._weights["like"]["weight"]
            return Signal(post_id=inter.post_id, user_id=inter.user_id, weight=weight, event_type=EventType.LIKE)
        elif event_type == "DISLIKE":
            weight = self._weights["dislike"]["weight"]
            return Signal(post_id=inter.post_id, user_id=inter.user_id, weight=weight, event_type=EventType.DISLIKE)
        elif event_type == "BOOKMARK":
            weight = self._weights["bookmark"]["weight"]
            return Signal(post_id=inter.post_id, user_id=inter.user_id, weight=weight, event_type=EventType.BOOKMARK)
        elif event_type == "CLOSE":
            return self._classify_close(inter)
        elif event_type == "OPEN":
            return self._classify_open(inter, close_by_key)
        return None

    def _classify_impression(self, inter: UserInteraction) -> Signal:
        threshold = self._weights["impression_read"]["threshold_duration_ms"]
        if inter.duration_ms is not None and inter.duration_ms >= threshold:
            weight = self._weights["impression_read"]["weight"]
        else:
            weight = self._weights["impression_skip"]["weight"]
        return Signal(
            post_id=inter.post_id,
            user_id=inter.user_id,
            weight=weight,
            event_type=EventType.IMPRESSION,
        )

    def _classify_close(self, inter: UserInteraction) -> Signal:
        duration = inter.duration_ms or 0
        scroll = inter.max_scroll_pct or 0.0

        # Check order: fast bounce (first!), full read, half read, other
        fast_threshold = self._weights["close_fast"]["threshold_duration_ms"]
        if duration < fast_threshold:
            weight = self._weights["close_fast"]["weight"]
        else:
            full_scroll = self._weights["close_full"]["threshold_scroll_pct"]
            full_duration = self._weights["close_full"]["threshold_duration_ms"]
            half_scroll = self._weights["close_half"]["threshold_scroll_pct"]
            half_duration = self._weights["close_half"]["threshold_duration_ms"]

            if scroll >= full_scroll and duration >= full_duration:
                weight = self._weights["close_full"]["weight"]
            elif scroll >= half_scroll and duration >= half_duration:
                weight = self._weights["close_half"]["weight"]
            else:
                weight = self._weights["close_other"]["weight"]

        return Signal(
            post_id=inter.post_id,
            user_id=inter.user_id,
            weight=weight,
            event_type=EventType.CLOSE,
        )

    def _classify_open(
        self,
        inter: UserInteraction,
        close_by_key: Dict[tuple, List[UserInteraction]],
    ) -> Signal:
        orphan_timeout_minutes = self._weights.get("orphan_timeout_minutes", 5)
        key = (inter.user_id, inter.post_id)
        closes = close_by_key.get(key, [])

        # Look for a CLOSE within the orphan timeout
        timeout_delta = timedelta(minutes=orphan_timeout_minutes)
        has_paired_close = any(
            abs((c.created_at - inter.created_at).total_seconds()) <= timeout_delta.total_seconds()
            for c in closes
        )

        if has_paired_close:
            weight = self._weights["open"]["weight"]
        else:
            weight = 0.0

        return Signal(
            post_id=inter.post_id,
            user_id=inter.user_id,
            weight=weight,
            event_type=EventType.OPEN,
        )
