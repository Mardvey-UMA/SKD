from __future__ import annotations

from abc import ABC, abstractmethod


class ContentEncoder(ABC):
    """Abstract port for generating dense vector embeddings from text."""

    @abstractmethod
    async def encode(self, text: str) -> list[float]:
        """Encode a single text string into a float embedding vector."""
        ...

    @abstractmethod
    async def encode_batch(self, texts: list[str]) -> list[list[float]]:
        """Encode a batch of text strings into a list of float embedding vectors."""
        ...
