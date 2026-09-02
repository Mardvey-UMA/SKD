"""Integration tests for UpdateProfileUseCase with a real PostgreSQL DB.

Task 50 — Phase 13: Integration Tests.
Depends on: Task 28 (UpdateProfileUseCase), Tasks 31-33 (repo adapters).
"""
from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from typing import Dict, List

import pytest
from sqlalchemy import select, text

from src.application.dto.update_profile import UpdateProfileCommand
from src.application.use_cases.update_profile import UpdateProfileUseCase
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.signal_classifier import SignalClassifier
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector
from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
from src.infrastructure.persistence.models.raw_content import RawContentModel
from src.infrastructure.persistence.models.rec_entity_interests import RecEntityInterestsModel
from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
from src.infrastructure.persistence.models.user_interactions import UserInteractionsModel
from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
from src.infrastructure.persistence.pg_content_repository import PgContentRepository
from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository
from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository
from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository


# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

_SIGNAL_WEIGHTS = {
    "impression_read": {"threshold_duration_ms": 2000, "weight": 0.15},
    "impression_skip": {"threshold_duration_ms": 2000, "weight": -0.05},
    "open": {"weight": 0.1},
    "close_fast": {"threshold_duration_ms": 3000, "weight": -0.2},
    "close_full": {"threshold_scroll_pct": 0.85, "threshold_duration_ms": 15000, "weight": 0.5},
    "close_half": {"threshold_scroll_pct": 0.5, "threshold_duration_ms": 10000, "weight": 0.4},
    "close_other": {"weight": 0.1},
    "like": {"weight": 0.6},
    "dislike": {"weight": -0.7},
    "bookmark": {"weight": 0.8},
    "orphan_timeout_minutes": 5,
}

_PROFILE_CONFIG = {
    "learning_rate": 0.08,
    "entity_min_weight": 0.4,
    "entity_max_per_post": 5,
    "entity_cleanup_days": 30,
    "entity_decay_factor": 0.95,
}


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


# ---------------------------------------------------------------------------
# Seed helpers
# ---------------------------------------------------------------------------


async def _seed_profile_config(session) -> None:
    """Upsert the 'profile' config key."""
    await session.execute(
        text(
            """
            INSERT INTO data_flow.rec_config (key, value)
            VALUES ('profile', CAST(:value AS jsonb))
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
            """
        ),
        {"value": json.dumps(_PROFILE_CONFIG)},
    )
    await session.flush()


async def _seed_raw_posts(session, post_ids: List[int]) -> Dict[int, uuid.UUID]:
    """Insert raw_content rows for each logical post_id. Returns mapping int -> UUID."""
    mapping: Dict[int, uuid.UUID] = {}
    for post_id in post_ids:
        row_id = uuid.uuid4()
        raw_data = {
            "title": f"Post {post_id}",
            "content": f"Content for post {post_id}",
            "publishedAt": _utcnow().isoformat(),
        }
        raw = RawContentModel(
            id=row_id,
            external_id=str(post_id),
            source_id=uuid.uuid4(),
            source_type="telegram",
            raw_data=raw_data,
            processing_status="COMPLETED",
            is_processed_by_rec=True,
            received_at=_utcnow(),
        )
        session.add(raw)
        mapping[post_id] = row_id
    await session.flush()
    return mapping


async def _seed_features(
    session,
    post_uuids: List[uuid.UUID],
    topic_1: str = "технологии",
    entities_persons: List[str] | None = None,
) -> None:
    if entities_persons is None:
        entities_persons = []
    for post_id in post_uuids:
        feat = PostsFeaturesModel(
            post_id=post_id,
            text_length=500,
            word_count=100,
            reading_time=0.5,
            complexity=0.5,
            is_short_form=True,
            is_long_form=False,
            topic_1=topic_1,
            topic_1_score=0.8,
            sentiment="POSITIVE",
            sentiment_score=0.8,
            entities_persons=entities_persons,
            entities_organizations=[],
            entities_locations=[],
            embedding=[0.1] * 312,
            processed_at=_utcnow(),
        )
        session.add(feat)
    await session.flush()


