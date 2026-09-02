"""Tests for GetColdStartUseCase."""
from __future__ import annotations

import json
import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock
from uuid import UUID, uuid4

from src.application.dto.cold_start import ColdStartResponse
from src.application.use_cases.get_cold_start import GetColdStartUseCase


REDIS_KEY = "rec:cold-start:trending"


def make_uuids(n: int) -> list[UUID]:
    return [uuid4() for _ in range(n)]


@pytest.fixture
def cache_client():
    return AsyncMock()


@pytest.fixture
def content_repo():
    return AsyncMock()


@pytest.fixture
def sut(cache_client, content_repo):
    return GetColdStartUseCase(
        cache_client=cache_client,
        content_repo=content_repo,
    )


def build_cached_payload(uuids: list[UUID]) -> str:
    return json.dumps({
        "items": [str(u) for u in uuids],
        "generated_at": datetime.now(timezone.utc).isoformat(),
    })


@pytest.mark.unit
class TestGetColdStartUseCase:
    @pytest.mark.asyncio
    async def test_execute_returns_cold_start_response(self, sut, cache_client):
        """execute returns a ColdStartResponse."""
        uuids = make_uuids(5)
        cache_client.get.return_value = build_cached_payload(uuids)
        result = await sut.execute()
        assert isinstance(result, ColdStartResponse)

    @pytest.mark.asyncio
    async def test_cache_hit_returns_items_from_redis(self, sut, cache_client):
        """On cache hit, items come from Redis."""
        uuids = make_uuids(10)
        cache_client.get.return_value = build_cached_payload(uuids)
        result = await sut.execute(count=10)
        assert len(result.items) == 10
        assert all(isinstance(i, UUID) for i in result.items)

    @pytest.mark.asyncio
    async def test_cache_hit_limits_to_count(self, sut, cache_client):
        """On cache hit, only 'count' items are returned."""
        uuids = make_uuids(50)
        cache_client.get.return_value = build_cached_payload(uuids)
        result = await sut.execute(count=5)
        assert len(result.items) == 5

    @pytest.mark.asyncio
    async def test_cache_miss_queries_content_repo(self, sut, cache_client, content_repo):
        """On cache miss, queries published_content via content_repo."""
        cache_client.get.return_value = None
        uuids = make_uuids(20)
        content_repo.get_recent_published_ids.return_value = uuids
        result = await sut.execute(count=10)
        content_repo.get_recent_published_ids.assert_called_once()
        assert len(result.items) == 10

    @pytest.mark.asyncio
    async def test_cache_miss_returns_all_if_fewer_than_count(self, sut, cache_client, content_repo):
        """Cache miss: returns all available if fewer than count."""
        cache_client.get.return_value = None
        uuids = make_uuids(3)
        content_repo.get_recent_published_ids.return_value = uuids
        result = await sut.execute(count=30)
        assert len(result.items) == 3

    @pytest.mark.asyncio
    async def test_count_capped_at_50(self, sut, cache_client):
        """count is capped at 50."""
        uuids = make_uuids(50)
        cache_client.get.return_value = build_cached_payload(uuids)
        result = await sut.execute(count=200)
        assert len(result.items) == 50

    @pytest.mark.asyncio
    async def test_empty_published_content_returns_empty_list(self, sut, cache_client, content_repo):
        """Empty published_content returns empty items list with count=0."""
        cache_client.get.return_value = None
        content_repo.get_recent_published_ids.return_value = []
        result = await sut.execute(count=30)
        assert result.items == []
        assert result.count == 0

    @pytest.mark.asyncio
    async def test_generated_at_is_valid_datetime(self, sut, cache_client):
        """generated_at field is a valid datetime."""
        uuids = make_uuids(5)
        cache_client.get.return_value = build_cached_payload(uuids)
        result = await sut.execute()
        assert isinstance(result.generated_at, datetime)

    @pytest.mark.asyncio
    async def test_response_count_matches_items_len(self, sut, cache_client):
        """response.count equals len(items)."""
        uuids = make_uuids(7)
        cache_client.get.return_value = build_cached_payload(uuids)
        result = await sut.execute(count=7)
        assert result.count == len(result.items)
