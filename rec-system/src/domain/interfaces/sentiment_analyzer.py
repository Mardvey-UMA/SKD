from __future__ import annotations

from abc import ABC, abstractmethod


class SentimentAnalyzer(ABC):
    """Abstract port for classifying the sentiment of text."""

    @abstractmethod
    async def analyze(self, text: str) -> tuple[str, float]:
        """Return a (sentiment_label, confidence_score) tuple for the given text."""
        ...
