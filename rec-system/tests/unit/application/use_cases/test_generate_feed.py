"""Tests for GenerateFeedUseCase."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import UUID, uuid4

from src.application.dto.generate_feed import GenerateFeedRequest, GenerateFeedResponse
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_interaction import UserInteraction
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.event_type import EventType
from src.domain.value_objects.post_score import PostScore
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


def make_profile(user_id: UUID, embedding=None) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_from_selected(["technology", "science", "health"], 0.01),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=5,
        created_at=now,
        last_updated=now,
        embedding=embedding,
    )


def make_content(post_id=None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="some content",
        post_date=datetime.now(timezone.utc),
        topic_1="technology",
        topic_1_score=0.8,
        processed_at=datetime.now(timezone.utc),
    )


def make_interaction(user_id: str, post_id: UUID = None) -> UserInteraction:
    return UserInteraction(
        id=1,
        event_id=uuid4(),
        user_id=user_id,
        post_id=post_id if post_id is not None else uuid4(),
        event_type="LIKE",
        duration_ms=None,
        scroll_pct=None,
        max_scroll_pct=None,
        created_at=datetime.now(timezone.utc),
        processed=False,
    )


def make_config() -> dict:
    return {
        "feed_size": 20,
        "freshness_candidate_limit": 500,
        "embedding_candidate_limit": 500,
        "max_age_hours": 48,
        "scoring_weights": {
            "topic_match": 0.30,
            "embedding_sim": 0.25,
            "entity_match": 0.15,
            "sentiment_match": 0.05,
            "freshness": 0.15,
            "format_match": 0.10,
        },
        "max_topic_streak": 3,
        "max_topic_ratio": 0.40,
        "entity_decay_factor": 0.9,
        "entity_cleanup_days": 30,
        "signal_weights": {
            "like": {"weight": 1.0},
            "dislike": {"weight": -1.0},
            "bookmark": {"weight": 1.5},
            "impression_read": {"weight": 0.5, "threshold_duration_ms": 5000},
            "impression_skip": {"weight": -0.1},
            "open": {"weight": 0.3},
            "close_fast": {"weight": -0.5, "threshold_duration_ms": 2000},
            "close_full": {"weight": 1.0, "threshold_scroll_pct": 0.8, "threshold_duration_ms": 10000},
            "close_half": {"weight": 0.5, "threshold_scroll_pct": 0.5, "threshold_duration_ms": 5000},
            "close_other": {"weight": 0.1},
            "orphan_timeout_minutes": 5,
        },
    }


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def config():
    return make_config()


@pytest.fixture
def mocks(user_id, config):
    profile = make_profile(user_id, embedding=[0.1, 0.2, 0.3])
    content_items = [make_content() for _ in range(5)]
    post_scores = [PostScore(post_id=c.post_id, score=0.8 - i * 0.05) for i, c in enumerate(content_items)]
    interaction = make_interaction(str(user_id), uuid4())

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile

    content_repo = AsyncMock()
    content_repo.get_candidates_by_freshness.return_value = content_items
    content_repo.get_candidates_by_embedding.return_value = content_items
    content_repo.get_candidates_by_sources.return_value = content_items
    # Each post maps to a unique cluster (no dedup by default)
    content_repo.get_dedup_clusters.side_effect = lambda post_ids: {pid: i for i, pid in enumerate(post_ids)}
    content_repo.get_related_pairs.return_value = {}
    # All posts get published IDs by default
    content_repo.get_published_ids.side_effect = lambda post_ids: {pid: uuid4() for pid in post_ids}

    entity_interest_repo = AsyncMock()
    entity_interest_repo.get_top_for_user.return_value = []

    interaction_repo = AsyncMock()
    interaction_repo.get_unprocessed_for_user.return_value = [interaction]

    config_repo = AsyncMock()

    async def get_config_se(key):
        if key == "feed":
            return config
        return {}

    config_repo.get_config.side_effect = get_config_se

    scorer = MagicMock()
    scorer.score_batch.return_value = post_scores

    narrow_scorer = MagicMock()
    narrow_scorer.score_batch.return_value = post_scores

    diversity_filter = MagicMock()
    diversity_filter.filter.return_value = post_scores[:config["feed_size"]]

    signal_classifier = MagicMock()
    signal_classifier.classify_batch.return_value = []

    profile_updater = MagicMock()
    profile_updater.apply_batch.return_value = (profile, [])

    blocked_sources_repo = AsyncMock()
    blocked_sources_repo.get_source_ids_for_user.return_value = set()

    return {
        "profile_repo": profile_repo,
        "content_repo": content_repo,
        "entity_interest_repo": entity_interest_repo,
        "interaction_repo": interaction_repo,
        "config_repo": config_repo,
        "scorer": scorer,
        "narrow_scorer": narrow_scorer,
        "diversity_filter": diversity_filter,
        "signal_classifier": signal_classifier,
        "profile_updater": profile_updater,
        "blocked_sources_repo": blocked_sources_repo,
        "profile": profile,
        "content_items": content_items,
        "post_scores": post_scores,
    }


@pytest.fixture
def sut(mocks):
    return GenerateFeedUseCase(
        profile_repo=mocks["profile_repo"],
        content_repo=mocks["content_repo"],
        entity_interest_repo=mocks["entity_interest_repo"],
        interaction_repo=mocks["interaction_repo"],
        config_repo=mocks["config_repo"],
        scorer=mocks["scorer"],
        diversity_filter=mocks["diversity_filter"],
        signal_classifier=mocks["signal_classifier"],
        profile_updater=mocks["profile_updater"],
        blocked_sources_repo=mocks["blocked_sources_repo"],
        narrow_scorer=mocks["narrow_scorer"],
    )


@pytest.mark.unit
class TestGenerateFeedUseCase:
    @pytest.mark.asyncio
    async def test_execute_returns_feed_response(self, sut, user_id, mocks):
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)
        assert isinstance(result, GenerateFeedResponse)
        assert isinstance(result.feed, list)
        assert isinstance(result.meta, dict)

    @pytest.mark.asyncio
    async def test_processes_unprocessed_events_first(self, sut, user_id, mocks):
        """Fetches unprocessed interactions and processes them before generating feed."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["interaction_repo"].get_unprocessed_for_user.assert_called_once_with(user_id)

    @pytest.mark.asyncio
    async def test_loads_profile_and_config(self, sut, user_id, mocks):
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["profile_repo"].get_by_user_id.assert_called_with(user_id)
        mocks["config_repo"].get_config.assert_called()

    @pytest.mark.asyncio
    async def test_two_stage_candidate_retrieval(self, sut, user_id, mocks, config):
        """Calls both freshness and embedding retrieval when profile has embedding."""
        profile = make_profile(user_id, embedding=[0.1, 0.2, 0.3])
        mocks["profile_repo"].get_by_user_id.return_value = profile
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["content_repo"].get_candidates_by_freshness.assert_called_once()
        mocks["content_repo"].get_candidates_by_embedding.assert_called_once()

    @pytest.mark.asyncio
    async def test_skips_embedding_stage_when_null(self, sut, user_id, mocks):
        """When profile.embedding is None, only freshness retrieval is used."""
        profile_no_embedding = make_profile(user_id, embedding=None)
        mocks["profile_repo"].get_by_user_id.return_value = profile_no_embedding
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["content_repo"].get_candidates_by_freshness.assert_called_once()
        mocks["content_repo"].get_candidates_by_embedding.assert_not_called()

    @pytest.mark.asyncio
    async def test_deduplicates_candidates(self, sut, user_id, mocks):
        """Same post_id from both stages appears only once in scoring input."""
        shared_pid = uuid4()
        shared_content = make_content(post_id=shared_pid)
        mocks["content_repo"].get_candidates_by_freshness.return_value = [shared_content, make_content()]
        mocks["content_repo"].get_candidates_by_embedding.return_value = [shared_content, make_content()]
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        # scorer should get unique candidates
        scored_candidates = mocks["scorer"].score_batch.call_args[0][0]
        post_ids = [cf.post_id for cf, _ in scored_candidates]
        assert len(post_ids) == len(set(post_ids))

    @pytest.mark.asyncio
    async def test_scores_candidates(self, sut, user_id, mocks):
        """Scorer.score_batch is called with candidates and profile."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["scorer"].score_batch.assert_called_once()

    @pytest.mark.asyncio
    async def test_applies_diversity_filter(self, sut, user_id, mocks):
        """DiversityFilter.filter is called after scoring."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["diversity_filter"].filter.assert_called_once()

    @pytest.mark.asyncio
    async def test_returns_feed_meta(self, sut, user_id, mocks):
        """Meta includes total, candidates_scored, profile_events_processed, generation_time_ms."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)
        meta = result.meta
        assert "total" in meta
        assert "candidates_scored" in meta
        assert "profile_events_processed" in meta
        assert "generation_time_ms" in meta

    @pytest.mark.asyncio
    async def test_respects_feed_size(self, sut, user_id, mocks):
        """Feed output is limited to feed_size."""
        content_items = [make_content() for _ in range(1, 30)]
        post_scores = [PostScore(post_id=c.post_id, score=0.9) for c in content_items]
        mocks["content_repo"].get_candidates_by_freshness.return_value = content_items
        mocks["scorer"].score_batch.return_value = post_scores
        # Return exactly feed_size items from diversity filter
        mocks["diversity_filter"].filter.return_value = post_scores[:10]
        request = GenerateFeedRequest(user_id=user_id, feed_size=10)
        result = await sut.execute(request)
        assert len(result.feed) <= 10

    @pytest.mark.asyncio
    async def test_empty_candidates_returns_empty_feed(self, sut, user_id, mocks):
        """No candidates -> empty feed list."""
        mocks["content_repo"].get_candidates_by_freshness.return_value = []
        mocks["content_repo"].get_candidates_by_embedding.return_value = []
        mocks["diversity_filter"].filter.return_value = []
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)
        assert result.feed == []

    @pytest.mark.asyncio
    async def test_inline_update_calls_get_by_ids_with_signal_post_ids(self, sut, user_id, mocks):
        """_process_user_events_inline calls content_repo.get_by_ids with post_ids from signals."""
        post_id = uuid4()
        signal = Signal(post_id=post_id, user_id=str(user_id), weight=1.0, event_type=EventType.LIKE)
        mocks["signal_classifier"].classify_batch.return_value = [signal]
        mocks["content_repo"].get_by_ids = AsyncMock(return_value=[])

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        mocks["content_repo"].get_by_ids.assert_called_once_with([post_id])

    @pytest.mark.asyncio
    async def test_inline_update_passes_content_features_map_to_apply_batch(self, sut, user_id, mocks):
        """_process_user_events_inline passes non-empty content_features_map to profile_updater.apply_batch."""
        post_id = uuid4()
        signal = Signal(post_id=post_id, user_id=str(user_id), weight=1.0, event_type=EventType.LIKE)
        content = make_content(post_id=post_id)
        mocks["signal_classifier"].classify_batch.return_value = [signal]
        mocks["content_repo"].get_by_ids = AsyncMock(return_value=[content])

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        call_kwargs = mocks["profile_updater"].apply_batch.call_args.kwargs
        assert call_kwargs["content_features_map"] == {post_id: content}

    # --- Dedup clustering tests ---

    @pytest.mark.asyncio
    async def test_dedup_clustering_called_when_enabled(self, sut, user_id, mocks):
        """get_dedup_clusters is called when enable_dedup_clustering defaults to True."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["content_repo"].get_dedup_clusters.assert_called_once()

    @pytest.mark.asyncio
    async def test_dedup_clustering_not_called_when_disabled(self, sut, user_id, mocks, config):
        """get_dedup_clusters is NOT called when enable_dedup_clustering=False."""
        async def get_config_disabled(key):
            if key == "feed":
                return config
            return {"enable_dedup_clustering": False, "enable_related_spacing": False}

        mocks["config_repo"].get_config.side_effect = get_config_disabled
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["content_repo"].get_dedup_clusters.assert_not_called()

    @pytest.mark.asyncio
    async def test_dedup_clustering_removes_duplicates(self, sut, user_id, mocks, config):
        """Posts sharing a dedup cluster are reduced to the freshest one."""
        from datetime import timedelta

        now = datetime.now(timezone.utc)
        pid_older = uuid4()
        pid_newer = uuid4()
        pid_unique = uuid4()

        older = ContentFeatures(
            post_id=pid_older, content="c", post_date=now - timedelta(hours=2), topic_1="technology"
        )
        newer = ContentFeatures(
            post_id=pid_newer, content="c", post_date=now, topic_1="technology"
        )
        unique = ContentFeatures(
            post_id=pid_unique, content="c", post_date=now - timedelta(hours=1), topic_1="science"
        )

        mocks["content_repo"].get_candidates_by_freshness.return_value = [older, newer, unique]
        mocks["content_repo"].get_candidates_by_embedding.return_value = []
        # pid_older and pid_newer share cluster 0; pid_unique is cluster 1
        mocks["content_repo"].get_dedup_clusters.side_effect = lambda post_ids: {
            pid_older: 0, pid_newer: 0, pid_unique: 1
        }

        profile_no_embedding = make_profile(user_id, embedding=None)
        mocks["profile_repo"].get_by_user_id.return_value = profile_no_embedding

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        scored_candidates = mocks["scorer"].score_batch.call_args[0][0]
        post_ids_scored = [cf.post_id for cf, _ in scored_candidates]
        assert len(post_ids_scored) == 2
        assert pid_newer in post_ids_scored
        assert pid_older not in post_ids_scored
        assert pid_unique in post_ids_scored

    @pytest.mark.asyncio
    async def test_dedup_clustering_backward_compat_empty_config(self, sut, user_id, mocks):
        """When dedup config is empty dict, defaults are used (enable_dedup=True)."""
        # mocks already return empty dedup config by default → get_dedup_clusters should be called
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["content_repo"].get_dedup_clusters.assert_called_once()

    # --- Related pairs tests ---

    @pytest.mark.asyncio
    async def test_related_pairs_passed_to_diversity_filter(self, sut, user_id, mocks):
        """get_related_pairs result is passed as related_map to diversity_filter.filter."""
        pid_a = uuid4()
        pid_b = uuid4()
        related = {pid_a: {pid_b}, pid_b: {pid_a}}
        mocks["content_repo"].get_related_pairs.return_value = related

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        call_kwargs = mocks["diversity_filter"].filter.call_args.kwargs
        assert call_kwargs.get("related_map") == related

    @pytest.mark.asyncio
    async def test_related_spacing_not_called_when_disabled(self, sut, user_id, mocks, config):
        """When enable_related_spacing=False, get_related_pairs is not called and related_map=None."""
        async def get_config_disabled(key):
            if key == "feed":
                return config
            return {"enable_dedup_clustering": True, "enable_related_spacing": False}

        mocks["config_repo"].get_config.side_effect = get_config_disabled

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        mocks["content_repo"].get_related_pairs.assert_not_called()
        call_kwargs = mocks["diversity_filter"].filter.call_args.kwargs
        assert call_kwargs.get("related_map") is None

    # --- Published ID mapping tests ---

    @pytest.mark.asyncio
    async def test_published_ids_called_after_diversity_filter(self, sut, user_id, mocks):
        """get_published_ids is called with the post_ids from the diversity-filtered feed."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["content_repo"].get_published_ids.assert_called_once()

    @pytest.mark.asyncio
    async def test_posts_without_published_mapping_excluded(self, sut, user_id, mocks):
        """Posts whose post_id has no published_content entry are excluded from the feed."""
        pid_mapped = uuid4()
        pid_unmapped = uuid4()
        pub_uuid = uuid4()

        scores = [
            PostScore(post_id=pid_mapped, score=0.9),
            PostScore(post_id=pid_unmapped, score=0.8),
        ]
        mocks["diversity_filter"].filter.return_value = scores
        # Only pid_mapped has a published ID
        mocks["content_repo"].get_published_ids.side_effect = lambda post_ids: {
            pid_mapped: pub_uuid
        }

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)

        assert len(result.feed) == 1
        assert result.feed[0]["post_id"] == str(pub_uuid)

    @pytest.mark.asyncio
    async def test_meta_includes_unmapped_posts(self, sut, user_id, mocks):
        """Meta dict includes unmapped_posts count for observability."""
        pid_mapped = uuid4()
        pid_unmapped = uuid4()

        scores = [
            PostScore(post_id=pid_mapped, score=0.9),
            PostScore(post_id=pid_unmapped, score=0.8),
        ]
        mocks["diversity_filter"].filter.return_value = scores
        mocks["content_repo"].get_published_ids.side_effect = lambda post_ids: {
            pid_mapped: uuid4()
        }

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)

        assert "unmapped_posts" in result.meta
        assert result.meta["unmapped_posts"] == 1

    @pytest.mark.asyncio
    async def test_feed_post_ids_are_published_content_uuids(self, sut, user_id, mocks):
        """Feed response post_id values are published_content.id strings, not raw post_ids."""
        raw_pid = uuid4()
        pub_uuid = uuid4()

        scores = [PostScore(post_id=raw_pid, score=0.9)]
        mocks["diversity_filter"].filter.return_value = scores
        mocks["content_repo"].get_published_ids.side_effect = lambda post_ids: {
            raw_pid: pub_uuid
        }

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        result = await sut.execute(request)

        assert len(result.feed) == 1
        assert result.feed[0]["post_id"] == str(pub_uuid)
        assert result.feed[0]["post_id"] != str(raw_pid)

    # --- Phase 1 user-sources: blacklist merge, include/exclude, NARROW mode ---

    @pytest.mark.asyncio
    async def test_blacklist_is_fetched_for_user(self, sut, user_id, mocks):
        """GenerateFeedUseCase fetches blocked source_ids for the caller."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)
        mocks["blocked_sources_repo"].get_source_ids_for_user.assert_called_once_with(user_id)

    @pytest.mark.asyncio
    async def test_blacklist_passed_to_freshness_as_exclude(self, sut, user_id, mocks):
        """Blacklist flows into get_candidates_by_freshness.exclude_source_ids."""
        blocked = uuid4()
        mocks["blocked_sources_repo"].get_source_ids_for_user.return_value = {blocked}

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        call_kwargs = mocks["content_repo"].get_candidates_by_freshness.call_args.kwargs
        exclude = set(call_kwargs.get("exclude_source_ids") or [])
        assert blocked in exclude

    @pytest.mark.asyncio
    async def test_request_exclude_unioned_with_blacklist(self, sut, user_id, mocks):
        """effective_exclude = request.exclude_source_ids ∪ blacklist."""
        blocked = uuid4()
        request_excl = uuid4()
        mocks["blocked_sources_repo"].get_source_ids_for_user.return_value = {blocked}

        request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=20,
            exclude_source_ids=[request_excl],
        )
        await sut.execute(request)

        call_kwargs = mocks["content_repo"].get_candidates_by_freshness.call_args.kwargs
        exclude = set(call_kwargs.get("exclude_source_ids") or [])
        assert blocked in exclude
        assert request_excl in exclude

    @pytest.mark.asyncio
    async def test_include_source_ids_routes_to_narrow_candidate_query(self, sut, user_id, mocks):
        """When include_source_ids is non-empty, narrow path is taken."""
        include = [uuid4(), uuid4()]
        request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=20,
            include_source_ids=include,
        )
        await sut.execute(request)

        mocks["content_repo"].get_candidates_by_sources.assert_called_once()
        # When narrow path is used, the two-stage retrieval is bypassed
        mocks["content_repo"].get_candidates_by_freshness.assert_not_called()
        mocks["content_repo"].get_candidates_by_embedding.assert_not_called()

    @pytest.mark.asyncio
    async def test_exclude_wins_over_include(self, sut, user_id, mocks):
        """Source in both include and exclude is removed (exclude wins)."""
        shared = uuid4()
        include = [shared, uuid4()]
        mocks["blocked_sources_repo"].get_source_ids_for_user.return_value = {shared}

        request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=20,
            include_source_ids=include,
        )
        await sut.execute(request)

        call_kwargs = mocks["content_repo"].get_candidates_by_sources.call_args.kwargs
        exclude = set(call_kwargs.get("exclude_source_ids") or [])
        assert shared in exclude

    @pytest.mark.asyncio
    async def test_narrow_mode_uses_narrow_scorer(self, sut, user_id, mocks):
        """ranking_mode=NARROW → NarrowScorer.score_batch is called; Scorer is not."""
        include = [uuid4()]
        request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=20,
            include_source_ids=include,
            ranking_mode="NARROW",
        )
        await sut.execute(request)

        mocks["narrow_scorer"].score_batch.assert_called_once()
        mocks["scorer"].score_batch.assert_not_called()

    @pytest.mark.asyncio
    async def test_narrow_mode_bypasses_diversity_filter(self, sut, user_id, mocks):
        """ranking_mode=NARROW → DiversityFilter is NOT invoked."""
        include = [uuid4()]
        request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=20,
            include_source_ids=include,
            ranking_mode="NARROW",
        )
        await sut.execute(request)

        mocks["diversity_filter"].filter.assert_not_called()

    @pytest.mark.asyncio
    async def test_narrow_mode_keeps_dedup_clustering(self, sut, user_id, mocks):
        """ranking_mode=NARROW still runs dedup clustering."""
        include = [uuid4()]
        request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=20,
            include_source_ids=include,
            ranking_mode="NARROW",
        )
        await sut.execute(request)

        mocks["content_repo"].get_dedup_clusters.assert_called_once()

    @pytest.mark.asyncio
    async def test_normal_mode_default_still_uses_scorer(self, sut, user_id, mocks):
        """When ranking_mode is omitted (NORMAL), the 6-component Scorer is used."""
        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        await sut.execute(request)

        mocks["scorer"].score_batch.assert_called_once()
        mocks["narrow_scorer"].score_batch.assert_not_called()
