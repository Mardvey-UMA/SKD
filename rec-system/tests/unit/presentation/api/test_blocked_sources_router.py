"""RED tests for /users/{user_id}/blocked-sources endpoints (Phase 1 user-sources)."""
from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import AsyncMock
from uuid import uuid4

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient


def _make_app(repo_mock):
    from src.presentation.api.blocked_sources_router import (
        blocked_sources_router,
        get_blocked_sources_repository,
    )

    app = FastAPI()
    app.include_router(blocked_sources_router)
    app.dependency_overrides[get_blocked_sources_repository] = lambda: repo_mock
    return app


@pytest.fixture
def repo_mock():
    mock = AsyncMock()
    mock.add.return_value = None
    mock.remove.return_value = None
    mock.list_for_user.return_value = []
    return mock


class TestPostBlockedSources:
    @pytest.mark.unit
    async def test_post_returns_204_on_create(self, repo_mock):
        app = _make_app(repo_mock)
        user_id = uuid4()
        source_id = uuid4()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                f"/users/{user_id}/blocked-sources",
                json={"source_id": str(source_id)},
            )
        assert response.status_code == 204
        repo_mock.add.assert_awaited_once()
        args, kwargs = repo_mock.add.call_args
        passed = list(args) + list(kwargs.values())
        assert user_id in passed
        assert source_id in passed

    @pytest.mark.unit
    async def test_post_returns_204_on_duplicate(self, repo_mock):
        """Idempotent: repeated block returns 204 (repo.add is itself idempotent)."""
        app = _make_app(repo_mock)
        user_id = uuid4()
        source_id = uuid4()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response1 = await client.post(
                f"/users/{user_id}/blocked-sources",
                json={"source_id": str(source_id)},
            )
            response2 = await client.post(
                f"/users/{user_id}/blocked-sources",
                json={"source_id": str(source_id)},
            )
        assert response1.status_code == 204
        assert response2.status_code == 204

    @pytest.mark.unit
    async def test_post_rejects_malformed_uuid(self, repo_mock):
        app = _make_app(repo_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                f"/users/{uuid4()}/blocked-sources",
                json={"source_id": "not-a-uuid"},
            )
        assert response.status_code == 422

    @pytest.mark.unit
    async def test_post_rejects_missing_source_id(self, repo_mock):
        app = _make_app(repo_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                f"/users/{uuid4()}/blocked-sources",
                json={},
            )
        assert response.status_code == 422


class TestDeleteBlockedSources:
    @pytest.mark.unit
    async def test_delete_returns_204_on_success(self, repo_mock):
        app = _make_app(repo_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.delete(
                f"/users/{uuid4()}/blocked-sources/{uuid4()}",
            )
        assert response.status_code == 204
        repo_mock.remove.assert_awaited_once()

    @pytest.mark.unit
    async def test_delete_returns_204_on_nonexistent(self, repo_mock):
        """Idempotent: DELETE of a missing row still returns 204."""
        app = _make_app(repo_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.delete(
                f"/users/{uuid4()}/blocked-sources/{uuid4()}",
            )
        assert response.status_code == 204


class TestGetBlockedSources:
    @pytest.mark.unit
    async def test_get_returns_empty_list(self, repo_mock):
        app = _make_app(repo_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get(f"/users/{uuid4()}/blocked-sources")
        assert response.status_code == 200
        body = response.json()
        assert body == {"items": [], "count": 0}

    @pytest.mark.unit
    async def test_get_returns_list_with_items(self, repo_mock):
        from src.domain.value_objects.blocked_source import BlockedSource

        s1 = uuid4()
        t1 = datetime(2026, 4, 10, 12, 0, 0, tzinfo=timezone.utc)
        repo_mock.list_for_user.return_value = [BlockedSource(source_id=s1, blocked_at=t1)]

        app = _make_app(repo_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get(f"/users/{uuid4()}/blocked-sources")
        assert response.status_code == 200
        body = response.json()
        assert body["count"] == 1
        assert body["items"][0]["source_id"] == str(s1)
        assert "blocked_at" in body["items"][0]
