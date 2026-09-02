"""Tests for updated OnboardUserUseCase (categories, source_content_ids, Kafka event)."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.application.dto.onboard import OnboardRequest, OnboardResponse
from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector


def make_profile(user_id: UUID, cold_start: bool = True) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_uniform(),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=0,
        created_at=now,
        last_updated=now,
        embedding=None,
        cold_start=cold_start,
    )


def make_content(post_id: UUID | None = None, embedding: list[float] | None = None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id or uuid4(),
        content="test content",
        post_date=datetime.now(timezone.utc),
        embedding=embedding or [0.1] * 312,
    )


VALID_CATEGORIES = ["технологии", "наука", "бизнес"]


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def mocks(user_id):
    profile = make_profile(user_id, cold_start=True)
    warm_profile = make_profile(user_id, cold_start=False)

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile
    profile_repo.save.return_value = None

    config_repo = AsyncMock()
    config_repo.get_config.return_value = {
        "min_topics": 3,
        "max_topics": 10,
        "baseline_weight": 0.01,
    }

    category_repo = AsyncMock()
    category_repo.get_category_ids.return_value = [
        "технологии", "наука", "бизнес", "спорт", "культура",
        "политика", "экономика", "общество", "здоровье", "образование",
        "происшествия", "международные новости", "финансы", "развлечения",
        "криминал", "армия", "природа", "транспорт",
    ]

    content_repo = AsyncMock()
    content_repo.get_by_ids.return_value = []

    onboarding_service = MagicMock()
    onboarding_service.create_profile.return_value = warm_profile

    event_publisher = AsyncMock()
    event_publisher.publish_recommendations_updated.return_value = None

    return {
        "profile_repo": profile_repo,
        "config_repo": config_repo,
        "category_repo": category_repo,
        "content_repo": content_repo,
        "onboarding_service": onboarding_service,
        "event_publisher": event_publisher,
        "profile": profile,
        "warm_profile": warm_profile,
    }


@pytest.fixture
def sut(mocks):
    return OnboardUserUseCase(
        profile_repo=mocks["profile_repo"],
        config_repo=mocks["config_repo"],
        category_repo=mocks["category_repo"],
        content_repo=mocks["content_repo"],
        onboarding_service=mocks["onboarding_service"],
        event_publisher=mocks["event_publisher"],
    )


@pytest.mark.unit
class TestOnboardUserUseCaseUpdated:
    @pytest.mark.asyncio
    async def test_execute_with_valid_categories_creates_profile(self, sut, user_id, mocks):
        """Valid categories create a warm profile."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        result = await sut.execute(request)
        mocks["onboarding_service"].create_profile.assert_called_once()

    @pytest.mark.asyncio
    async def test_response_has_user_id_and_profile_initialized(self, sut, user_id):
        """Response has user_id and profile_initialized=True."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        result = await sut.execute(request)
        assert isinstance(result, OnboardResponse)
        assert result.user_id == user_id
        assert result.profile_initialized is True

    @pytest.mark.asyncio
    async def test_invalid_category_raises_validation_error(self, sut, user_id):
        """Invalid category ID raises ValueError."""
        request = OnboardRequest(user_id=user_id, categories=["invalid_cat", "наука", "бизнес"])
        with pytest.raises(ValueError):
            await sut.execute(request)

    @pytest.mark.asyncio
    async def test_source_content_ids_triggers_embedding_warmup(self, sut, user_id, mocks):
        """When source_content_ids provided, get_by_ids is called."""
        source_ids = [uuid4(), uuid4()]
        embeddings = [[0.1] * 312, [0.2] * 312]
        mocks["content_repo"].get_by_ids.return_value = [
            make_content(source_ids[0], embeddings[0]),
            make_content(source_ids[1], embeddings[1]),
        ]
        request = OnboardRequest(
            user_id=user_id,
            categories=VALID_CATEGORIES,
            source_content_ids=source_ids,
        )
        await sut.execute(request)
        mocks["content_repo"].get_by_ids.assert_called_once_with(source_ids)

    @pytest.mark.asyncio
    async def test_source_content_ids_none_skips_embedding_warmup(self, sut, user_id, mocks):
        """When source_content_ids=None, get_by_ids is not called."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES, source_content_ids=None)
        await sut.execute(request)
        mocks["content_repo"].get_by_ids.assert_not_called()

    @pytest.mark.asyncio
    async def test_cold_start_set_to_false_after_onboarding(self, sut, user_id, mocks):
        """Profile cold_start is set to False after successful onboarding."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        await sut.execute(request)
        # The saved profile should have cold_start=False
        saved_profile: UserProfile = mocks["profile_repo"].save.call_args[0][0]
        assert saved_profile.cold_start is False

    @pytest.mark.asyncio
    async def test_recommendations_updated_event_published(self, sut, user_id, mocks):
        """recommendations.updated event published with reason onboarding_complete."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        await sut.execute(request)
        mocks["event_publisher"].publish_recommendations_updated.assert_called_once_with(
            user_id=str(user_id),
            reason="onboarding_complete",
        )

    @pytest.mark.asyncio
    async def test_proceeds_even_when_profile_repo_returns_none(self, mocks, user_id):
        """Use case no longer checks for profile existence — that is the dependency's job.

        The ensure_user_profile_exists dependency guarantees the profile row exists
        before the use case executes, so OnboardUserUseCase does NOT raise when
        get_by_user_id returns None here.
        """
        mocks["profile_repo"].get_by_user_id.return_value = None
        sut = OnboardUserUseCase(
            profile_repo=mocks["profile_repo"],
            config_repo=mocks["config_repo"],
            category_repo=mocks["category_repo"],
            content_repo=mocks["content_repo"],
            onboarding_service=mocks["onboarding_service"],
            event_publisher=mocks["event_publisher"],
        )
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        # Must succeed — profile existence is guaranteed by ensure_user_profile_exists
        result = await sut.execute(request)
        assert result.profile_initialized is True

    @pytest.mark.asyncio
    async def test_min_3_categories_required(self, sut, user_id, mocks):
        """Fewer than 3 categories raises validation error."""
        request = OnboardRequest(user_id=user_id, categories=["технологии", "наука"])
        with pytest.raises(ValueError):
            await sut.execute(request)

    @pytest.mark.asyncio
    async def test_max_10_source_content_ids(self, sut, user_id, mocks):
        """More than 10 source_content_ids raises validation error."""
        too_many_ids = [uuid4() for _ in range(11)]
        request = OnboardRequest(
            user_id=user_id,
            categories=VALID_CATEGORIES,
            source_content_ids=too_many_ids,
        )
        with pytest.raises(ValueError):
            await sut.execute(request)
