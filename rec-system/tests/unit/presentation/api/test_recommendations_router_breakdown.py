"""RED tests for include_breakdown query param on POST /recommendations (P7)."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock
from uuid import uuid4, UUID

from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.application.dto.recommendations import RecommendationsResponse
from src.presentation.schemas.recommendations import FeedItemDetail

SCORING_KEYS = ["topic_match", "embedding_sim", "entity_match", "sentiment_match", "freshness", "format_match"]


def _make_detail(content_id: UUID, raw_id: UUID) -> FeedItemDetail:
    return FeedItemDetail(
        content_id=content_id,
        raw_content_id=raw_id,
        final_score=0.7,
        scoring_components={k: 0.1 for k in SCORING_KEYS},
        cold_start_applied=False,
    )


def _make_app(rec_mock, profile_mock=None) -> FastAPI:
    from src.presentation.api.recommendations_router import (
        recommendations_router,
        get_recommendations_use_case,
        get_cold_start_use_case,
    )
    from src.presentation.dependencies.user_profile_guard import get_user_profile_repository

    if profile_mock is None:
        profile_mock = AsyncMock()
        profile_mock.get_by_user_id.return_value = object()

    app = FastAPI()
    app.include_router(recommendations_router)
    app.dependency_overrides[get_recommendations_use_case] = lambda: rec_mock
    app.dependency_overrides[get_user_profile_repository] = lambda: profile_mock
    return app


@pytest.mark.unit
class TestRecommendationsRouterBreakdown:
    @pytest.fixture
    def user_id(self):
        return uuid4()

    def _legacy_response(self, user_id: UUID) -> RecommendationsResponse:
        return RecommendationsResponse(
            user_id=user_id,
            items=[uuid4(), uuid4()],
            count=2,
            generated_at=datetime.now(timezone.utc),
        )

    def _breakdown_response(self, user_id: UUID) -> RecommendationsResponse:
        items = [uuid4(), uuid4()]
        details = [_make_detail(cid, uuid4()) for cid in items]
        return RecommendationsResponse(
            user_id=user_id,
            items=items,
            count=2,
            generated_at=datetime.now(timezone.utc),
            items_detailed=[d.model_dump() for d in details],
            latency_breakdown={"retrieval_ms": 5, "scoring_ms": 10, "rerank_ms": 0, "total_ms": 15},
            profile_snapshot={"interaction_count": 3, "cold_start": False},
            feature_flags={"live_profile": False, "hot_arrival": False, "rerank": False},
        )

    @pytest.mark.asyncio
    async def test_without_flag_returns_legacy_shape(self, user_id):
        """No ?include_breakdown → body has no items_detailed."""
        mock = AsyncMock()
        mock.execute.return_value = self._legacy_response(user_id)
        app = _make_app(mock)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            resp = await client.post("/recommendations", json={"user_id": str(user_id), "count": 2})

        assert resp.status_code == 200
        body = resp.json()
        assert "items_detailed" not in body
        assert "latency_breakdown" not in body
        assert "profile_snapshot" not in body
        assert "feature_flags" not in body

    @pytest.mark.asyncio
    async def test_with_flag_true_response_has_items_detailed(self, user_id):
        """?include_breakdown=true → body contains items_detailed."""
        mock = AsyncMock()
        mock.execute.return_value = self._breakdown_response(user_id)
        app = _make_app(mock)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            resp = await client.post(
                "/recommendations?include_breakdown=true",
                json={"user_id": str(user_id), "count": 2},
            )

        assert resp.status_code == 200
        body = resp.json()
        assert "items_detailed" in body
        assert len(body["items_detailed"]) == 2
        assert "latency_breakdown" in body
        assert "profile_snapshot" in body
        assert "feature_flags" in body

    @pytest.mark.asyncio
    async def test_flag_is_passed_to_use_case(self, user_id):
        """include_breakdown=true must be forwarded to the use case DTO."""
        mock = AsyncMock()
        mock.execute.return_value = self._legacy_response(user_id)
        app = _make_app(mock)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.post(
                "/recommendations?include_breakdown=true",
                json={"user_id": str(user_id), "count": 5},
            )

        call_arg = mock.execute.call_args[0][0]
        assert call_arg.include_breakdown is True

    @pytest.mark.asyncio
    async def test_flag_false_passed_to_use_case(self, user_id):
        """include_breakdown absent → use case receives False."""
        mock = AsyncMock()
        mock.execute.return_value = self._legacy_response(user_id)
        app = _make_app(mock)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            await client.post("/recommendations", json={"user_id": str(user_id), "count": 5})

        call_arg = mock.execute.call_args[0][0]
        assert call_arg.include_breakdown is False

    @pytest.mark.asyncio
    async def test_items_detailed_scoring_components_keys(self, user_id):
        """scoring_components must have the 6 canonical keys."""
        mock = AsyncMock()
        mock.execute.return_value = self._breakdown_response(user_id)
        app = _make_app(mock)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            resp = await client.post(
                "/recommendations?include_breakdown=true",
                json={"user_id": str(user_id), "count": 2},
            )

        body = resp.json()
        for detail in body["items_detailed"]:
            assert set(detail["scoring_components"].keys()) == set(SCORING_KEYS)
