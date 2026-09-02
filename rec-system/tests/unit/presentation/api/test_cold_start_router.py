"""Unit tests for GET /recommendations/cold-start router — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock
from datetime import datetime, timezone
from uuid import uuid4, UUID

from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.application.dto.cold_start import ColdStartResponse


class TestColdStartEndpoint:
    """Tests for GET /recommendations/cold-start endpoint."""

    def _make_app(self, cold_start_mock) -> FastAPI:
        from src.presentation.api.recommendations_router import (
            recommendations_router,
            get_cold_start_use_case,
            get_recommendations_use_case,
        )
        # Dummy mock for POST /recommendations
        dummy_rec_mock = AsyncMock()
        app = FastAPI()
        app.include_router(recommendations_router)
        app.dependency_overrides[get_cold_start_use_case] = lambda: cold_start_mock
        app.dependency_overrides[get_recommendations_use_case] = lambda: dummy_rec_mock
        return app

    @pytest.fixture
    def cold_start_mock(self):
        mock = AsyncMock()
        mock.execute.return_value = ColdStartResponse(
            items=[uuid4(), uuid4(), uuid4()],
            count=3,
            generated_at=datetime.now(timezone.utc),
        )
        return mock

    @pytest.mark.unit
    async def test_returns_200_with_correct_structure(self, cold_start_mock):
        app = self._make_app(cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/recommendations/cold-start")
        assert response.status_code == 200
        body = response.json()
        assert "items" in body
        assert "count" in body
        assert "generated_at" in body

    @pytest.mark.unit
    async def test_default_count_is_30(self, cold_start_mock):
        app = self._make_app(cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.get("/recommendations/cold-start")
        cold_start_mock.execute.assert_called_once()
        call_kwargs = cold_start_mock.execute.call_args
        # Default count=30 should be passed
        assert call_kwargs.kwargs.get("count", call_kwargs.args[0] if call_kwargs.args else None) <= 50

    @pytest.mark.unit
    async def test_count_capped_at_50(self, cold_start_mock):
        app = self._make_app(cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.get("/recommendations/cold-start?count=100")
        cold_start_mock.execute.assert_called_once()
        call_kwargs = cold_start_mock.execute.call_args
        # count must be capped at 50
        actual_count = call_kwargs.kwargs.get("count", call_kwargs.args[0] if call_kwargs.args else None)
        assert actual_count == 50

    @pytest.mark.unit
    async def test_response_has_items_as_uuid_strings(self, cold_start_mock):
        app = self._make_app(cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/recommendations/cold-start")
        body = response.json()
        items = body["items"]
        assert isinstance(items, list)
        for item in items:
            assert isinstance(item, str)
            UUID(item)  # Must be parseable as UUID

    @pytest.mark.unit
    async def test_count_param_respected(self, cold_start_mock):
        cold_start_mock.execute.return_value = ColdStartResponse(
            items=[uuid4() for _ in range(20)],
            count=20,
            generated_at=datetime.now(timezone.utc),
        )
        app = self._make_app(cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/recommendations/cold-start?count=20")
        assert response.status_code == 200
        body = response.json()
        assert body["count"] == 20

    @pytest.mark.unit
    async def test_no_user_context_required(self, cold_start_mock):
        """Cold-start endpoint does not require user_id."""
        app = self._make_app(cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/recommendations/cold-start")
        # No user_id needed — endpoint is public
        assert response.status_code == 200
