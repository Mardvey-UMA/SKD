"""BlockedSourcesRepository — per-user source blacklist port (Phase 1 user-sources)."""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING
from uuid import UUID

if TYPE_CHECKING:
    from src.domain.value_objects.blocked_source import BlockedSource


class BlockedSourcesRepository(ABC):
    """Abstract port for the per-user source blacklist.

    All mutations are idempotent (add on existing row is a no-op; remove on
    missing row is a no-op) so callers can safely retry without worrying
    about duplicate-key errors.
    """

    @abstractmethod
    async def get_source_ids_for_user(self, user_id: UUID) -> set[UUID]:
        """Return the set of source_ids currently blocked by *user_id*."""
        ...

    @abstractmethod
    async def add(self, user_id: UUID, source_id: UUID) -> None:
        """Idempotent: insert (user_id, source_id); existing row is preserved."""
        ...

    @abstractmethod
    async def remove(self, user_id: UUID, source_id: UUID) -> None:
        """Idempotent: delete (user_id, source_id); missing row is a no-op."""
        ...

    @abstractmethod
    async def list_for_user(self, user_id: UUID) -> list[BlockedSource]:
        """Return the full list of blocked sources for *user_id* with blocked_at."""
        ...
