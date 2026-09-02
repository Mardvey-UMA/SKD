"""RED tests for FeedItemDetail schema and extended RecommendationsResponseSchema (P7)."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from uuid import uuid4, UUID

from src.presentation.schemas.recommendations import (
    FeedItemDetail,
    RecommendationsResponseSchema,
)

SCORING_KEYS = {"topic_match", "embedding_sim", "entity_match", "sentiment_match", "freshness", "format_match"}


@pytest.mark.unit
class TestRecommendationsResponseBackwardCompat:
    def test_legacy_dump_has_no_breakdown_fields(self):
        """model_dump(exclude_none=True) without new fields must produce legacy shape."""
        uid = uuid4()
        schema = RecommendationsResponseSchema(
            user_id=uid,
            items=[uuid4(), uuid4()],
            count=2,
            generated_at=datetime.now(timezone.utc),
        )
        dumped = schema.model_dump(exclude_none=True)
        assert set(dumped.keys()) == {"user_id", "items", "count", "generated_at"}

    def test_legacy_json_has_no_breakdown_fields(self):
        uid = uuid4()
        schema = RecommendationsResponseSchema(
            user_id=uid,
            items=[uuid4()],
            count=1,
            generated_at=datetime.now(timezone.utc),
        )
        data = schema.model_dump(mode="json", exclude_none=True)
        assert "items_detailed" not in data
        assert "latency_breakdown" not in data
        assert "profile_snapshot" not in data
        assert "feature_flags" not in data

    def test_items_are_uuids(self):
        items = [uuid4(), uuid4(), uuid4()]
        schema = RecommendationsResponseSchema(
            user_id=uuid4(),
            items=items,
            count=3,
            generated_at=datetime.now(timezone.utc),
        )
        assert schema.items == items


@pytest.mark.unit
class TestFeedItemDetail:
    def _make_components(self) -> dict:
        return {
            "topic_match": 0.42,
            "embedding_sim": 0.31,
            "entity_match": 0.05,
            "sentiment_match": 0.00,
            "freshness": 0.15,
            "format_match": 0.02,
        }

    def test_all_fields_populated(self):
        content_id = uuid4()
        raw_id = uuid4()
        detail = FeedItemDetail(
            content_id=content_id,
            raw_content_id=raw_id,
            final_score=0.742,
            scoring_components=self._make_components(),
            cold_start_applied=False,
            rerank_score=None,
            filtered_out_by=None,
        )
        assert detail.content_id == content_id
        assert detail.raw_content_id == raw_id
        assert abs(detail.final_score - 0.742) < 1e-9
        assert set(detail.scoring_components.keys()) == SCORING_KEYS
        assert detail.cold_start_applied is False
        assert detail.rerank_score is None
        assert detail.filtered_out_by is None

    def test_scoring_components_has_six_canonical_keys(self):
        detail = FeedItemDetail(
            content_id=uuid4(),
            raw_content_id=uuid4(),
            final_score=0.5,
            scoring_components=self._make_components(),
            cold_start_applied=False,
        )
        assert set(detail.scoring_components.keys()) == SCORING_KEYS

    def test_dump_excludes_none_rerank_score(self):
        detail = FeedItemDetail(
            content_id=uuid4(),
            raw_content_id=uuid4(),
            final_score=0.5,
            scoring_components=self._make_components(),
            cold_start_applied=False,
        )
        dumped = detail.model_dump(exclude_none=True)
        assert "rerank_score" not in dumped
        assert "filtered_out_by" not in dumped


@pytest.mark.unit
class TestRecommendationsResponseWithBreakdown:
    def _make_detail(self) -> FeedItemDetail:
        return FeedItemDetail(
            content_id=uuid4(),
            raw_content_id=uuid4(),
            final_score=0.7,
            scoring_components={
                "topic_match": 0.3,
                "embedding_sim": 0.2,
                "entity_match": 0.1,
                "sentiment_match": 0.05,
                "freshness": 0.15,
                "format_match": 0.1,
            },
            cold_start_applied=False,
        )

    def test_items_detailed_populated(self):
        uid = uuid4()
        details = [self._make_detail(), self._make_detail()]
        schema = RecommendationsResponseSchema(
            user_id=uid,
            items=[d.content_id for d in details],
            count=2,
            generated_at=datetime.now(timezone.utc),
            items_detailed=details,
            latency_breakdown={"retrieval_ms": 10, "scoring_ms": 20, "rerank_ms": 0, "total_ms": 30},
            profile_snapshot={"interaction_count": 5, "cold_start": False},
            feature_flags={"live_profile": False, "hot_arrival": False, "rerank": False},
        )
        assert len(schema.items_detailed) == 2
        assert schema.latency_breakdown["total_ms"] == 30
        assert schema.profile_snapshot["cold_start"] is False
        assert "live_profile" in schema.feature_flags

    def test_dump_with_breakdown_includes_all_new_fields(self):
        uid = uuid4()
        detail = self._make_detail()
        schema = RecommendationsResponseSchema(
            user_id=uid,
            items=[detail.content_id],
            count=1,
            generated_at=datetime.now(timezone.utc),
            items_detailed=[detail],
            latency_breakdown={"total_ms": 50},
            profile_snapshot={"interaction_count": 0, "cold_start": True},
            feature_flags={"rerank": False},
        )
        dumped = schema.model_dump(exclude_none=True)
        assert "items_detailed" in dumped
        assert "latency_breakdown" in dumped
        assert "profile_snapshot" in dumped
        assert "feature_flags" in dumped
        assert len(dumped["items_detailed"]) == 1

    def test_items_detailed_none_by_default(self):
        schema = RecommendationsResponseSchema(
            user_id=uuid4(),
            items=[],
            count=0,
            generated_at=datetime.now(timezone.utc),
        )
        assert schema.items_detailed is None
