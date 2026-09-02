"""RelevanceReranker — port for cross-encoder reranking."""
from __future__ import annotations

from abc import ABC, abstractmethod
from uuid import UUID
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from src.domain.entities.content_features import ContentFeatures


class RelevanceReranker(ABC):
    """Port: rerank content candidates against a user context string.

    Returns a list of (post_id, rerank_score) tuples sorted descending by
    score, with length <= top_k.
    """

    @abstractmethod
    async def rerank(
        self,
        user_context: str,
        candidates: list["ContentFeatures"],
        top_k: int,
    ) -> list[tuple[UUID, float]]:
        """Compute cross-encoder scores and return ranked (post_id, score) pairs.

        Args:
            user_context: Natural-language query representing user interests.
            candidates: Content features to score.
            top_k: Maximum number of results to return.

        Returns:
            List of (post_id, score) sorted by score descending, len <= top_k.
        """
