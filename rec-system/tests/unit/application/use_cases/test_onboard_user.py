"""Tests for OnboardUserUseCase (legacy tests updated to use categories field)."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.application.dto.onboard import OnboardRequest, OnboardResponse
from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs

VALID_CATEGORIES = ["технологии", "наука", "здоровье"]


def make_profile(user_id: UUID) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_from_selected(VALID_CATEGORIES, 0.01),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=0,
        created_at=now,
        last_updated=now,
        embedding=None,
    )


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def mocks(user_id):
    profile = make_profile(user_id)
    config = {
        "min_topics": 3,
        "max_topics": 10,
        "baseline_weight": 0.01,
    }

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile
    profile_repo.save.return_value = None

    config_repo = AsyncMock()
    config_repo.get_config.return_value = config

    category_repo = AsyncMock()
    category_repo.get_category_ids.return_value = [
        "технологии", "наука", "здоровье", "спорт", "культура",
        "политика", "экономика", "общество", "образование", "бизнес",
        "происшествия", "международные новости", "финансы", "развлечения",
        "криминал", "армия", "природа", "транспорт",
    ]

    content_repo = AsyncMock()
    content_repo.get_by_ids.return_value = []

    onboarding_service = MagicMock()
    onboarding_service.create_profile.return_value = profile

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
        "config": config,
    }


@pytest.fixture
def sut(mocks):
    return OnboardUserUseCase(
        profile_repo=mocks["profile_repo"],
        config_repo=mocks["config_repo"],
        onboarding_service=mocks["onboarding_service"],
        category_repo=mocks["category_repo"],
        content_repo=mocks["content_repo"],
        event_publisher=mocks["event_publisher"],
    )


@pytest.mark.unit
class TestOnboardUserUseCase:
    @pytest.mark.asyncio
    async def test_execute_creates_profile(self, sut, user_id, mocks):
        """OnboardingService.create_profile is called with correct args."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        await sut.execute(request)
        mocks["onboarding_service"].create_profile.assert_called_once()
        call_kwargs = mocks["onboarding_service"].create_profile.call_args
        assert call_kwargs[0][0] == user_id or call_kwargs[1].get("user_id") == user_id

    @pytest.mark.asyncio
    async def test_returns_ok_status(self, sut, user_id, mocks):
        """Response has profile_initialized=True."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        result = await sut.execute(request)
        assert isinstance(result, OnboardResponse)
        assert result.profile_initialized is True

    @pytest.mark.asyncio
    async def test_validates_topics(self, sut, user_id, mocks):
        """When OnboardingService raises ValueError, it propagates."""
        mocks["onboarding_service"].create_profile.side_effect = ValueError("Invalid topics")
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        with pytest.raises(ValueError):
            await sut.execute(request)

    @pytest.mark.asyncio
    async def test_saves_to_repository(self, sut, user_id, mocks):
        """profile_repo.save is called after onboarding."""
        request = OnboardRequest(user_id=user_id, categories=VALID_CATEGORIES)
        await sut.execute(request)
        mocks["profile_repo"].save.assert_called_once()
