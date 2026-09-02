"""RED tests for POST /recommendations/explain endpoint — Phase 8."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, patch
from datetime import datetime, timezone
from uuid import uuid4, UUID

from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport

from src.application.services.scoring_explainer import (
    ScoringExplanation,
    ComponentBreakdown,
    NotFoundError,
)


# ── helpers ───────────────────────────────────────────────────────────────────


def _make_component(name: str, value: float | None = 0.5, weight: float = 0.20) -> ComponentBreakdown:
    contribution = (value or 0.0) * weight
    details: dict = {}
    if name == "topic_match":
        details["matched_topics"] = [
            {"topic": "технологии", "profile_weight": 0.45, "post_score": 0.92}
        ]
    elif name == "entity_match":
        details["matched_entities"] = []
        details["interest_weights"] = {}
    elif name == "sentiment_match":
        details["post_sentiment"] = "POSITIVE"
        details["profile_preference"] = {"POSITIVE": 0.4, "NEGATIVE": 0.2, "NEUTRAL": 0.4}
    elif name == "freshness":
        details["age_hours"] = 2.3
        details["half_life_hours"] = 48
    elif name == "format_match":
        details["post_word_count"] = 400
        details["profile_preferred_word_count_avg"] = 400.0
        details["post_complexity"] = 0.6
        details["profile_preferred_complexity_avg"] = 0.6
    return ComponentBreakdown(
        name=name,
        value=value,
        weight=weight,
        contribution=contribution,
        details=details,
    )


def _make_explanation(
    dead_components: list[str] | None = None,
) -> ScoringExplanation:
    names = ["topic_match", "embedding_sim", "entity_match", "sentiment_match", "freshness", "format_match"]
    weights = {"topic_match": 0.30, "embedding_sim": 0.25, "entity_match": 0.15,
               "sentiment_match": 0.05, "freshness": 0.15, "format_match": 0.10}
    dead = dead_components or []
    comps = []
    for n in names:
        val = None if n in dead else 0.5
        w = weights[n]
        comps.append(_make_component(n, value=val, weight=w))

    return ScoringExplanation(
        content_id=uuid4(),
        raw_content_id=uuid4(),
        published_content_id=uuid4(),
        final_score=0.78,
        components=comps,
        cold_start_applied=bool(dead),
        dead_components=dead,
        redistributed_weights={"topic_match": 0.45} if dead else None,
        rank_in_candidates=12,
        rank_in_feed=3,
        filtered_out_by=None,
    )


def _make_app(explainer_mock) -> FastAPI:
    from src.presentation.api.explain_router import explain_router, get_explainer

    app = FastAPI()
    app.include_router(explain_router)
    app.dependency_overrides[get_explainer] = lambda: explainer_mock
    return app


# ── tests ─────────────────────────────────────────────────────────────────────


@pytest.mark.unit
async def test_explain_returns_403_when_disabled(monkeypatch):
    """Endpoint returns 403 when REC_EXPLAIN_ENABLED env var is not 'true'."""
    monkeypatch.delenv("REC_EXPLAIN_ENABLED", raising=False)
    explainer = AsyncMock()
    app = _make_app(explainer)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/recommendations/explain",
            json={"user_id": str(uuid4()), "content_id": str(uuid4())},
        )

    assert response.status_code == 403


@pytest.mark.unit
async def test_explain_returns_200_with_breakdown(monkeypatch):
    """Returns 200 with full component breakdown when enabled and input is valid."""
    monkeypatch.setenv("REC_EXPLAIN_ENABLED", "true")
    explanation = _make_explanation()
    explainer = AsyncMock()
    explainer.explain.return_value = explanation
    app = _make_app(explainer)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/recommendations/explain",
            json={"user_id": str(uuid4()), "content_id": str(uuid4())},
        )

    assert response.status_code == 200
    body = response.json()
    assert "components" in body
    assert "topic_match" in body["components"]
    assert "contribution" in body["components"]["topic_match"]
    assert "final_score" in body
    assert "dead_components" in body
    assert "cold_start_applied" in body


@pytest.mark.unit
async def test_explain_returns_404_unknown_user(monkeypatch):
    """Returns 404 when explainer raises NotFoundError (unknown user)."""
    monkeypatch.setenv("REC_EXPLAIN_ENABLED", "true")
    explainer = AsyncMock()
    explainer.explain.side_effect = NotFoundError("User not found")
    app = _make_app(explainer)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/recommendations/explain",
            json={"user_id": str(uuid4()), "content_id": str(uuid4())},
        )

    assert response.status_code == 404


@pytest.mark.unit
async def test_explain_returns_422_malformed_uuid(monkeypatch):
    """Returns 422 when request body contains invalid UUID strings."""
    monkeypatch.setenv("REC_EXPLAIN_ENABLED", "true")
    explainer = AsyncMock()
    app = _make_app(explainer)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/recommendations/explain",
            json={"user_id": "not-a-uuid", "content_id": "also-invalid"},
        )

    assert response.status_code == 422


@pytest.mark.unit
async def test_explain_dead_components_listed(monkeypatch):
    """When explainer returns dead_components, they appear in response."""
    monkeypatch.setenv("REC_EXPLAIN_ENABLED", "true")
    explanation = _make_explanation(dead_components=["embedding_sim"])
    explainer = AsyncMock()
    explainer.explain.return_value = explanation
    app = _make_app(explainer)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/recommendations/explain",
            json={"user_id": str(uuid4()), "content_id": str(uuid4())},
        )

    assert response.status_code == 200
    body = response.json()
    assert "embedding_sim" in body["dead_components"]
    # embedding_sim component value should be null
    assert body["components"]["embedding_sim"]["value"] is None
