from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING
from uuid import UUID

if TYPE_CHECKING:
    from src.domain.entities.user_interaction import UserInteraction


class InteractionRepository(ABC):
    """Abstract repository for user interaction records."""

    @abstractmethod
    async def get_unprocessed_for_user(self, user_id: UUID) -> list[UserInteraction]:
        """Return all unprocessed interactions for a specific user."""
        ...

    @abstractmethod
    async def get_unprocessed(self, limit: int) -> list[UserInteraction]:
        """Return up to limit unprocessed interactions across all users."""
        ...

    @abstractmethod
    async def mark_processed(self, interaction_ids: list[int]) -> None:
        """Mark the given interaction IDs as processed."""
        ...

    @abstractmethod
    async def save_batch(self, interactions: list[UserInteraction]) -> None:
        """Insert interactions with ON CONFLICT (event_id) DO NOTHING."""
        ...

    @abstractmethod
    async def get_recent(self, user_id: UUID, limit: int) -> list[UserInteraction]:
        """Return up to limit most-recent interactions for user, ordered by created_at DESC.

        Includes all event types and all processed states.
        Used by LiveProfileService for on-request embedding blending.
        """
        ...
