"""Redis implementation of CacheClient."""
from __future__ import annotations

from typing import Optional

import redis.asyncio

from src.domain.interfaces.cache_client import CacheClient


class RedisCacheClient(CacheClient):
    """Redis adapter implementing the CacheClient port.

    Redis is mandatory -- no fallback. Exceptions propagate to caller.
    """

    def __init__(self, redis_client: redis.asyncio.Redis) -> None:
        self._redis = redis_client

    async def get(self, key: str) -> Optional[str]:
        """Return the cached value for key, or None if absent/expired."""
        value = await self._redis.get(key)
        if value is None:
            return None
        if isinstance(value, bytes):
            return value.decode("utf-8")
        return str(value)

    async def set(self, key: str, value: str, ttl: Optional[int] = None) -> None:
        """Store value under key. If ttl given, expire after that many seconds."""
        if ttl is not None:
            await self._redis.setex(key, ttl, value)
        else:
            await self._redis.set(key, value)

    async def delete(self, key: str) -> None:
        """Delete key from the cache (no-op if absent)."""
        await self._redis.delete(key)
