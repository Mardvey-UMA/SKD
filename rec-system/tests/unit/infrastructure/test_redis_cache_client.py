"""Unit tests for RedisCacheClient adapter."""
from __future__ import annotations

import pytest

from src.domain.interfaces.cache_client import CacheClient


class TestRedisCacheClientInterface:
    def test_implements_cache_client_interface(self):
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import MagicMock

        mock_redis = MagicMock()
        client = RedisCacheClient(redis_client=mock_redis)
        assert isinstance(client, CacheClient)


class TestRedisCacheClientWithFakeRedis:
    """Integration-style unit tests using fakeredis."""

    @pytest.fixture
    async def cache_client(self):
        import fakeredis.aioredis
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient

        fake = fakeredis.aioredis.FakeRedis()
        client = RedisCacheClient(redis_client=fake)
        yield client
        await fake.aclose()

    @pytest.mark.asyncio
    async def test_get_returns_none_for_nonexistent_key(self, cache_client):
        result = await cache_client.get("nonexistent_key")
        assert result is None

    @pytest.mark.asyncio
    async def test_set_and_get_roundtrip(self, cache_client):
        await cache_client.set("test_key", "test_value")
        result = await cache_client.get("test_key")
        assert result == "test_value"

    @pytest.mark.asyncio
    async def test_set_with_ttl_stores_value_immediately(self, cache_client):
        await cache_client.set("ttl_key", "ttl_value", ttl=900)
        result = await cache_client.get("ttl_key")
        assert result == "ttl_value"

    @pytest.mark.asyncio
    async def test_delete_removes_key(self, cache_client):
        await cache_client.set("del_key", "del_value")
        await cache_client.delete("del_key")
        result = await cache_client.get("del_key")
        assert result is None

    @pytest.mark.asyncio
    async def test_get_after_delete_returns_none(self, cache_client):
        await cache_client.set("key", "value")
        result_before = await cache_client.get("key")
        assert result_before == "value"

        await cache_client.delete("key")
        result_after = await cache_client.get("key")
        assert result_after is None

    @pytest.mark.asyncio
    async def test_delete_nonexistent_key_is_noop(self, cache_client):
        """Deleting a key that doesn't exist should not raise."""
        await cache_client.delete("nonexistent_key_xyz")
        # No exception raised


class TestRedisCacheClientWithMockedRedis:
    """Unit tests using mocked redis client."""

    @pytest.mark.asyncio
    async def test_get_calls_redis_get(self):
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import AsyncMock, MagicMock

        mock_redis = MagicMock()
        mock_redis.get = AsyncMock(return_value=b"hello")
        client = RedisCacheClient(redis_client=mock_redis)

        result = await client.get("my_key")
        mock_redis.get.assert_called_once_with("my_key")
        assert result == "hello"

    @pytest.mark.asyncio
    async def test_get_returns_none_when_redis_returns_none(self):
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import AsyncMock, MagicMock

        mock_redis = MagicMock()
        mock_redis.get = AsyncMock(return_value=None)
        client = RedisCacheClient(redis_client=mock_redis)

        result = await client.get("missing")
        assert result is None

    @pytest.mark.asyncio
    async def test_set_without_ttl_calls_set(self):
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import AsyncMock, MagicMock

        mock_redis = MagicMock()
        mock_redis.set = AsyncMock()
        client = RedisCacheClient(redis_client=mock_redis)

        await client.set("key", "value")
        mock_redis.set.assert_called_once_with("key", "value")

    @pytest.mark.asyncio
    async def test_set_with_ttl_calls_setex(self):
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import AsyncMock, MagicMock

        mock_redis = MagicMock()
        mock_redis.setex = AsyncMock()
        client = RedisCacheClient(redis_client=mock_redis)

        await client.set("key", "value", ttl=900)
        mock_redis.setex.assert_called_once_with("key", 900, "value")

    @pytest.mark.asyncio
    async def test_delete_calls_redis_delete(self):
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import AsyncMock, MagicMock

        mock_redis = MagicMock()
        mock_redis.delete = AsyncMock()
        client = RedisCacheClient(redis_client=mock_redis)

        await client.delete("my_key")
        mock_redis.delete.assert_called_once_with("my_key")

    @pytest.mark.asyncio
    async def test_exceptions_propagate_from_redis(self):
        """No fallback -- exceptions from Redis should propagate."""
        from src.infrastructure.cache.redis_cache_client import RedisCacheClient
        from unittest.mock import AsyncMock, MagicMock

        mock_redis = MagicMock()
        mock_redis.get = AsyncMock(side_effect=ConnectionError("Redis down"))
        client = RedisCacheClient(redis_client=mock_redis)

        with pytest.raises(ConnectionError):
            await client.get("any_key")
