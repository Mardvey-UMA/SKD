from __future__ import annotations

from abc import ABC, abstractmethod


class CacheClient(ABC):
    """Abstract port for Redis cache operations."""

    @abstractmethod
    async def get(self, key: str) -> str | None:
        """Return the cached value for *key*, or None if absent/expired."""
        ...

    @abstractmethod
    async def set(self, key: str, value: str, ttl: int | None = None) -> None:
        """Store *value* under *key*.  If *ttl* is given, expire after that many seconds."""
        ...

    @abstractmethod
    async def delete(self, key: str) -> None:
        """Delete *key* from the cache (no-op if absent)."""
        ...
