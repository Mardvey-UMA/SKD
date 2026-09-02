from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING
from uuid import UUID

if TYPE_CHECKING:
    from src.domain.entities.entity_interest import EntityInterest


class EntityInterestRepository(ABC):
    """Abstract repository for user entity interest persistence and retrieval."""

    @abstractmethod
    async def get_top_for_user(self, user_id: UUID, limit: int) -> list[EntityInterest]:
        """Return the top-weighted entity interests for user_id, up to limit."""
        ...

    @abstractmethod
    async def upsert(self, interest: EntityInterest) -> None:
        """Insert or update an entity interest record."""
        ...

    @abstractmethod
    async def decay_all_for_user(self, user_id: UUID, decay_factor: float) -> None:
        """Multiply all entity interest weights for user_id by decay_factor."""
        ...

    @abstractmethod
    async def cleanup_expired(self, user_id: UUID, cleanup_days: int) -> None:
        """Remove entity interest records older than cleanup_days for user_id."""
        ...
