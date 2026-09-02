"""Tests for UpdateProfileUseCase."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.application.dto.update_profile import UpdateProfileCommand, UpdateProfileResult
from src.application.use_cases.update_profile import UpdateProfileUseCase
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.entity_interest import EntityInterest
from src.domain.entities.user_interaction import UserInteraction
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.event_type import EventType
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


def make_profile(user_id: UUID) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_from_selected(["technology", "science", "health"], 0.01),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=5,
        created_at=now,
        last_updated=now,
        embedding=None,
    )


def make_interaction(user_id: UUID, post_id, interaction_id: int = 1) -> UserInteraction:
    return UserInteraction(
        id=interaction_id,
        event_id=uuid4(),
        user_id=str(user_id),
        post_id=post_id,
        event_type="LIKE",
        duration_ms=None,
        scroll_pct=None,
        max_scroll_pct=None,
        created_at=datetime.now(timezone.utc),
        processed=False,
    )


def make_content(post_id=None) -> ContentFeatures:
    from uuid import UUID
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="some text",
        post_date=datetime.now(timezone.utc),
        topic_1="technology",
        topic_1_score=0.8,
        processed_at=datetime.now(timezone.utc),
    )


def make_signal(user_id: UUID, post_id) -> Signal:
    return Signal(
        post_id=post_id,
        user_id=str(user_id),
        weight=1.0,
        event_type=EventType.LIKE,
    )


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def config():
    return {
        "learning_rate": 0.08,
        "entity_min_weight": 0.4,
        "entity_max_per_post": 5,
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
def mocks(user_id, config):
    profile = make_profile(user_id)
    post_uuid = uuid4()
    interaction = make_interaction(user_id, post_id=post_uuid)
    content = make_content(post_id=post_uuid)
    signal = make_signal(user_id, post_id=post_uuid)
    entity_interest = EntityInterest(
        user_id=str(user_id),
        entity_type="person",
        entity_name="Alice",
        weight=1.0,
        last_seen=datetime.now(timezone.utc),
    )

    interaction_repo = AsyncMock()
    interaction_repo.get_unprocessed.return_value = [interaction]
    interaction_repo.get_unprocessed_for_user.return_value = [interaction]
    interaction_repo.mark_processed.return_value = None

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile
    profile_repo.save.return_value = None

    entity_interest_repo = AsyncMock()
    entity_interest_repo.upsert.return_value = None
    entity_interest_repo.decay_all_for_user.return_value = None
    entity_interest_repo.cleanup_expired.return_value = None

    config_repo = AsyncMock()
    config_repo.get_config.return_value = config

    signal_classifier = MagicMock()
    signal_classifier.classify_batch.return_value = [signal]

    profile_updater = MagicMock()
    profile_updater.apply_batch.return_value = (profile, [entity_interest])

    content_repo = AsyncMock()
    content_repo.get_candidates_by_freshness.return_value = [content]

    return {
        "interaction_repo": interaction_repo,
        "profile_repo": profile_repo,
        "entity_interest_repo": entity_interest_repo,
        "config_repo": config_repo,
        "signal_classifier": signal_classifier,
        "profile_updater": profile_updater,
        "content_repo": content_repo,
        "profile": profile,
        "interaction": interaction,
        "content": content,
        "signal": signal,
        "entity_interest": entity_interest,
    }


@pytest.fixture
def sut(mocks):
    return UpdateProfileUseCase(
        interaction_repo=mocks["interaction_repo"],
        profile_repo=mocks["profile_repo"],
        entity_interest_repo=mocks["entity_interest_repo"],
        config_repo=mocks["config_repo"],
        signal_classifier=mocks["signal_classifier"],
        profile_updater=mocks["profile_updater"],
        content_repo=mocks["content_repo"],
    )


@pytest.mark.unit
class TestUpdateProfileUseCase:
    @pytest.mark.asyncio
    async def test_execute_fetches_unprocessed_interactions(self, sut, mocks):
        """interaction_repo.get_unprocessed is called when no user_id given."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["interaction_repo"].get_unprocessed.assert_called_once()

    @pytest.mark.asyncio
    async def test_groups_by_user_id(self, sut, user_id, mocks):
        """Multiple interactions for same user are processed together."""
        user_id_2 = uuid4()
        interactions = [
            make_interaction(user_id, post_id=10, interaction_id=1),
            make_interaction(user_id, post_id=11, interaction_id=2),
            make_interaction(user_id_2, post_id=12, interaction_id=3),
        ]
        mocks["interaction_repo"].get_unprocessed.return_value = interactions
        profile2 = make_profile(user_id_2)
        mocks["profile_repo"].get_by_user_id.side_effect = lambda uid: (
            make_profile(uid)
        )
        command = UpdateProfileCommand()
        await sut.execute(command)
        # profile_repo.get_by_user_id should be called once per unique user
        assert mocks["profile_repo"].get_by_user_id.call_count == 2

    @pytest.mark.asyncio
    async def test_classifies_signals(self, sut, mocks):
        """SignalClassifier.classify_batch is called with the user's interactions."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["signal_classifier"].classify_batch.assert_called_once()

    @pytest.mark.asyncio
    async def test_updates_profile(self, sut, mocks):
        """ProfileUpdater.apply_batch is called with signals and profile."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["profile_updater"].apply_batch.assert_called_once()

    @pytest.mark.asyncio
    async def test_saves_updated_profile(self, sut, mocks):
        """profile_repo.save is called with the updated profile."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["profile_repo"].save.assert_called_once()

    @pytest.mark.asyncio
    async def test_upserts_entity_interests(self, sut, mocks):
        """entity_interest_repo.upsert is called for each entity from profile update."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["entity_interest_repo"].upsert.assert_called()

    @pytest.mark.asyncio
    async def test_applies_entity_decay(self, sut, user_id, mocks):
        """entity_interest_repo.decay_all_for_user is called for the user."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["entity_interest_repo"].decay_all_for_user.assert_called_once()

    @pytest.mark.asyncio
    async def test_cleans_expired_entities(self, sut, user_id, mocks):
        """entity_interest_repo.cleanup_expired is called for the user."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["entity_interest_repo"].cleanup_expired.assert_called_once()

    @pytest.mark.asyncio
    async def test_marks_interactions_processed(self, sut, mocks):
        """interaction_repo.mark_processed is called with the interaction IDs."""
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["interaction_repo"].mark_processed.assert_called_once()
        call_args = mocks["interaction_repo"].mark_processed.call_args[0][0]
        assert isinstance(call_args, list)

    @pytest.mark.asyncio
    async def test_single_user_mode(self, sut, user_id, mocks):
        """When user_id is specified, only fetch that user's interactions."""
        command = UpdateProfileCommand(user_id=user_id)
        await sut.execute(command)
        mocks["interaction_repo"].get_unprocessed_for_user.assert_called_once_with(user_id)
        mocks["interaction_repo"].get_unprocessed.assert_not_called()

    @pytest.mark.asyncio
    async def test_returns_result(self, sut, mocks):
        """Result contains users_updated and events_processed counts."""
        command = UpdateProfileCommand()
        result = await sut.execute(command)
        assert isinstance(result, UpdateProfileResult)
        assert result.users_updated >= 0
        assert result.events_processed >= 0

    @pytest.mark.asyncio
    async def test_no_unprocessed_events(self, sut, mocks):
        """When no interactions exist, returns 0 counts and no updates."""
        mocks["interaction_repo"].get_unprocessed.return_value = []
        command = UpdateProfileCommand()
        result = await sut.execute(command)
        assert result.users_updated == 0
        assert result.events_processed == 0
        mocks["profile_repo"].save.assert_not_called()

    @pytest.mark.asyncio
    async def test_uses_get_by_ids_instead_of_get_candidates_by_freshness(self, sut, mocks):
        """execute calls content_repo.get_by_ids, NOT get_candidates_by_freshness."""
        mocks["content_repo"].get_by_ids = AsyncMock(return_value=[mocks["content"]])
        command = UpdateProfileCommand()
        await sut.execute(command)
        mocks["content_repo"].get_by_ids.assert_called_once()
        mocks["content_repo"].get_candidates_by_freshness.assert_not_called()

    @pytest.mark.asyncio
    async def test_get_by_ids_called_with_interaction_post_ids(self, sut, user_id, mocks):
        """execute calls get_by_ids with post_ids extracted from interactions."""
        mocks["content_repo"].get_by_ids = AsyncMock(return_value=[mocks["content"]])
        command = UpdateProfileCommand()
        await sut.execute(command)
        called_post_ids = mocks["content_repo"].get_by_ids.call_args[0][0]
        assert mocks["interaction"].post_id in called_post_ids

    @pytest.mark.asyncio
    async def test_apply_batch_receives_content_map_from_get_by_ids(self, sut, user_id, mocks):
        """apply_batch receives content_features_map populated from get_by_ids result."""
        content = mocks["content"]
        mocks["content_repo"].get_by_ids = AsyncMock(return_value=[content])
        command = UpdateProfileCommand()
        await sut.execute(command)
        call_kwargs = mocks["profile_updater"].apply_batch.call_args.kwargs
        assert content.post_id in call_kwargs["content_features_map"]
        assert call_kwargs["content_features_map"][content.post_id] is content
