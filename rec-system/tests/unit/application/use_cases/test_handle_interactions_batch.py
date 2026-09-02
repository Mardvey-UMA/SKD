"""Tests for HandleInteractionsBatchUseCase."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, call
from uuid import UUID, uuid4

from src.application.dto.kafka_events import InteractionsBatchEvent, InteractionItem
from src.application.use_cases.handle_interactions_batch import (
    HandleInteractionsBatchUseCase,
    _make_fake_interaction,
    STRONG_ACTION_TYPES,
)
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.entity_interest import EntityInterest
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.topic_vector import TopicVector


DEBOUNCE_KEY_PREFIX = "rec:invalidation:last:"


def make_profile(user_id: UUID) -> UserProfile:
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
        cold_start=True,
    )


def make_content(post_id: UUID | None = None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id or uuid4(),
        content="test content",
        post_date=datetime.now(timezone.utc),
    )


def make_interaction(action_type: str, content_id: UUID | None = None) -> InteractionItem:
    return InteractionItem(
        content_id=content_id or uuid4(),
        action_type=action_type,
        duration_sec=30,
        timestamp=datetime.now(timezone.utc),
    )


def make_event(
    user_id: UUID,
    interactions: list[InteractionItem] | None = None,
) -> InteractionsBatchEvent:
    return InteractionsBatchEvent(
        event_type="user.interactions.batch",
        user_id=user_id,
        interactions=interactions or [make_interaction("view")],
        batch_ts=datetime.now(timezone.utc),
    )


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def mocks(user_id):
    profile = make_profile(user_id)
    content = make_content()

    profile_repo = AsyncMock()
    profile_repo.get_by_user_id.return_value = profile
    profile_repo.save.return_value = None

    content_repo = AsyncMock()
    content_repo.get_by_ids.return_value = [content]

    signal_classifier = MagicMock()
    signal_classifier.classify_batch.return_value = [
        Signal(post_id=content.post_id, user_id=str(user_id), weight=0.15, event_type="IMPRESSION")
    ]

    updated_profile = make_profile(user_id)
    profile_updater = MagicMock()
    profile_updater.apply_batch.return_value = (updated_profile, [])

    event_publisher = AsyncMock()
    event_publisher.publish_recommendations_updated.return_value = None

    cache_client = AsyncMock()
    cache_client.get.return_value = None  # No debounce key by default
    cache_client.set.return_value = None

    entity_interest_repo = AsyncMock()
    entity_interest_repo.upsert.return_value = None
    entity_interest_repo.decay_all_for_user.return_value = None
    entity_interest_repo.cleanup_expired.return_value = None

    interaction_repo = AsyncMock()
    interaction_repo.save_batch.return_value = None

    return {
        "profile_repo": profile_repo,
        "content_repo": content_repo,
        "signal_classifier": signal_classifier,
        "profile_updater": profile_updater,
        "event_publisher": event_publisher,
        "cache_client": cache_client,
        "entity_interest_repo": entity_interest_repo,
        "interaction_repo": interaction_repo,
        "profile": profile,
        "content": content,
        "updated_profile": updated_profile,
    }


@pytest.fixture
def sut(mocks):
    return HandleInteractionsBatchUseCase(
        profile_repo=mocks["profile_repo"],
        content_repo=mocks["content_repo"],
        signal_classifier=mocks["signal_classifier"],
        profile_updater=mocks["profile_updater"],
        event_publisher=mocks["event_publisher"],
        cache_client=mocks["cache_client"],
        entity_interest_repo=mocks["entity_interest_repo"],
        interaction_repo=mocks["interaction_repo"],
    )


@pytest.mark.unit
class TestHandleInteractionsBatchUseCase:
    @pytest.mark.asyncio
    async def test_execute_processes_interactions(self, sut, user_id, mocks):
        """execute classifies signals and updates profile."""
        interactions = [make_interaction("view")]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["signal_classifier"].classify_batch.assert_called()
        mocks["profile_updater"].apply_batch.assert_called()
        mocks["profile_repo"].save.assert_called()

    @pytest.mark.asyncio
    async def test_profile_created_defensively_if_not_found(self, mocks, user_id):
        """If profile not found, creates an empty profile and continues."""
        mocks["profile_repo"].get_by_user_id.return_value = None
        sut = HandleInteractionsBatchUseCase(
            profile_repo=mocks["profile_repo"],
            content_repo=mocks["content_repo"],
            signal_classifier=mocks["signal_classifier"],
            profile_updater=mocks["profile_updater"],
            event_publisher=mocks["event_publisher"],
            cache_client=mocks["cache_client"],
            entity_interest_repo=mocks["entity_interest_repo"],
            interaction_repo=mocks["interaction_repo"],
        )
        event = make_event(user_id)
        await sut.execute(event)  # Should not raise
        # Profile should have been saved (created + updated)
        assert mocks["profile_repo"].save.call_count >= 1

    @pytest.mark.asyncio
    async def test_strong_signal_like_triggers_publish_check(self, sut, user_id, mocks):
        """A LIKE action type triggers debounce check and publishes if key absent."""
        interactions = [make_interaction("LIKE")]
        mocks["signal_classifier"].classify_batch.return_value = [
            Signal(post_id=uuid4(), user_id=str(user_id), weight=0.6, event_type="LIKE")
        ]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["cache_client"].get.assert_called()

    @pytest.mark.asyncio
    async def test_debounce_first_call_publishes(self, sut, user_id, mocks):
        """First call with strong signal publishes recommendations.updated."""
        interactions = [make_interaction("LIKE")]
        mocks["cache_client"].get.return_value = None  # No debounce key
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["event_publisher"].publish_recommendations_updated.assert_called_once_with(
            user_id=str(user_id),
            reason="interactions_processed",
        )

    @pytest.mark.asyncio
    async def test_debounce_second_call_skips_publish(self, mocks, user_id):
        """Second call within 15 min (debounce key exists) skips publish."""
        mocks["cache_client"].get.return_value = "1"  # Debounce key exists
        sut = HandleInteractionsBatchUseCase(
            profile_repo=mocks["profile_repo"],
            content_repo=mocks["content_repo"],
            signal_classifier=mocks["signal_classifier"],
            profile_updater=mocks["profile_updater"],
            event_publisher=mocks["event_publisher"],
            cache_client=mocks["cache_client"],
            entity_interest_repo=mocks["entity_interest_repo"],
            interaction_repo=mocks["interaction_repo"],
        )
        interactions = [make_interaction("LIKE")]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_debounce_key_set_after_publish(self, sut, user_id, mocks):
        """After publishing, debounce key is set with TTL 900."""
        interactions = [make_interaction("LIKE")]
        mocks["cache_client"].get.return_value = None
        event = make_event(user_id, interactions)
        await sut.execute(event)
        debounce_key = f"{DEBOUNCE_KEY_PREFIX}{user_id}"
        mocks["cache_client"].set.assert_called_once_with(debounce_key, "1", ttl=900)

    @pytest.mark.asyncio
    async def test_no_strong_signals_no_publish(self, sut, user_id, mocks):
        """Without strong signals (view only), no publish attempt."""
        interactions = [make_interaction("view")]
        # Override signal classifier to return weak signal
        mocks["signal_classifier"].classify_batch.return_value = [
            Signal(post_id=uuid4(), user_id=str(user_id), weight=0.15, event_type="IMPRESSION")
        ]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_scroll_past_does_not_trigger_publish(self, sut, user_id, mocks):
        """scroll_past is not a strong signal."""
        interactions = [make_interaction("scroll_past")]
        mocks["signal_classifier"].classify_batch.return_value = [
            Signal(post_id=uuid4(), user_id=str(user_id), weight=-0.05, event_type="IMPRESSION")
        ]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_multiple_interactions_all_processed(self, sut, user_id, mocks):
        """All interactions in the batch are passed to signal_classifier."""
        content_ids = [uuid4() for _ in range(3)]
        interactions = [
            make_interaction("view", content_ids[0]),
            make_interaction("click", content_ids[1]),
            make_interaction("save", content_ids[2]),
        ]
        mocks["content_repo"].get_by_ids.return_value = [
            make_content(cid) for cid in content_ids
        ]
        mocks["signal_classifier"].classify_batch.return_value = [
            Signal(post_id=cid, user_id=str(user_id), weight=0.6, event_type="LIKE")
            for cid in content_ids
        ]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        # All 3 interactions passed to classifier
        call_args = mocks["signal_classifier"].classify_batch.call_args
        classified_interactions = call_args[0][0]
        assert len(classified_interactions) == 3

    @pytest.mark.asyncio
    async def test_entity_interests_upserted_when_apply_batch_returns_them(self, sut, user_id, mocks):
        """entity_interest_repo.upsert is called for each EntityInterest returned by apply_batch."""
        now = datetime.now(timezone.utc)
        ei1 = EntityInterest(user_id=user_id, entity_type="PER", entity_name="Иванов", weight=1.0, last_seen=now)
        ei2 = EntityInterest(user_id=user_id, entity_type="ORG", entity_name="Газпром", weight=0.5, last_seen=now)
        mocks["profile_updater"].apply_batch.return_value = (mocks["updated_profile"], [ei1, ei2])

        event = make_event(user_id, [make_interaction("view")])
        await sut.execute(event)

        assert mocks["entity_interest_repo"].upsert.call_count == 2
        mocks["entity_interest_repo"].upsert.assert_any_call(ei1)
        mocks["entity_interest_repo"].upsert.assert_any_call(ei2)

    @pytest.mark.asyncio
    async def test_entity_decay_called_after_upsert(self, sut, user_id, mocks):
        """entity_interest_repo.decay_all_for_user is called after upsert."""
        mocks["profile_updater"].apply_batch.return_value = (mocks["updated_profile"], [])

        event = make_event(user_id, [make_interaction("view")])
        await sut.execute(event)

        mocks["entity_interest_repo"].decay_all_for_user.assert_called_once_with(user_id, pytest.approx(0.9))

    @pytest.mark.asyncio
    async def test_entity_cleanup_called_after_decay(self, sut, user_id, mocks):
        """entity_interest_repo.cleanup_expired is called after decay."""
        mocks["profile_updater"].apply_batch.return_value = (mocks["updated_profile"], [])

        event = make_event(user_id, [make_interaction("view")])
        await sut.execute(event)

        mocks["entity_interest_repo"].cleanup_expired.assert_called_once_with(user_id, 30)

    @pytest.mark.asyncio
    async def test_interactions_saved_to_user_interactions(self, sut, user_id, mocks):
        """interaction_repo.save_batch is called with the batch of interactions."""
        interactions = [make_interaction("view"), make_interaction("click")]
        event = make_event(user_id, interactions)
        await sut.execute(event)

        mocks["interaction_repo"].save_batch.assert_called_once()
        saved = mocks["interaction_repo"].save_batch.call_args[0][0]
        assert len(saved) == 2

    @pytest.mark.asyncio
    async def test_no_entity_interests_when_apply_batch_returns_empty(self, sut, user_id, mocks):
        """No upsert calls when apply_batch returns empty entity_changes."""
        mocks["profile_updater"].apply_batch.return_value = (mocks["updated_profile"], [])

        event = make_event(user_id, [make_interaction("view")])
        await sut.execute(event)

        mocks["entity_interest_repo"].upsert.assert_not_called()


@pytest.mark.unit
class TestActionTypeMappingAndNormalization:
    """Tests for action_type mapping fixes: dislike support and .lower() normalization."""

    def test_dislike_maps_to_dislike_event_type(self):
        """'dislike' action_type must map to 'DISLIKE' event_type."""
        user_id = uuid4()
        item = make_interaction("dislike")
        interaction = _make_fake_interaction(user_id, item)
        assert interaction.event_type == "DISLIKE"

    def test_uppercase_click_maps_to_open_canonical(self):
        """Uppercase 'CLICK' maps to 'OPEN' per canonical ActionType.fromString()."""
        user_id = uuid4()
        item = make_interaction("CLICK")
        interaction = _make_fake_interaction(user_id, item)
        assert interaction is not None
        assert interaction.event_type == "OPEN"

    def test_mixed_case_save_maps_to_bookmark(self):
        """'Save' (mixed case) must map to 'BOOKMARK'."""
        user_id = uuid4()
        item = make_interaction("Save")
        interaction = _make_fake_interaction(user_id, item)
        assert interaction.event_type == "BOOKMARK"

    def test_uppercase_dislike_maps_to_dislike_via_normalization(self):
        """'DISLIKE' (uppercase from frontend) must normalize to 'dislike' and map to 'DISLIKE'."""
        user_id = uuid4()
        item = make_interaction("DISLIKE")
        interaction = _make_fake_interaction(user_id, item)
        assert interaction.event_type == "DISLIKE"

    def test_hide_still_maps_to_dislike(self):
        """Regression: 'hide' must still map to 'DISLIKE'."""
        user_id = uuid4()
        item = make_interaction("hide")
        interaction = _make_fake_interaction(user_id, item)
        assert interaction.event_type == "DISLIKE"

    def test_share_maps_to_bookmark_canonical(self):
        """'share' maps to 'BOOKMARK' per canonical ActionType.fromString()."""
        user_id = uuid4()
        item = make_interaction("share")
        interaction = _make_fake_interaction(user_id, item)
        assert interaction is not None
        assert interaction.event_type == "BOOKMARK"

    def test_DISLIKE_is_in_strong_action_types(self):
        """Canonical 'DISLIKE' must be in STRONG_ACTION_TYPES to trigger debounce/publish."""
        assert "DISLIKE" in STRONG_ACTION_TYPES

    def test_lowercase_share_is_not_in_strong_action_types(self):
        """STRONG_ACTION_TYPES contains canonical uppercase only; lowercase not a member."""
        assert "share" not in STRONG_ACTION_TYPES

    @pytest.mark.asyncio
    async def test_dislike_action_triggers_strong_signal_debounce(self, mocks, user_id):
        """Batch with action_type='dislike' must trigger debounce check (publish path)."""
        history_repo = AsyncMock()
        history_repo.save_recommendations.return_value = None
        sut = HandleInteractionsBatchUseCase(
            profile_repo=mocks["profile_repo"],
            content_repo=mocks["content_repo"],
            signal_classifier=mocks["signal_classifier"],
            profile_updater=mocks["profile_updater"],
            event_publisher=mocks["event_publisher"],
            cache_client=mocks["cache_client"],
            entity_interest_repo=mocks["entity_interest_repo"],
            interaction_repo=mocks["interaction_repo"],
            history_repo=history_repo,
        )
        interactions = [make_interaction("dislike")]
        mocks["cache_client"].get.return_value = None
        event = make_event(user_id, interactions)
        await sut.execute(event)
        mocks["cache_client"].get.assert_called()
        mocks["event_publisher"].publish_recommendations_updated.assert_called_once()

    @pytest.mark.asyncio
    async def test_dislike_action_written_to_recommendation_history(self, mocks, user_id):
        """Batch with action_type='dislike' must write content_id to recommendation_history."""
        history_repo = AsyncMock()
        history_repo.save_recommendations.return_value = None
        sut = HandleInteractionsBatchUseCase(
            profile_repo=mocks["profile_repo"],
            content_repo=mocks["content_repo"],
            signal_classifier=mocks["signal_classifier"],
            profile_updater=mocks["profile_updater"],
            event_publisher=mocks["event_publisher"],
            cache_client=mocks["cache_client"],
            entity_interest_repo=mocks["entity_interest_repo"],
            interaction_repo=mocks["interaction_repo"],
            history_repo=history_repo,
        )
        content_id = uuid4()
        interactions = [make_interaction("dislike", content_id)]
        event = make_event(user_id, interactions)
        await sut.execute(event)
        history_repo.save_recommendations.assert_called_once()
        call_kwargs = history_repo.save_recommendations.call_args
        saved_ids = call_kwargs[1].get("content_ids") or call_kwargs[0][1]
        assert content_id in saved_ids
