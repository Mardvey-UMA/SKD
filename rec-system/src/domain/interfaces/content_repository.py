from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING
from uuid import UUID

if TYPE_CHECKING:
    from src.domain.entities.content_features import ContentFeatures


class ContentRepository(ABC):
    """Abstract repository for content features persistence and retrieval."""

    @abstractmethod
    async def get_unprocessed(self, batch_size: int) -> list[ContentFeatures]:
        """Return up to batch_size unprocessed content items."""
        ...

    @abstractmethod
    async def save_features(self, features: ContentFeatures) -> None:
        """Persist extracted NLP features for a content item."""
        ...

    @abstractmethod
    async def get_raw_by_id(self, post_id: UUID) -> "ContentFeatures | None":
        """Return a single raw_content row mapped to ContentFeatures, or None if not found."""
        ...

    @abstractmethod
    async def get_candidates_by_freshness(
        self,
        max_age_hours: int,
        limit: int,
        exclude_source_ids: list[UUID] | None = None,
    ) -> list[ContentFeatures]:
        """Return candidate content items newer than max_age_hours, up to limit.

        Phase 1 user-sources: ``exclude_source_ids`` filters out posts whose
        source_id matches any in the list (pass None or [] to disable).
        """
        ...

    @abstractmethod
    async def get_candidates_by_embedding(
        self,
        embedding: list[float],
        limit: int,
        exclude_source_ids: list[UUID] | None = None,
    ) -> list[ContentFeatures]:
        """Return candidate content items closest to the given embedding, up to limit.

        Phase 1 user-sources: ``exclude_source_ids`` filters out posts whose
        source_id matches any in the list (pass None or [] to disable).
        """
        ...

    @abstractmethod
    async def get_candidates_by_sources(
        self,
        include_source_ids: list[UUID],
        exclude_source_ids: list[UUID] | None,
        max_age_hours: int,
        limit: int,
    ) -> list[ContentFeatures]:
        """Return candidates restricted to the given source_ids, ordered by freshness.

        Phase 1 user-sources narrow path. Empty ``include_source_ids`` returns [].
        """
        ...

    @abstractmethod
    async def get_by_ids(self, post_ids: list[UUID]) -> list[ContentFeatures]:
        """Return content features for the given post_ids."""
        ...

    @abstractmethod
    async def get_dedup_clusters(self, post_ids: list[UUID]) -> dict[UUID, int]:
        """Return post_id -> cluster_id mapping using EXACT/DUPLICATE similarity edges."""
        ...

    @abstractmethod
    async def get_related_pairs(self, post_ids: list[UUID]) -> dict[UUID, set[UUID]]:
        """Return symmetric adjacency map for RELATED posts among candidates."""
        ...

    @abstractmethod
    async def get_published_ids(self, content_ids: list[UUID]) -> dict[UUID, UUID]:
        """Return raw_content.id -> published_content.id mapping."""
        ...

    @abstractmethod
    async def get_recent_published_ids(self, limit: int) -> list[UUID]:
        """Return the most recently published content IDs, ordered by published_at DESC."""
        ...

    @abstractmethod
    async def get_corpus_sample(self, limit: int = 1000) -> list[ContentFeatures]:
        """Return up to limit posts from posts_features for eval corpus sampling.

        Does NOT require a published_content mapping — returns directly from
        posts_features ordered by processed_at DESC. Intended for the evaluation
        harness only; not used in production recommendation paths.
        """
        ...

    @abstractmethod
    async def get_source_post_counts(self) -> dict[UUID, int]:
        """Return source_id -> post count map from posts_features. Eval-only."""
        ...
