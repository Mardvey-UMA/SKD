from __future__ import annotations

from abc import ABC, abstractmethod


class EntityExtractor(ABC):
    """Abstract port for extracting named entities from text."""

    @abstractmethod
    async def extract(self, text: str) -> dict[str, list[str]]:
        """Return a dict with 'persons', 'organizations', and 'locations' lists."""
        ...
