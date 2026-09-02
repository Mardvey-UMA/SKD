from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING
from uuid import UUID

if TYPE_CHECKING:
    from src.domain.entities.user_profile import UserProfile


class UserProfileRepository(ABC):
    """Abstract repository for user profile persistence and retrieval."""

    @abstractmethod
    async def get_by_user_id(self, user_id: UUID) -> UserProfile | None:
        """Return the user profile for user_id, or None if not found."""
        ...

    @abstractmethod
    async def save(self, profile: UserProfile) -> None:
        """Persist (insert or update) a user profile."""
        ...
