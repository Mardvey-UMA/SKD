"""Unit tests for updated GET /health endpoint — RED phase."""
from __future__ import annotations

import os
import pytest
from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport


class TestHealthEndpointUpdated:
    """Tests for updated GET /health endpoint with status: ok and version."""

    def _make_app(self) -> FastAPI:
        from src.presentation.api.health_router import health_router
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
    async def test_response_status_is_ok(self):
        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        body = response.json()
        assert body["status"] == "ok"

    @pytest.mark.unit
    async def test_response_contains_version_field(self):
        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        body = response.json()
        assert "version" in body

    @pytest.mark.unit
    async def test_version_defaults_to_1_2_0(self):
        # Ensure APP_VERSION is not set
        os.environ.pop("APP_VERSION", None)
        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        body = response.json()
        assert body["version"] == "1.2.0"

    @pytest.mark.unit
    async def test_custom_app_version_env_var_reflected(self):
        os.environ["APP_VERSION"] = "2.0.0"
        try:
            app = self._make_app()
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
                response = await client.get("/health")
            body = response.json()
            assert body["version"] == "2.0.0"
        finally:
            os.environ.pop("APP_VERSION", None)

    @pytest.mark.unit
    async def test_old_healthy_response_is_gone(self):
        """The old response format {"status": "healthy"} must be gone."""
        os.environ.pop("APP_VERSION", None)
        app = self._make_app()
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")
        body = response.json()
        assert body.get("status") != "healthy"
