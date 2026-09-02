from __future__ import annotations

from abc import ABC, abstractmethod


class TopicClassifier(ABC):
    """Abstract port for classifying text into topics with confidence scores."""

    @abstractmethod
    async def classify(self, text: str) -> list[tuple[str, float]]:
        """Return the top-3 (topic, score) tuples for the given text."""
        ...