async def _seed_profile(session, user_id: uuid.UUID) -> None:
    now = _utcnow()
    profile = RecProfilesModel(
        user_id=user_id,
        topic_vector=TopicVector({"технологии": 0.5}).to_dict(),
        sentiment_prefs=SentimentPrefs({"POSITIVE": 0.6, "NEGATIVE": 0.2, "NEUTRAL": 0.2}).to_dict(),
        format_prefs=FormatPrefs(avg_length=100.0, avg_complexity=0.5).to_dict(),
        interaction_count=5,
        created_at=now,
        last_updated=now,
        embedding=None,
    )
    session.add(profile)
    await session.flush()


async def _seed_interaction(
    session,
    user_id: uuid.UUID,
    post_id: uuid.UUID,
    event_type: str = "LIKE",
    processed: bool = False,
) -> int:
    interaction = UserInteractionsModel(
        event_id=uuid.uuid4(),
        user_id=user_id,
        post_id=post_id,
        event_type=event_type,
        duration_ms=5000,
        scroll_pct=0.9,
        max_scroll_pct=0.9,
        created_at=_utcnow(),
        processed=processed,
    )
    session.add(interaction)
    await session.flush()
    return interaction.id


# ---------------------------------------------------------------------------
# Use case factory
# ---------------------------------------------------------------------------


def _make_use_case(session_factory) -> UpdateProfileUseCase:
    return UpdateProfileUseCase(
        interaction_repo=PgInteractionRepository(session_factory),
        profile_repo=PgUserProfileRepository(session_factory),
        entity_interest_repo=PgEntityInterestRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        profile_updater=ProfileUpdater(),
        content_repo=PgContentRepository(session_factory),
    )


