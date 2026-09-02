"""Unit tests for POST /recommendations and GET /recommendations/cold-start — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock
from datetime import datetime, timezone
from uuid import uuid4, UUID

from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.application.dto.recommendations import RecommendationsResponse
from src.application.dto.cold_start import ColdStartResponse
from src.application.use_cases.get_recommendations import UserProfileNotFoundError


class TestRecommendationsEndpoint:
    """Tests for POST /recommendations endpoint."""

    def _make_app(self, rec_use_case_mock, cold_start_mock=None, profile_repo_mock=None) -> FastAPI:
        from src.presentation.api.recommendations_router import (
            recommendations_router,
            get_recommendations_use_case,
            get_cold_start_use_case,
        )
        from src.presentation.dependencies.user_profile_guard import get_user_profile_repository
        if profile_repo_mock is None:
            # Default: profile always exists so guard is a no-op
            profile_repo_mock = AsyncMock()
            profile_repo_mock.get_by_user_id.return_value = object()  # truthy — profile exists
        app = FastAPI()
        app.include_router(recommendations_router)
        app.dependency_overrides[get_recommendations_use_case] = lambda: rec_use_case_mock
        app.dependency_overrides[get_user_profile_repository] = lambda: profile_repo_mock
        if cold_start_mock is not None:
            app.dependency_overrides[get_cold_start_use_case] = lambda: cold_start_mock
        return app

    @pytest.fixture
    def user_id(self):
        return uuid4()

    @pytest.fixture
    def rec_use_case_mock(self, user_id):
        mock = AsyncMock()
        items = [uuid4(), uuid4(), uuid4()]
        mock.execute.return_value = RecommendationsResponse(
            user_id=user_id,
            items=items,
            count=len(items),
            generated_at=datetime.now(timezone.utc),
        )
        return mock

    @pytest.fixture
    def cold_start_mock(self):
        mock = AsyncMock()
        mock.execute.return_value = ColdStartResponse(
            items=[uuid4(), uuid4()],
            count=2,
            generated_at=datetime.now(timezone.utc),
        )
        return mock

    @pytest.mark.unit
    async def test_returns_200_with_correct_structure(self, user_id, rec_use_case_mock, cold_start_mock):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={"user_id": str(user_id), "count": 3},
            )
        assert response.status_code == 200
        body = response.json()
        assert "user_id" in body
        assert "items" in body
        assert "count" in body
        assert "generated_at" in body

    @pytest.mark.unit
    async def test_response_items_are_uuids_without_scores(self, user_id, rec_use_case_mock, cold_start_mock):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={"user_id": str(user_id), "count": 3},
            )
        body = response.json()
        items = body["items"]
        assert isinstance(items, list)
        for item in items:
            # Item must be a UUID string (not a dict with score)
            assert isinstance(item, str)
            UUID(item)  # Must be parseable as UUID

    @pytest.mark.unit
    async def test_404_when_user_profile_not_found(self, cold_start_mock):
        user_id = uuid4()
        mock = AsyncMock()
        mock.execute.side_effect = UserProfileNotFoundError(user_id)
        app = self._make_app(mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={"user_id": str(user_id), "count": 10},
            )
        assert response.status_code == 404
        body = response.json()
        assert body["error"] == "user_not_found"
        assert "user_id" in body
        assert "message" in body

    @pytest.mark.unit
    async def test_count_parameter_is_passed_to_use_case(self, rec_use_case_mock, cold_start_mock):
        user_id = uuid4()
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.post(
                "/recommendations",
                json={"user_id": str(user_id), "count": 42},
            )
        rec_use_case_mock.execute.assert_called_once()
        call_arg = rec_use_case_mock.execute.call_args[0][0]
        assert call_arg.count == 42

    @pytest.mark.unit
    async def test_old_generate_feed_endpoint_returns_404(self, rec_use_case_mock, cold_start_mock):
        """POST /generate-feed must no longer exist."""
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/generate-feed",
                json={"user_id": str(uuid4()), "feed_size": 10},
            )
        assert response.status_code == 404

    @pytest.mark.unit
    async def test_missing_user_id_returns_422(self, rec_use_case_mock, cold_start_mock):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={"count": 10},
            )
        assert response.status_code == 422

    @pytest.mark.unit
    async def test_invalid_count_zero_returns_422(self, rec_use_case_mock, cold_start_mock):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={"user_id": str(uuid4()), "count": 0},
            )
        assert response.status_code == 422

    # --- Phase 1 user-sources: new optional fields + NARROW mode ---

    @pytest.mark.unit
    async def test_backward_compat_accepts_legacy_body(self, rec_use_case_mock, cold_start_mock):
        """Legacy body {user_id, count} must still succeed (no new fields required)."""
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={"user_id": str(uuid4()), "count": 5},
            )
        assert response.status_code == 200

    @pytest.mark.unit
    async def test_accepts_include_and_exclude_source_ids(self, rec_use_case_mock, cold_start_mock):
        """Optional include_source_ids / exclude_source_ids are accepted and forwarded."""
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        sid_a = str(uuid4())
        sid_b = str(uuid4())
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={
                    "user_id": str(uuid4()),
                    "count": 10,
                    "include_source_ids": [sid_a],
                    "exclude_source_ids": [sid_b],
                },
            )
        assert response.status_code == 200
        dto = rec_use_case_mock.execute.call_args[0][0]
        assert [str(s) for s in dto.include_source_ids] == [sid_a]
        assert [str(s) for s in dto.exclude_source_ids] == [sid_b]

    @pytest.mark.unit
    async def test_rejects_malformed_uuid_in_include_list(self, rec_use_case_mock, cold_start_mock):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={
                    "user_id": str(uuid4()),
                    "count": 10,
                    "include_source_ids": ["not-a-uuid"],
                },
            )
        assert response.status_code == 422

    @pytest.mark.unit
    async def test_rejects_invalid_ranking_mode(self, rec_use_case_mock, cold_start_mock):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={
                    "user_id": str(uuid4()),
                    "count": 10,
                    "ranking_mode": "FUZZY",
                },
            )
        assert response.status_code == 422

    @pytest.mark.unit
    async def test_narrow_without_includes_returns_400(
        self, rec_use_case_mock, cold_start_mock
    ):
        """NARROW mode requires non-empty include_source_ids (locked decision)."""
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={
                    "user_id": str(uuid4()),
                    "count": 10,
                    "ranking_mode": "NARROW",
                },
            )
        assert response.status_code == 400
        body = response.json()
        assert body["error"] == "narrow_mode_requires_include_source_ids"

    @pytest.mark.unit
    async def test_narrow_with_empty_includes_returns_400(
        self, rec_use_case_mock, cold_start_mock
    ):
        """NARROW mode with include_source_ids=[] is equally rejected."""
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={
                    "user_id": str(uuid4()),
                    "count": 10,
                    "ranking_mode": "NARROW",
                    "include_source_ids": [],
                },
            )
        assert response.status_code == 400

    @pytest.mark.unit
    async def test_narrow_with_includes_returns_200(
        self, rec_use_case_mock, cold_start_mock
    ):
        app = self._make_app(rec_use_case_mock, cold_start_mock)
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post(
                "/recommendations",
                json={
                    "user_id": str(uuid4()),
                    "count": 10,
                    "ranking_mode": "NARROW",
                    "include_source_ids": [str(uuid4())],
                },
            )
        assert response.status_code == 200
