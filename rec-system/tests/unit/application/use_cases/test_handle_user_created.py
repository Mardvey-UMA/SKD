"""Tests for HandleUserCreatedUseCase."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, call
from uuid import UUID, uuid4

from src.application.dto.kafka_events import UserCreatedEvent
from src.application.use_cases.handle_user_created import HandleUserCreatedUseCase
from src.domain.entities.user_profile import UserProfile


def make_event(user_id: UUID | None = None) -> UserCreatedEvent:
    return UserCreatedEvent(
        event_type="user.created",
        user_id=user_id or uuid4(),
        email="test@example.com",
        timestamp=datetime.now(timezone.utc),
    )


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def profile_repo():
    repo = AsyncMock()
    repo.get_by_user_id.return_value = None  # default: no profile exists
    repo.save.return_value = None
    return repo


@pytest.fixture
def sut(profile_repo):
    return HandleUserCreatedUseCase(profile_repo=profile_repo)


@pytest.mark.unit
class TestHandleUserCreatedUseCase:
    @pytest.mark.asyncio
    async def test_creates_new_profile(self, sut, profile_repo, user_id):
        """execute creates a new profile when none exists."""
        event = make_event(user_id)
        await sut.execute(event)
        profile_repo.save.assert_called_once()

    @pytest.mark.asyncio
    async def test_created_profile_has_cold_start_true(self, profile_repo, user_id):
        """Created profile has cold_start=True."""
        profile_repo.get_by_user_id.return_value = None
        sut = HandleUserCreatedUseCase(profile_repo=profile_repo)
        event = make_event(user_id)
        await sut.execute(event)
        saved_profile: UserProfile = profile_repo.save.call_args[0][0]
        assert saved_profile.cold_start is True

    @pytest.mark.asyncio
    async def test_created_profile_has_zero_interaction_count(self, profile_repo, user_id):
        """Created profile has interaction_count=0."""
        profile_repo.get_by_user_id.return_value = None
        sut = HandleUserCreatedUseCase(profile_repo=profile_repo)
        event = make_event(user_id)
        await sut.execute(event)
        saved_profile: UserProfile = profile_repo.save.call_args[0][0]
        assert saved_profile.interaction_count == 0

    @pytest.mark.asyncio
    async def test_created_profile_has_null_embedding(self, profile_repo, user_id):
        """Created profile has null/None embedding."""
        profile_repo.get_by_user_id.return_value = None
        sut = HandleUserCreatedUseCase(profile_repo=profile_repo)
        event = make_event(user_id)
        await sut.execute(event)
        saved_profile: UserProfile = profile_repo.save.call_args[0][0]
        assert saved_profile.embedding is None

    @pytest.mark.asyncio
    async def test_created_profile_has_correct_user_id(self, profile_repo, user_id):
        """Created profile has the correct user_id from the event."""
        profile_repo.get_by_user_id.return_value = None
        sut = HandleUserCreatedUseCase(profile_repo=profile_repo)
        event = make_event(user_id)
        await sut.execute(event)
        saved_profile: UserProfile = profile_repo.save.call_args[0][0]
        assert saved_profile.user_id == user_id

    @pytest.mark.asyncio
    async def test_idempotent_skip_if_profile_exists(self, profile_repo, user_id):
        """If profile already exists, execute skips without error."""
        from datetime import datetime, timezone
        from src.domain.value_objects.topic_vector import TopicVector
        from src.domain.value_objects.sentiment_prefs import SentimentPrefs
        from src.domain.value_objects.format_prefs import FormatPrefs

        now = datetime.now(timezone.utc)
        existing_profile = UserProfile(
            user_id=user_id,
            topic_vector=TopicVector.create_uniform(),
            sentiment_prefs=SentimentPrefs.create_uniform(),
            format_prefs=FormatPrefs.create_default(),
            interaction_count=5,
            created_at=now,
            last_updated=now,
            embedding=None,
            cold_start=False,
        )
        profile_repo.get_by_user_id.return_value = existing_profile
        sut = HandleUserCreatedUseCase(profile_repo=profile_repo)
        event = make_event(user_id)
        await sut.execute(event)  # should not raise
        profile_repo.save.assert_not_called()

    @pytest.mark.asyncio
    async def test_idempotent_existing_profile_not_overwritten(self, profile_repo, user_id):
        """Existing profile interaction_count is NOT reset to 0."""
        from datetime import datetime, timezone
        from src.domain.value_objects.topic_vector import TopicVector
        from src.domain.value_objects.sentiment_prefs import SentimentPrefs
        from src.domain.value_objects.format_prefs import FormatPrefs

        now = datetime.now(timezone.utc)
        existing = UserProfile(
            user_id=user_id,
            topic_vector=TopicVector.create_uniform(),
            sentiment_prefs=SentimentPrefs.create_uniform(),
            format_prefs=FormatPrefs.create_default(),
            interaction_count=42,
            created_at=now,
            last_updated=now,
            embedding=None,
            cold_start=False,
        )
        profile_repo.get_by_user_id.return_value = existing
        sut = HandleUserCreatedUseCase(profile_repo=profile_repo)
        event = make_event(user_id)
        await sut.execute(event)
        profile_repo.save.assert_not_called()
