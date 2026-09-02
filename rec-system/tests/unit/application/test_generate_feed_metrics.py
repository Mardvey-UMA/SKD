"""Tests verifying that GenerateFeedUseCase instruments Prometheus metrics."""
from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

import pytest
from prometheus_client import REGISTRY

from src.application.dto.generate_feed import GenerateFeedRequest
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.post_score import PostScore
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector


def _sample_value(sample_name: str, labels: dict | None = None) -> float:
    """Read current sample value by sample name (not family name)."""
    labels = labels or {}
    for metric in REGISTRY.collect():
        for sample in metric.samples:
            if sample.name == sample_name and sample.labels == labels:
                return sample.value
    return 0.0


def _make_profile(user_id: UUID, embedding=None) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_from_selected(["technology"], 0.01),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=0,
        created_at=now,
        last_updated=now,
        embedding=embedding,
    )


def _make_content(post_id=None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id or uuid4(),
        content="content",
        post_date=datetime.now(timezone.utc),
        topic_1="technology",
        topic_1_score=0.8,
        processed_at=datetime.now(timezone.utc),
    )


def _build_sut(user_id: UUID, has_profile: bool = True):
    profile = _make_profile(user_id, embedding=[0.1, 0.2, 0.3]) if has_profile else None
    content_items = [_make_content() for _ in range(3)]
    post_scores = [PostScore(post_id=c.post_id, score=0.9) for c in content_items]
    config = {
        "feed_size": 10,
        "freshness_candidate_limit": 500,
        "embedding_candidate_limit": 500,
        "max_age_hours": 48,
        "scoring_weights": {},
    }

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile

    content_repo = AsyncMock()
    content_repo.get_candidates_by_freshness.return_value = content_items
    content_repo.get_candidates_by_embedding.return_value = []
    content_repo.get_dedup_clusters.side_effect = (
        lambda post_ids: {pid: i for i, pid in enumerate(post_ids)}
    )
    content_repo.get_related_pairs.return_value = {}
    content_repo.get_published_ids.side_effect = (
        lambda post_ids: {pid: uuid4() for pid in post_ids}
    )

    entity_interest_repo = AsyncMock()
    entity_interest_repo.get_top_for_user.return_value = []

    interaction_repo = AsyncMock()
    interaction_repo.get_unprocessed_for_user.return_value = []

    config_repo = AsyncMock()

    async def _get_config(key):
        return config if key == "feed" else {}

    config_repo.get_config.side_effect = _get_config

    scorer = MagicMock()
    scorer.score_batch.return_value = post_scores

    narrow_scorer = MagicMock()
    narrow_scorer.score_batch.return_value = post_scores

    diversity_filter = MagicMock()
    diversity_filter.filter.return_value = post_scores

    signal_classifier = MagicMock()
    signal_classifier.classify_batch.return_value = []

    profile_updater = MagicMock()
    profile_updater.apply_batch.return_value = (profile, [])

    blocked_sources_repo = AsyncMock()
    blocked_sources_repo.get_source_ids_for_user.return_value = set()

    use_case = GenerateFeedUseCase(
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
    return use_case


@pytest.mark.unit
class TestGenerateFeedMetrics:

    async def test_personalized_counter_increments_on_successful_execute(self):
        user_id = uuid4()
        use_case = _build_sut(user_id, has_profile=True)

        before = _sample_value("rec_feed_requests_total", {"source": "personalized"})
        await use_case.execute(GenerateFeedRequest(user_id=user_id, feed_size=10))
        after = _sample_value("rec_feed_requests_total", {"source": "personalized"})

        assert after - before == pytest.approx(1.0)

    async def test_latency_histogram_count_increments_on_execute(self):
        user_id = uuid4()
        use_case = _build_sut(user_id, has_profile=True)

        before = _sample_value("rec_feed_request_latency_ms_count", {"source": "personalized"})
        await use_case.execute(GenerateFeedRequest(user_id=user_id, feed_size=10))
        after = _sample_value("rec_feed_request_latency_ms_count", {"source": "personalized"})

        assert after - before == pytest.approx(1.0)

    async def test_narrow_counter_increments_when_include_source_ids_set(self):
        user_id = uuid4()
        use_case = _build_sut(user_id, has_profile=True)

        # Make content_repo return items for narrow path
        source_id = uuid4()
        before = _sample_value("rec_feed_requests_total", {"source": "narrow"})
        await use_case.execute(
            GenerateFeedRequest(user_id=user_id, feed_size=10, include_source_ids=[source_id])
        )
        after = _sample_value("rec_feed_requests_total", {"source": "narrow"})

        assert after - before == pytest.approx(1.0)

    async def test_counter_increments_even_on_empty_feed(self):
        """Metrics are recorded even when no profile exists (early return path)."""
        user_id = uuid4()
        use_case = _build_sut(user_id, has_profile=False)

        before = _sample_value("rec_feed_requests_total", {"source": "personalized"})
        await use_case.execute(GenerateFeedRequest(user_id=user_id, feed_size=10))
        after = _sample_value("rec_feed_requests_total", {"source": "personalized"})

        assert after - before == pytest.approx(1.0)