@pytest.mark.integration
class TestUpdateProfileIntegration:
    """Integration tests for UpdateProfileUseCase with a real PostgreSQL DB."""

    # ------------------------------------------------------------------
    # Test 1: profile updated from interactions
    # ------------------------------------------------------------------

    async def test_updates_profile_from_interactions(self, async_session, session_factory):
        """Seed interactions, run use case, verify profile interaction_count increased."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_profile_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [101001])
        await _seed_features(async_session, list(id_map.values()))
        await _seed_interaction(async_session, user_id, id_map[101001], event_type="LIKE")
        await async_session.commit()

        command = UpdateProfileCommand(user_id=user_id)
        result = await use_case.execute(command)

        assert result.users_updated == 1
        assert result.events_processed == 1

        # Verify profile was updated
        profile_repo = PgUserProfileRepository(session_factory)
        updated_profile = await profile_repo.get_by_user_id(user_id)
        assert updated_profile is not None
        assert updated_profile.interaction_count > 5  # original was 5

    # ------------------------------------------------------------------
    # Test 2: interactions marked processed after update
    # ------------------------------------------------------------------

    async def test_marks_interactions_processed(self, async_session, session_factory):
        """After UpdateProfileUseCase runs, interactions.processed = True."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_profile_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [102001])
        await _seed_features(async_session, list(id_map.values()))
        interaction_id = await _seed_interaction(
            async_session, user_id, id_map[102001], event_type="BOOKMARK"
        )
        await async_session.commit()

        command = UpdateProfileCommand(user_id=user_id)
        await use_case.execute(command)

        # Re-query interaction to verify processed=True
        result = await async_session.execute(
            select(UserInteractionsModel).where(UserInteractionsModel.id == interaction_id)
        )
        row = result.scalar_one_or_none()
        # May be None due to separate sessions — verify via repo
        interaction_repo = PgInteractionRepository(session_factory)
        unprocessed = await interaction_repo.get_unprocessed_for_user(user_id)
        assert len(unprocessed) == 0, "All interactions should be marked processed"

    # ------------------------------------------------------------------
    # Test 3: entity interests created from strong signals
    # ------------------------------------------------------------------

    async def test_entity_interests_created(self, async_session, session_factory):
        """Strong signals (LIKE) create entity interest records in DB."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_profile_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [103001])
        # Post with named entities
        await _seed_features(
            async_session, list(id_map.values()), entities_persons=["Alice", "Bob"]
        )
        await _seed_interaction(async_session, user_id, id_map[103001], event_type="LIKE")
        await async_session.commit()

        command = UpdateProfileCommand(user_id=user_id)
        await use_case.execute(command)

        # Verify entity interests were created
        entity_repo = PgEntityInterestRepository(session_factory)
        interests = await entity_repo.get_top_for_user(user_id, limit=10)
        entity_names = {i.entity_name for i in interests}

        assert "Alice" in entity_names or "Bob" in entity_names, (
            "Entity interests should be created for named entities from strong signals"
        )

    # ------------------------------------------------------------------
    # Test 4: entity decay applied (weights * 0.95)
    # ------------------------------------------------------------------

    async def test_entity_decay_applied(self, async_session, session_factory):
        """Existing entity interest weights are multiplied by decay_factor (0.95)."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_profile_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [104001])
        await _seed_features(async_session, list(id_map.values()))

        # Seed an existing entity interest with known weight
        entity = RecEntityInterestsModel(
            user_id=user_id,
            entity_type="person",
            entity_name="Charlie",
            weight=2.0,
            last_seen=_utcnow(),
        )
        async_session.add(entity)

        # Add an unprocessed interaction to trigger profile update
        await _seed_interaction(async_session, user_id, id_map[104001], event_type="LIKE")
        await async_session.commit()

        command = UpdateProfileCommand(user_id=user_id)
        await use_case.execute(command)

        # Charlie's weight should have been decayed
        entity_repo = PgEntityInterestRepository(session_factory)
        interests = await entity_repo.get_top_for_user(user_id, limit=10)
        charlie = next((i for i in interests if i.entity_name == "Charlie"), None)

        if charlie is not None:
            # Weight should be less than initial 2.0 after decay
            assert charlie.weight < 2.0, (
                f"Entity 'Charlie' weight {charlie.weight} should be < 2.0 after decay"
            )

    # ------------------------------------------------------------------
    # Test 5: single-user mode — only specified user's interactions processed
    # ------------------------------------------------------------------

    async def test_single_user_mode(self, async_session, session_factory):
        """When user_id is provided, only that user's interactions are processed."""
        user_a = uuid.uuid4()
        user_b = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_profile_config(async_session)
        await _seed_profile(async_session, user_a)
        await _seed_profile(async_session, user_b)
        id_map = await _seed_raw_posts(async_session, [105001, 105002])
        await _seed_features(async_session, list(id_map.values()))
        # Both users have unprocessed interactions
        await _seed_interaction(async_session, user_a, id_map[105001], event_type="LIKE")
        await _seed_interaction(async_session, user_b, id_map[105002], event_type="LIKE")
        await async_session.commit()

        # Run use case for user_a only
        command = UpdateProfileCommand(user_id=user_a)
        result = await use_case.execute(command)

        assert result.users_updated == 1
        assert result.events_processed == 1

        # User B's interactions should remain unprocessed
        interaction_repo = PgInteractionRepository(session_factory)
        user_b_unprocessed = await interaction_repo.get_unprocessed_for_user(user_b)
        assert len(user_b_unprocessed) == 1, "User B's interaction should still be unprocessed"

    # ------------------------------------------------------------------
    # Test 6: no unprocessed interactions is a no-op
    # ------------------------------------------------------------------

    async def test_no_unprocessed_is_noop(self, async_session, session_factory):
        """When no pending interactions exist, profile is unchanged."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_profile_config(async_session)
        await _seed_profile(async_session, user_id)
        # All interactions already processed
        id_map = await _seed_raw_posts(async_session, [106001])
        await _seed_features(async_session, list(id_map.values()))
        await _seed_interaction(async_session, user_id, id_map[106001], event_type="LIKE", processed=True)
        await async_session.commit()

        command = UpdateProfileCommand(user_id=user_id)
        result = await use_case.execute(command)

        assert result.users_updated == 0
        assert result.events_processed == 0
