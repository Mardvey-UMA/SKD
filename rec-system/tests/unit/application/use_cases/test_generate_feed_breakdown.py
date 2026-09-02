"""RED tests for GenerateFeedUseCase include_breakdown flag (P7)."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.application.dto.generate_feed import GenerateFeedRequest, GenerateFeedResponse
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.post_score import PostScore
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector

SCORING_KEYS = {"topic_match", "embedding_sim", "entity_match", "sentiment_match", "freshness", "format_match"}


def _make_profile(user_id: UUID) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_from_selected(["технологии", "наука"], 0.01),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=3,
        created_at=now,
        last_updated=now,
        embedding=None,
    )


def _make_content(post_id=None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id or uuid4(),
        content="test content",
        post_date=datetime.now(timezone.utc),
        topic_1="технологии",
        topic_1_score=0.7,
        processed_at=datetime.now(timezone.utc),
    )


def _make_explain_result() -> dict:
    components = {
        key: {"value": 0.1 * i, "weight": 0.1, "contribution": 0.01 * i}
        for i, key in enumerate(["topic_match", "embedding_sim", "entity_match",
                                  "sentiment_match", "freshness", "format_match"])
    }
    return {
        "final_score": 0.5,
        "components": components,
        "cold_start_applied": False,
        "dead_components": [],
        "redistributed_weights": None,
    }


def _build_sut(user_id: UUID, post_ids: list[UUID], pub_ids: list[UUID]):
    profile = _make_profile(user_id)
    content_items = [_make_content(pid) for pid in post_ids]
    post_scores = [PostScore(post_id=pid, score=0.8 - i * 0.05) for i, pid in enumerate(post_ids)]

    config = {
        "feed_size": 20,
        "freshness_candidate_limit": 500,
        "embedding_candidate_limit": 500,
        "max_age_hours": 48,
        "scoring_weights": {k: 1 / 6 for k in SCORING_KEYS},
        "max_topic_streak": 3,
        "max_topic_ratio": 0.40,
    }

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile

    content_repo = AsyncMock()
    content_repo.get_candidates_by_freshness.return_value = content_items
    content_repo.get_candidates_by_embedding.return_value = []
    content_repo.get_dedup_clusters.side_effect = lambda ids: {pid: i for i, pid in enumerate(ids)}
    content_repo.get_related_pairs.return_value = {}
    pub_map = {pid: pub for pid, pub in zip(post_ids, pub_ids)}
    content_repo.get_published_ids.side_effect = lambda ids: {pid: pub_map.get(pid) for pid in ids if pid in pub_map}
    content_repo.get_by_ids.return_value = content_items

    entity_interest_repo = AsyncMock()
    entity_interest_repo.get_top_for_user.return_value = []

    interaction_repo = AsyncMock()
    interaction_repo.get_unprocessed_for_user.return_value = []

    config_repo = AsyncMock()
    config_repo.get_config.side_effect = lambda key: config if key == "feed" else {}

    scorer = MagicMock()
    scorer.score_batch.return_value = post_scores
    scorer.explain_score.return_value = _make_explain_result()

    diversity_filter = MagicMock()
    diversity_filter.filter.return_value = post_scores

    signal_classifier = MagicMock()
    signal_classifier.classify_batch.return_value = []

    profile_updater = MagicMock()
    profile_updater.apply_batch.return_value = (profile, [])

    blocked_sources_repo = AsyncMock()
    blocked_sources_repo.get_source_ids_for_user.return_value = set()

    narrow_scorer = MagicMock()
    narrow_scorer.score_batch.return_value = post_scores

    sut = GenerateFeedUseCase(
        profile_repo=profile_repo,
        content_repo=content_repo,
        entity_interest_repo=entity_interest_repo,
        interaction_repo=interaction_repo,
        config_repo=config_repo,
        scorer=scorer,
        diversity_filter=diversity_filter,
        signal_classifier=signal_classifier,
        profile_updater=profile_updater,
        blocked_sources_repo=blocked_sources_repo,
        narrow_scorer=narrow_scorer,
    )
    return sut, scorer


@pytest.mark.unit
class TestGenerateFeedBreakdown:
    @pytest.mark.asyncio
    async def test_no_breakdown_by_default(self):
        """include_breakdown absent/False → items_detailed, latency_breakdown, etc. are None."""
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(3)]
        pub_ids = [uuid4() for _ in range(3)]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)

        assert result.items_detailed is None
        assert result.latency_breakdown is None
        assert result.profile_snapshot is None
        assert result.feature_flags is None

    @pytest.mark.asyncio
    async def test_breakdown_false_explicit(self):
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(2)]
        pub_ids = [uuid4() for _ in range(2)]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=False)
        result = await sut.execute(request)

        assert result.items_detailed is None

    @pytest.mark.asyncio
    async def test_breakdown_true_populates_items_detailed(self):
        """include_breakdown=True → items_detailed has same length as feed."""
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(3)]
        pub_ids = [uuid4() for _ in range(3)]
        sut, scorer = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert result.items_detailed is not None
        assert len(result.items_detailed) == len(result.feed)

    @pytest.mark.asyncio
    async def test_breakdown_scoring_components_has_six_keys(self):
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(2)]
        pub_ids = [uuid4() for _ in range(2)]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert result.items_detailed is not None
        for item in result.items_detailed:
            assert set(item["scoring_components"].keys()) == SCORING_KEYS

    @pytest.mark.asyncio
    async def test_breakdown_latency_has_total_ms(self):
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(2)]
        pub_ids = [uuid4() for _ in range(2)]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert result.latency_breakdown is not None
        assert "total_ms" in result.latency_breakdown
        assert result.latency_breakdown["total_ms"] >= 0

    @pytest.mark.asyncio
    async def test_breakdown_profile_snapshot_fields(self):
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(2)]
        pub_ids = [uuid4() for _ in range(2)]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert result.profile_snapshot is not None
        assert "interaction_count" in result.profile_snapshot
        assert "cold_start" in result.profile_snapshot

    @pytest.mark.asyncio
    async def test_breakdown_feature_flags_present(self):
        user_id = uuid4()
        post_ids = [uuid4() for _ in range(2)]
        pub_ids = [uuid4() for _ in range(2)]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert result.feature_flags is not None
        assert "live_profile" in result.feature_flags
        assert "rerank" in result.feature_flags

    @pytest.mark.asyncio
    async def test_breakdown_item_has_content_id_and_raw_content_id(self):
        user_id = uuid4()
        post_ids = [uuid4()]
        pub_ids = [uuid4()]
        sut, _ = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert result.items_detailed is not None
        assert len(result.items_detailed) >= 1
        item = result.items_detailed[0]
        assert "content_id" in item
        assert "raw_content_id" in item
        assert "final_score" in item

    @pytest.mark.asyncio
    async def test_scorer_explain_score_called_per_filtered_item(self):
        """explain_score must be called once per item in filtered output."""
        user_id = uuid4()
        post_ids = [uuid4(), uuid4()]
        pub_ids = [uuid4(), uuid4()]
        sut, scorer = _build_sut(user_id, post_ids, pub_ids)

        request = GenerateFeedRequest(user_id=user_id, feed_size=20, include_breakdown=True)
        result = await sut.execute(request)

        assert scorer.explain_score.call_count == len(result.feed)
