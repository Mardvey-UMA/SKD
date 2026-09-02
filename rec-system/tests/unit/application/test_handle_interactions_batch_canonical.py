"""RED tests for canonical action_type mapping in HandleInteractionsBatchUseCase.

Backend `user-interactions-service` (MVP Hardening P1) emits canonical UPPERCASE
action_type values: IMPRESSION, OPEN, CLOSE, LIKE, DISLIKE, BOOKMARK.
Legacy lowercase values (view, click, save, hide, scroll_past, share) must
still be accepted and mapped to canonical per backend `ActionType.fromString()`.

Unknown types must be silently dropped (None) — per CLAUDE.md.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

import pytest

from src.application.dto.kafka_events import InteractionItem, InteractionsBatchEvent
from src.application.use_cases.handle_interactions_batch import (
    HandleInteractionsBatchUseCase,
    STRONG_ACTION_TYPES,
    _ACTION_TYPE_MAP,
    _make_fake_interaction,
)
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.topic_vector import TopicVector


# ---------- helpers ----------

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


def make_item(
    action_type: str,
    content_id: UUID | None = None,
    duration_sec: float | None = 30,
    timestamp: datetime | None = None,
) -> InteractionItem:
    return InteractionItem(
        content_id=content_id or uuid4(),
        action_type=action_type,
        duration_sec=duration_sec,
        timestamp=timestamp or datetime.now(timezone.utc),
    )


def make_event(user_id: UUID, items: list[InteractionItem]) -> InteractionsBatchEvent:
    return InteractionsBatchEvent(
        event_type="user.interactions.batch",
        user_id=user_id,
        interactions=items,
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
    signal_classifier.classify_batch.return_value = []

    profile_updater = MagicMock()
    profile_updater.apply_batch.return_value = (make_profile(user_id), [])

    event_publisher = AsyncMock()
    cache_client = AsyncMock()
    cache_client.get.return_value = None
    cache_client.set.return_value = None

    entity_interest_repo = AsyncMock()
    interaction_repo = AsyncMock()

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
    }


def build_sut(mocks) -> HandleInteractionsBatchUseCase:
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


# ---------- canonical identity mapping (event_type on UserInteraction) ----------

@pytest.mark.unit
class TestCanonicalIdentityMapping:
    """Canonical uppercase action_types map to the same canonical event_type."""

    def test_canonical_IMPRESSION_maps_to_IMPRESSION(self):
        ui = _make_fake_interaction(uuid4(), make_item("IMPRESSION"))
        assert ui is not None
        assert ui.event_type == "IMPRESSION"

    def test_canonical_OPEN_maps_to_OPEN(self):
        ui = _make_fake_interaction(uuid4(), make_item("OPEN"))
        assert ui is not None
        assert ui.event_type == "OPEN"

    def test_canonical_CLOSE_maps_to_CLOSE(self):
        ui = _make_fake_interaction(uuid4(), make_item("CLOSE"))
        assert ui is not None
        assert ui.event_type == "CLOSE"

    def test_canonical_LIKE_maps_to_LIKE(self):
        ui = _make_fake_interaction(uuid4(), make_item("LIKE"))
        assert ui is not None
        assert ui.event_type == "LIKE"

    def test_canonical_DISLIKE_maps_to_DISLIKE(self):
        ui = _make_fake_interaction(uuid4(), make_item("DISLIKE"))
        assert ui is not None
        assert ui.event_type == "DISLIKE"

    def test_canonical_BOOKMARK_maps_to_BOOKMARK(self):
        ui = _make_fake_interaction(uuid4(), make_item("BOOKMARK"))
        assert ui is not None
        assert ui.event_type == "BOOKMARK"


# ---------- legacy → canonical (backend fromString aliases) ----------

@pytest.mark.unit
class TestLegacyAliasMapping:
    """Legacy action_type values map to canonical per backend ActionType.fromString()."""

    def test_legacy_VIEW_maps_to_IMPRESSION(self):
        ui = _make_fake_interaction(uuid4(), make_item("VIEW"))
        assert ui is not None
        assert ui.event_type == "IMPRESSION"

    def test_legacy_CLICK_maps_to_OPEN(self):
        """CLICK → OPEN (NOT LIKE — matches backend fromString)."""
        ui = _make_fake_interaction(uuid4(), make_item("CLICK"))
        assert ui is not None
        assert ui.event_type == "OPEN"

    def test_legacy_SCROLL_PAST_maps_to_CLOSE(self):
        ui = _make_fake_interaction(uuid4(), make_item("SCROLL_PAST"))
        assert ui is not None
        assert ui.event_type == "CLOSE"

    def test_legacy_SAVE_maps_to_BOOKMARK(self):
        ui = _make_fake_interaction(uuid4(), make_item("SAVE"))
        assert ui is not None
        assert ui.event_type == "BOOKMARK"

    def test_legacy_HIDE_maps_to_DISLIKE(self):
        ui = _make_fake_interaction(uuid4(), make_item("HIDE"))
        assert ui is not None
        assert ui.event_type == "DISLIKE"

    def test_legacy_SHARE_maps_to_BOOKMARK(self):
        ui = _make_fake_interaction(uuid4(), make_item("SHARE"))
        assert ui is not None
        assert ui.event_type == "BOOKMARK"


# ---------- case insensitivity ----------

@pytest.mark.unit
class TestCaseInsensitive:
    def test_lowercase_like_maps_to_LIKE(self):
        ui = _make_fake_interaction(uuid4(), make_item("like"))
        assert ui is not None
        assert ui.event_type == "LIKE"

    def test_mixedcase_Like_maps_to_LIKE(self):
        ui = _make_fake_interaction(uuid4(), make_item("Like"))
        assert ui is not None
        assert ui.event_type == "LIKE"

    def test_lowercase_click_maps_to_OPEN(self):
        ui = _make_fake_interaction(uuid4(), make_item("click"))
        assert ui is not None
        assert ui.event_type == "OPEN"


# ---------- unknown action_type → silent drop ----------

@pytest.mark.unit
class TestUnknownActionType:
    def test_unknown_returns_None(self):
        ui = _make_fake_interaction(uuid4(), make_item("FOOBAR"))
        assert ui is None

    def test_empty_string_returns_None(self):
        ui = _make_fake_interaction(uuid4(), make_item(""))
        assert ui is None


# ---------- STRONG_ACTION_TYPES set ----------

@pytest.mark.unit
class TestStrongActionTypesSet:
    def test_LIKE_is_strong(self):
        assert "LIKE" in STRONG_ACTION_TYPES

    def test_DISLIKE_is_strong(self):
        assert "DISLIKE" in STRONG_ACTION_TYPES

    def test_BOOKMARK_is_strong(self):
        assert "BOOKMARK" in STRONG_ACTION_TYPES

    def test_IMPRESSION_is_NOT_strong(self):
        assert "IMPRESSION" not in STRONG_ACTION_TYPES

    def test_OPEN_is_NOT_strong(self):
        assert "OPEN" not in STRONG_ACTION_TYPES

    def test_CLOSE_is_NOT_strong(self):
        assert "CLOSE" not in STRONG_ACTION_TYPES


# ---------- integration-ish: execute() with canonical action_types ----------

@pytest.mark.unit
class TestExecuteWithCanonicalActionTypes:
    """Verify execute() processes canonical action_types end-to-end."""

    @pytest.mark.asyncio
    async def test_canonical_LIKE_reaches_signal_classifier_as_LIKE(self, mocks, user_id):
        """LIKE action_type → SignalClassifier sees UserInteraction(event_type='LIKE')."""
        sut = build_sut(mocks)
        content_id = uuid4()
        mocks["content_repo"].get_by_ids.return_value = [make_content(content_id)]
        event = make_event(user_id, [make_item("LIKE", content_id)])

        await sut.execute(event)

        mocks["signal_classifier"].classify_batch.assert_called_once()
        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        assert len(classified) == 1
        assert classified[0].event_type == "LIKE"

    @pytest.mark.asyncio
    async def test_canonical_BOOKMARK_reaches_signal_classifier_as_BOOKMARK(self, mocks, user_id):
        sut = build_sut(mocks)
        content_id = uuid4()
        mocks["content_repo"].get_by_ids.return_value = [make_content(content_id)]
        event = make_event(user_id, [make_item("BOOKMARK", content_id)])

        await sut.execute(event)

        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        assert len(classified) == 1
        assert classified[0].event_type == "BOOKMARK"

    @pytest.mark.asyncio
    async def test_canonical_DISLIKE_reaches_signal_classifier_as_DISLIKE(self, mocks, user_id):
        """Regression: DISLIKE still maps correctly."""
        sut = build_sut(mocks)
        content_id = uuid4()
        mocks["content_repo"].get_by_ids.return_value = [make_content(content_id)]
        event = make_event(user_id, [make_item("DISLIKE", content_id)])

        await sut.execute(event)

        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        assert len(classified) == 1
        assert classified[0].event_type == "DISLIKE"

    @pytest.mark.asyncio
    async def test_canonical_CLOSE_reaches_signal_classifier_as_CLOSE(self, mocks, user_id):
        """CLOSE event_type reaches classifier — so _classify_close() is triggered."""
        sut = build_sut(mocks)
        content_id = uuid4()
        mocks["content_repo"].get_by_ids.return_value = [make_content(content_id)]
        event = make_event(user_id, [make_item("CLOSE", content_id, duration_sec=20)])

        await sut.execute(event)

        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        assert len(classified) == 1
        assert classified[0].event_type == "CLOSE"
        assert classified[0].duration_ms == 20_000

    @pytest.mark.asyncio
    async def test_canonical_OPEN_and_CLOSE_paired_reach_classifier(self, mocks, user_id):
        """OPEN + CLOSE within 5 min → both reach classifier with paired event types."""
        sut = build_sut(mocks)
        content_id = uuid4()
        mocks["content_repo"].get_by_ids.return_value = [make_content(content_id)]
        now = datetime.now(timezone.utc)
        event = make_event(
            user_id,
            [
                make_item("OPEN", content_id, duration_sec=None, timestamp=now),
                make_item("CLOSE", content_id, duration_sec=20, timestamp=now + timedelta(seconds=20)),
            ],
        )

        await sut.execute(event)

        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        types = [ui.event_type for ui in classified]
        assert "OPEN" in types
        assert "CLOSE" in types

    @pytest.mark.asyncio
    async def test_canonical_LIKE_triggers_recommendations_updated_publish(self, mocks, user_id):
        """LIKE is a strong signal → debounce check + publish path."""
        sut = build_sut(mocks)
        mocks["cache_client"].get.return_value = None  # no debounce key
        event = make_event(user_id, [make_item("LIKE")])

        await sut.execute(event)

        mocks["cache_client"].get.assert_called()
        mocks["event_publisher"].publish_recommendations_updated.assert_called_once_with(
            user_id=str(user_id),
            reason="interactions_processed",
        )

    @pytest.mark.asyncio
    async def test_canonical_BOOKMARK_triggers_recommendations_updated_publish(self, mocks, user_id):
        sut = build_sut(mocks)
        mocks["cache_client"].get.return_value = None
        event = make_event(user_id, [make_item("BOOKMARK")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_called_once()

    @pytest.mark.asyncio
    async def test_canonical_DISLIKE_triggers_recommendations_updated_publish(self, mocks, user_id):
        sut = build_sut(mocks)
        mocks["cache_client"].get.return_value = None
        event = make_event(user_id, [make_item("DISLIKE")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_called_once()

    @pytest.mark.asyncio
    async def test_canonical_IMPRESSION_does_not_trigger_publish(self, mocks, user_id):
        """IMPRESSION is NOT a strong signal — no publish attempt."""
        sut = build_sut(mocks)
        event = make_event(user_id, [make_item("IMPRESSION")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_canonical_OPEN_does_not_trigger_publish(self, mocks, user_id):
        """OPEN is NOT a strong signal (per canonical contract)."""
        sut = build_sut(mocks)
        event = make_event(user_id, [make_item("OPEN")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_legacy_CLICK_does_not_trigger_publish(self, mocks, user_id):
        """Legacy CLICK maps to OPEN (not LIKE) — must NOT trigger publish."""
        sut = build_sut(mocks)
        event = make_event(user_id, [make_item("CLICK")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_legacy_SAVE_triggers_publish(self, mocks, user_id):
        """Legacy SAVE maps to BOOKMARK (strong) → publish."""
        sut = build_sut(mocks)
        mocks["cache_client"].get.return_value = None
        event = make_event(user_id, [make_item("SAVE")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_called_once()

    @pytest.mark.asyncio
    async def test_legacy_HIDE_triggers_publish(self, mocks, user_id):
        """Legacy HIDE maps to DISLIKE (strong) → publish."""
        sut = build_sut(mocks)
        mocks["cache_client"].get.return_value = None
        event = make_event(user_id, [make_item("HIDE")])

        await sut.execute(event)

        mocks["event_publisher"].publish_recommendations_updated.assert_called_once()

    @pytest.mark.asyncio
    async def test_unknown_action_type_is_silently_dropped_no_crash(self, mocks, user_id):
        """Unknown action_type — event skipped, no crash, no signal."""
        sut = build_sut(mocks)
        event = make_event(user_id, [make_item("FOOBAR")])

        await sut.execute(event)  # must not raise

        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        assert len(classified) == 0  # FOOBAR dropped
        mocks["event_publisher"].publish_recommendations_updated.assert_not_called()

    @pytest.mark.asyncio
    async def test_unknown_mixed_with_valid_only_valid_processed(self, mocks, user_id):
        """Mix of FOOBAR + LIKE → only LIKE reaches classifier."""
        sut = build_sut(mocks)
        mocks["cache_client"].get.return_value = None
        mocks["content_repo"].get_by_ids.return_value = [make_content()]
        event = make_event(user_id, [make_item("FOOBAR"), make_item("LIKE")])

        await sut.execute(event)

        classified = mocks["signal_classifier"].classify_batch.call_args[0][0]
        assert len(classified) == 1
        assert classified[0].event_type == "LIKE"

    @pytest.mark.asyncio
    async def test_canonical_LIKE_written_to_recommendation_history(self, mocks, user_id):
        """LIKE is strong → post written to recommendation_history."""
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
        event = make_event(user_id, [make_item("LIKE", content_id)])

        await sut.execute(event)

        history_repo.save_recommendations.assert_called_once()
        call_kwargs = history_repo.save_recommendations.call_args.kwargs
        assert content_id in call_kwargs["content_ids"]
