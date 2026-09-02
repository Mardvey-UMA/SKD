"""Unit tests for POST /onboarding router — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock
from uuid import uuid4

from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.application.dto.onboard import OnboardResponse


class TestOnboardingEndpoint:
    """Tests for POST /onboarding endpoint."""

    def _make_app(self, use_case_mock, profile_repo_mock=None) -> FastAPI:
        from src.presentation.api.onboarding_router import (
            onboarding_router,
            get_onboard_user_use_case,
        )
        from src.presentation.dependencies.user_profile_guard import get_user_profile_repository
        if profile_repo_mock is None:
            # Default: profile always exists so guard is a no-op
            profile_repo_mock = AsyncMock()
            profile_repo_mock.get_by_user_id.return_value = object()  # truthy — profile exists
        app = FastAPI()
        app.include_router(onboarding_router)
        app.dependency_overrides[get_onboard_user_use_case] = lambda: use_case_mock
        app.dependency_overrides[get_user_profile_repository] = lambda: profile_repo_mock
        return app

    @pytest.fixture
    def user_id(self):
        return uuid4()

    @pytest.fixture
    def use_case_mock(self, user_id):
        mock = AsyncMock()
        mock.execute.return_value = OnboardResponse(
            user_id=user_id,
            profile_initialized=True,
        )
        return mock

    @pytest.mark.unit
    async def test_returns_200_with_correct_structure(self, user_id, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/onboarding",
                json={
                    "user_id": str(user_id),
                    "categories": ["технологии", "наука", "бизнес"],
                },
            )
        assert response.status_code == 200
        body = response.json()
        assert "user_id" in body
        assert "profile_initialized" in body

    @pytest.mark.unit
    async def test_response_profile_initialized_true(self, user_id, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/onboarding",
                json={
                    "user_id": str(user_id),
                    "categories": ["технологии", "наука", "бизнес"],
                },
            )
        body = response.json()
        assert body["profile_initialized"] is True

    @pytest.mark.unit
    async def test_no_profile_not_found_404_path_in_router(self):
        """Router no longer maps ValueError('No recommendation profile exists...') to 404.

        The ensure_user_profile_exists dependency now guarantees the profile
        exists before the use case executes, so that ValueError is never raised.
        This test verifies the old 404-mapping code is absent from the router.
        """
        from src.presentation.api.onboarding_router import onboard_user
        import inspect
        source = inspect.getsource(onboard_user)
        # The old indicator constant is gone from the router
        assert "No recommendation profile exists" not in source
        assert "user_not_found" not in source

    @pytest.mark.unit
    async def test_fewer_than_3_categories_returns_422(self, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/onboarding",
                json={
                    "user_id": str(uuid4()),
                    "categories": ["технологии", "наука"],
                },
            )
        assert response.status_code == 422

    @pytest.mark.unit
    async def test_source_content_ids_is_optional(self, user_id, use_case_mock):
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/onboarding",
                json={
                    "user_id": str(user_id),
                    "categories": ["технологии", "наука", "бизнес"],
                    # No source_content_ids
                },
            )
        assert response.status_code == 200

    @pytest.mark.unit
    async def test_old_onboard_endpoint_returns_404(self, use_case_mock):
        """POST /onboard must no longer exist."""
        app = self._make_app(use_case_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/onboard",
                json={
                    "user_id": str(uuid4()),
                    "chosen_topics": ["tech", "science", "sports"],
                },
            )
        assert response.status_code == 404

    @pytest.mark.unit
    async def test_calls_use_case_with_correct_data(self, user_id, use_case_mock):
        app = self._make_app(use_case_mock)
        categories = ["технологии", "наука", "бизнес"]
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.post(
                "/onboarding",
                json={"user_id": str(user_id), "categories": categories},
            )
        use_case_mock.execute.assert_called_once()
        call_arg = use_case_mock.execute.call_args[0][0]
        assert call_arg.user_id == user_id
        assert call_arg.categories == categories
