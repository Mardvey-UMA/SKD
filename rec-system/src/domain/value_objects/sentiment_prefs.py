from __future__ import annotations

from typing import Dict


class SentimentPrefs:
    """Immutable value object for 3-class sentiment distribution (L1-normalized)."""

    SENTIMENTS = ["POSITIVE", "NEGATIVE", "NEUTRAL"]

    def __init__(self, weights: Dict[str, float]) -> None:
        # Clamp negatives, then L1-normalize
        clamped = {s: max(0.0, weights.get(s, 0.0)) for s in self.SENTIMENTS}
        total = sum(clamped.values())
        if total == 0.0:
            normalized = {s: 1.0 / 3 for s in self.SENTIMENTS}
        else:
            normalized = {s: v / total for s, v in clamped.items()}
        self._weights: Dict[str, float] = normalized

    @property
    def weights(self) -> Dict[str, float]:
        return dict(self._weights)

    def get(self, sentiment: str) -> float:
        return self._weights.get(sentiment, 0.0)

    @classmethod
    def create_uniform(cls) -> "SentimentPrefs":
        return cls({s: 1.0 for s in cls.SENTIMENTS})

    def apply_update(self, sentiment: str, lr: float, weight: float) -> "SentimentPrefs":
        """Apply lr * weight * 0.5 to sentiment class, return new normalized instance."""
        new_weights = dict(self._weights)
        if sentiment in new_weights:
            new_weights[sentiment] += lr * weight * 0.5
        return SentimentPrefs(new_weights)

    def to_dict(self) -> Dict[str, float]:
        return dict(self._weights)
