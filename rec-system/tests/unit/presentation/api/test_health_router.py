"""Unit tests for /health endpoint — updated to new contract."""
from __future__ import annotations

import pytest
from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.presentation.api.health_router import health_router


class TestHealthEndpoint:
    """Tests for GET /health endpoint."""

    def _make_app(self) -> FastAPI:
        app = FastAPI()
        app.include_router(health_router)
        return app

    @pytest.mark.unit
    async def test_returns_200(self):
        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        assert response.status_code == 200

    @pytest.mark.unit
    async def test_response_body(self):
        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        body = response.json()
        assert body["status"] == "ok"
        assert "version" in body
