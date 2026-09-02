from __future__ import annotations

from typing import Dict


class FeedMeta:
    """Immutable value object for feed generation metadata matching API contract."""

    def __init__(
        self,
        total: int,
        candidates_scored: int,
        profile_events_processed: int,
        generation_time_ms: int,
    ) -> None:
        self._total = total
        self._candidates_scored = candidates_scored
        self._profile_events_processed = profile_events_processed
        self._generation_time_ms = generation_time_ms

    @property
    def total(self) -> int:
        return self._total

    @property
    def candidates_scored(self) -> int:
        return self._candidates_scored

    @property
    def profile_events_processed(self) -> int:
        return self._profile_events_processed

    @property
    def generation_time_ms(self) -> int:
        return self._generation_time_ms

    def to_dict(self) -> Dict[str, int]:
        return {
            "total": self._total,
            "candidates_scored": self._candidates_scored,
            "profile_events_processed": self._profile_events_processed,
            "generation_time_ms": self._generation_time_ms,
        }
