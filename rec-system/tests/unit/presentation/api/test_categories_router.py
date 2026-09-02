"""Unit tests for GET /categories router — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.application.dto.categories import CategoriesResponse, CategoryItem


class TestCategoriesEndpoint:
    """Tests for GET /categories endpoint."""

    def _make_app(self, use_case_mock) -> FastAPI:
        from src.presentation.api.categories_router import (
            categories_router,
            get_categories_use_case,
        )
        app = FastAPI()
        app.include_router(categories_router)
        app.dependency_overrides[get_categories_use_case] = lambda: use_case_mock
        return app

    @pytest.fixture
    def use_case_mock(self):
        mock = AsyncMock()
        mock.execute.return_value = CategoriesResponse(
            categories=[
                CategoryItem(id="технологии", name="Технологии", icon="laptop"),
                CategoryItem(id="наука", name="Наука", icon="flask"),
                CategoryItem(id="спорт", name="Спорт", icon="trophy"),
            ],
            min_select=3,
            max_select=10,
        )
        return mock

    @pytest.mark.unit
    async def test_returns_200(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/categories")
        assert response.status_code == 200

    @pytest.mark.unit
    async def test_response_contains_categories_list(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/categories")
        body = response.json()
        assert "categories" in body
        assert isinstance(body["categories"], list)
        assert len(body["categories"]) == 3

    @pytest.mark.unit
    async def test_category_items_have_id_name_icon(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/categories")
        body = response.json()
        first = body["categories"][0]
        assert "id" in first
        assert "name" in first
        assert "icon" in first
        assert first["id"] == "технологии"
        assert first["name"] == "Технологии"
        assert first["icon"] == "laptop"

    @pytest.mark.unit
    async def test_response_contains_min_select_and_max_select(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/categories")
        body = response.json()
        assert "min_select" in body
        assert "max_select" in body
        assert body["min_select"] == 3
        assert body["max_select"] == 10

    @pytest.mark.unit
    async def test_locale_parameter_accepted(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/categories?locale=ru")
        assert response.status_code == 200

    @pytest.mark.unit
    async def test_locale_parameter_does_not_change_response(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response_ru = await client.get("/categories?locale=ru")
            response_default = await client.get("/categories")
        assert response_ru.json() == response_default.json()

    @pytest.mark.unit
    async def test_calls_use_case(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.get("/categories")
        use_case_mock.execute.assert_called_once()
