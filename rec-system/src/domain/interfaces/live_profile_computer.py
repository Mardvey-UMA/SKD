from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING
from uuid import UUID

if TYPE_CHECKING:
    from src.domain.entities.user_profile import UserProfile


class LiveProfileComputer(ABC):
    @abstractmethod
    async def compute(
        self,
        user_id: UUID,
        stored_profile: "UserProfile",
        blend_alpha: float,
        recent_n: int,
        decay_hours: float = 24.0,
    ) -> list[float] | None:
        """Blend stored profile embedding with live-computed vector from recent interactions.

        Returns the blended/normalized embedding as a list[float], or None if there
        are no positive interactions or no embeddings available for them.
        """
        ...
