"""Tests for GetRecommendationsUseCase."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.application.dto.recommendations import RecommendationsRequest, RecommendationsResponse
from src.application.use_cases.get_recommendations import (
    GetRecommendationsUseCase,
    UserProfileNotFoundError,
)
from src.application.dto.generate_feed import GenerateFeedResponse
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


def make_profile(user_id: UUID) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_from_selected(["технологии", "наука", "спорт"], 0.01),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=5,
        created_at=now,
        last_updated=now,
        embedding=None,
    )


def make_feed_response(post_ids: list[UUID]) -> GenerateFeedResponse:
    """Build a GenerateFeedResponse with the given published UUIDs."""
    return GenerateFeedResponse(
        feed=[{"post_id": str(pid), "score": 0.8} for pid in post_ids],
        meta={},
    )


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def mocks(user_id):
    profile = make_profile(user_id)
    generated_ids = [uuid4() for _ in range(10)]
    feed_response = make_feed_response(generated_ids)

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile

    history_repo = AsyncMock()
    history_repo.get_recommended_ids.return_value = set()
    history_repo.save_recommendations.return_value = None

    generate_feed_uc = AsyncMock()
    generate_feed_uc.execute.return_value = feed_response

    return {
        "profile_repo": profile_repo,
        "history_repo": history_repo,
        "generate_feed_uc": generate_feed_uc,
        "profile": profile,
        "generated_ids": generated_ids,
    }


@pytest.fixture
def sut(mocks):
    return GetRecommendationsUseCase(
        generate_feed_uc=mocks["generate_feed_uc"],
        history_repo=mocks["history_repo"],
        profile_repo=mocks["profile_repo"],
    )


@pytest.mark.unit
class TestGetRecommendationsUseCase:
    @pytest.mark.asyncio
    async def test_execute_returns_recommendations_response(self, sut, user_id):
        """execute returns a RecommendationsResponse."""
        request = RecommendationsRequest(user_id=user_id, count=10)
        result = await sut.execute(request)
        assert isinstance(result, RecommendationsResponse)

    @pytest.mark.asyncio
    async def test_items_are_uuids_without_scores(self, sut, user_id, mocks):
        """Response items are UUIDs, not dicts with scores."""
        request = RecommendationsRequest(user_id=user_id, count=10)
        result = await sut.execute(request)
        for item in result.items:
            assert isinstance(item, UUID)

    @pytest.mark.asyncio
    async def test_history_exclusion_removes_seen_items(self, user_id, mocks):
        """Items in recommendation_history are excluded from response."""
        generated_ids = mocks["generated_ids"]
        # Mark first 3 as already seen
        seen = set(generated_ids[:3])
        mocks["history_repo"].get_recommended_ids.return_value = seen

        sut = GetRecommendationsUseCase(
            generate_feed_uc=mocks["generate_feed_uc"],
            history_repo=mocks["history_repo"],
            profile_repo=mocks["profile_repo"],
        )
        request = RecommendationsRequest(user_id=user_id, count=10)
        result = await sut.execute(request)

        for item in result.items:
            assert item not in seen

    @pytest.mark.asyncio
    async def test_no_history_write_at_generation_time(self, sut, user_id, mocks):
        """History is NOT written at generation time — only on actual impressions."""
        request = RecommendationsRequest(user_id=user_id, count=5)
        await sut.execute(request)
        mocks["history_repo"].save_recommendations.assert_not_called()

    @pytest.mark.asyncio
    async def test_history_exhaustion_clears_and_retries(self, user_id, mocks):
        """When all candidates are in history, clear history and retry."""
        generated_ids = mocks["generated_ids"]
        # Mark ALL as already seen
        mocks["history_repo"].get_recommended_ids.return_value = set(generated_ids)
        mocks["history_repo"].delete_for_user.return_value = len(generated_ids)

        sut = GetRecommendationsUseCase(
            generate_feed_uc=mocks["generate_feed_uc"],
            history_repo=mocks["history_repo"],
            profile_repo=mocks["profile_repo"],
        )
        request = RecommendationsRequest(user_id=user_id, count=5)
        result = await sut.execute(request)

        # Should have cleared history
        mocks["history_repo"].delete_for_user.assert_called_once_with(user_id)
        # Retry should return items
        assert len(result.items) > 0

    @pytest.mark.asyncio
    async def test_raises_404_when_profile_not_found(self, mocks, user_id):
        """Raises UserProfileNotFoundError when profile does not exist."""
        mocks["profile_repo"].get_by_user_id.return_value = None
        sut = GetRecommendationsUseCase(
            generate_feed_uc=mocks["generate_feed_uc"],
            history_repo=mocks["history_repo"],
            profile_repo=mocks["profile_repo"],
        )
        request = RecommendationsRequest(user_id=user_id, count=10)
        with pytest.raises(UserProfileNotFoundError):
            await sut.execute(request)

    @pytest.mark.asyncio
    async def test_fewer_candidates_returns_all_available(self, user_id, mocks):
        """If fewer candidates than count, returns all available."""
        only_3 = [uuid4() for _ in range(3)]
        mocks["generate_feed_uc"].execute.return_value = make_feed_response(only_3)
        sut = GetRecommendationsUseCase(
            generate_feed_uc=mocks["generate_feed_uc"],
            history_repo=mocks["history_repo"],
            profile_repo=mocks["profile_repo"],
        )
        request = RecommendationsRequest(user_id=user_id, count=100)
        result = await sut.execute(request)
        assert len(result.items) == 3

    @pytest.mark.asyncio
    async def test_no_duplicate_ids_in_response(self, sut, user_id):
        """No duplicate UUIDs in response items."""
        request = RecommendationsRequest(user_id=user_id, count=10)
        result = await sut.execute(request)
        assert len(result.items) == len(set(result.items))

    @pytest.mark.asyncio
    async def test_generated_at_is_set(self, sut, user_id):
        """generated_at is a valid datetime in response."""
        request = RecommendationsRequest(user_id=user_id, count=5)
        result = await sut.execute(request)
        assert isinstance(result.generated_at, datetime)

    @pytest.mark.asyncio
    async def test_response_user_id_matches_request(self, sut, user_id):
        """Response user_id matches the request user_id."""
        request = RecommendationsRequest(user_id=user_id, count=5)
        result = await sut.execute(request)
        assert result.user_id == user_id

    @pytest.mark.asyncio
    async def test_count_in_response_matches_items_len(self, sut, user_id):
        """Response count equals the number of returned items."""
        request = RecommendationsRequest(user_id=user_id, count=5)
        result = await sut.execute(request)
        assert result.count == len(result.items)
