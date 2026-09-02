from __future__ import annotations

from abc import ABC, abstractmethod
from uuid import UUID


class RecommendationHistoryRepository(ABC):
    """Abstract port for recommendation history CRUD."""

    @abstractmethod
    async def get_recommended_ids(self, user_id: UUID) -> set[UUID]:
        """Return the set of content_ids already recommended to *user_id*."""
        ...

    @abstractmethod
    async def save_recommendations(
        self,
        user_id: UUID,
        content_ids: list[UUID],
    ) -> None:
        """Persist *content_ids* as recommended to *user_id* (idempotent, INSERT OR IGNORE)."""
        ...

    @abstractmethod
    async def delete_for_user(self, user_id: UUID) -> int:
        """Delete ALL history entries for *user_id*.  Returns the number of rows deleted."""
        ...

    @abstractmethod
    async def delete_older_than(self, days: int) -> int:
        """Delete history entries older than *days* days.  Returns the number of rows deleted."""
        ...
