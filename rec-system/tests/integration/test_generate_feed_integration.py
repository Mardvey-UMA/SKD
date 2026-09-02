"""Integration tests for GenerateFeedUseCase with a real PostgreSQL DB.

Task 49 — Phase 13: Integration Tests.
Depends on: Task 25 (GenerateFeedUseCase), Tasks 30-35 (all repo adapters).

NLP services (TopicClassifier, etc.) are mocked.
Domain services (Scorer, DiversityFilter, SignalClassifier, ProfileUpdater) are REAL.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Dict, List

import pytest
import json

from sqlalchemy import text

from src.application.dto.generate_feed import GenerateFeedRequest
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.domain.services.diversity_filter import DiversityFilter
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.scorer import Scorer
from src.domain.services.signal_classifier import SignalClassifier
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector
from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
from src.infrastructure.persistence.models.raw_content import RawContentModel
from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
from src.infrastructure.persistence.models.user_disliked_posts import UserDislikedPostsModel
from src.infrastructure.persistence.models.user_interactions import UserInteractionsModel
from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
from src.infrastructure.persistence.pg_content_repository import PgContentRepository
from src.infrastructure.persistence.pg_disliked_posts_repository import PgDislikedPostsRepository
from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository
from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository
from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository


# ---------------------------------------------------------------------------
# Config values matching migration seed data
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

# Feed config: includes both feed-generation params and scoring weights
_FEED_CONFIG = {
    "max_age_hours": 168,  # 7 days
    "freshness_candidate_limit": 300,
    "embedding_candidate_limit": 200,
    "max_topic_streak": 3,
    "max_topic_ratio": 0.40,
    "freshness_halflife_hours": 48,
    "scoring_weights": {
        "topic_match": 0.30,
        "embedding_sim": 0.25,
        "entity_match": 0.15,
        "sentiment_match": 0.05,
        "freshness": 0.15,
        "format_match": 0.10,
    },
}


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


# ---------------------------------------------------------------------------
# DB seed helpers
# ---------------------------------------------------------------------------


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
    embedding: List[float] | None = None,
    topic_1: str = "технологии",
) -> None:
    """Insert features for the given post UUIDs."""
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
            entities_persons=[],
            entities_organizations=[],
            entities_locations=[],
            embedding=embedding,
            processed_at=_utcnow(),
        )
        session.add(feat)
    await session.flush()


async def _seed_profile(
    session,
    user_id: uuid.UUID,
    embedding: List[float] | None = None,
) -> None:
    now = _utcnow()
    profile = RecProfilesModel(
        user_id=user_id,
        topic_vector=TopicVector({"технологии": 0.8, "наука": 0.2}).to_dict(),
        sentiment_prefs=SentimentPrefs(
            {"POSITIVE": 0.6, "NEGATIVE": 0.2, "NEUTRAL": 0.2}
        ).to_dict(),
        format_prefs=FormatPrefs(avg_length=100.0, avg_complexity=0.5).to_dict(),
        interaction_count=10,
        created_at=now,
        last_updated=now,
        embedding=embedding,
    )
    session.add(profile)
    await session.flush()


async def _seed_feed_config(session) -> None:
    """Upsert the 'feed' config key used by GenerateFeedUseCase."""
    await session.execute(
        text(
            """
            INSERT INTO data_flow.rec_config (key, value)
            VALUES ('feed', CAST(:value AS jsonb))
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
            """
        ),
        {"value": json.dumps(_FEED_CONFIG)},
    )
    await session.flush()


async def _seed_interaction(
    session,
    user_id: uuid.UUID,
    post_id: uuid.UUID,
    event_type: str = "LIKE",
    processed: bool = False,
) -> None:
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


# ---------------------------------------------------------------------------
# Use case factory with real repos and real domain services
# ---------------------------------------------------------------------------


def _make_use_case(session_factory) -> GenerateFeedUseCase:
    return GenerateFeedUseCase(
        profile_repo=PgUserProfileRepository(session_factory),
        content_repo=PgContentRepository(session_factory),
        entity_interest_repo=PgEntityInterestRepository(session_factory),
        interaction_repo=PgInteractionRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        disliked_posts_repo=PgDislikedPostsRepository(session_factory),
        scorer=Scorer(),
        diversity_filter=DiversityFilter(),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        profile_updater=ProfileUpdater(),
    )


@pytest.mark.integration
class TestGenerateFeedIntegration:
    """Integration tests for GenerateFeedUseCase with a real PostgreSQL DB."""

    # ------------------------------------------------------------------
    # Test 1: generates feed for an onboarded user with existing posts
    # ------------------------------------------------------------------

    async def test_generates_feed_for_onboarded_user(self, async_session, session_factory):
        """User with profile, seeded posts — returns scored feed."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_feed_config(async_session)
        await _seed_profile(async_session, user_id, embedding=[0.1] * 312)
        id_map = await _seed_raw_posts(async_session, [10001, 10002, 10003])
        await _seed_features(async_session, list(id_map.values()), embedding=[0.1] * 312)
        await async_session.commit()

        request = GenerateFeedRequest(user_id=user_id, feed_size=10)
        response = await use_case.execute(request)

        assert response.feed is not None
        assert len(response.feed) > 0
        assert response.meta["total"] == len(response.feed)

    # ------------------------------------------------------------------
    # Test 2: feed excludes disliked posts
    # ------------------------------------------------------------------

    async def test_feed_excludes_disliked_posts(self, async_session, session_factory):
        """Disliked posts are not included in the feed."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_feed_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [20001, 20002, 20003])
        await _seed_features(async_session, list(id_map.values()))

        # Mark post 20001 (by its UUID) as disliked
        disliked_uuid = id_map[20001]
        disliked = UserDislikedPostsModel(user_id=user_id, post_id=disliked_uuid)
        async_session.add(disliked)
        await async_session.flush()
        await async_session.commit()

        request = GenerateFeedRequest(user_id=user_id, feed_size=20)
        response = await use_case.execute(request)

        feed_post_ids = {item["post_id"] for item in response.feed}
        assert disliked_uuid not in feed_post_ids, "Disliked post should not be in feed"

    # ------------------------------------------------------------------
    # Test 3: feed respects size limit
    # ------------------------------------------------------------------

    async def test_feed_respects_size_limit(self, async_session, session_factory):
        """feed.total <= feed_size even when many candidates exist."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_feed_config(async_session)
        await _seed_profile(async_session, user_id)
        post_ids = list(range(30001, 30016))
        id_map = await _seed_raw_posts(async_session, post_ids)
        await _seed_features(async_session, list(id_map.values()))
        await async_session.commit()

        feed_size = 5
        request = GenerateFeedRequest(user_id=user_id, feed_size=feed_size)
        response = await use_case.execute(request)

        assert response.meta["total"] <= feed_size

    # ------------------------------------------------------------------
    # Test 4: cold-start user gets feed (no embedding)
    # ------------------------------------------------------------------

    async def test_cold_start_user_gets_feed(self, async_session, session_factory):
        """User with no embedding still gets a feed via freshness candidates."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_feed_config(async_session)
        # Profile with NO embedding (cold start)
        await _seed_profile(async_session, user_id, embedding=None)
        id_map = await _seed_raw_posts(async_session, [40001, 40002, 40003])
        await _seed_features(async_session, list(id_map.values()))
        await async_session.commit()

        request = GenerateFeedRequest(user_id=user_id, feed_size=10)
        response = await use_case.execute(request)

        # Cold-start user still gets feed from freshness candidates
        assert response.feed is not None
        assert len(response.feed) > 0

    # ------------------------------------------------------------------
    # Test 5: pending events processed before feed generation
    # ------------------------------------------------------------------

    async def test_processes_pending_events_before_feed(self, async_session, session_factory):
        """Unprocessed interactions are processed inline before feed is generated."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_feed_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [50001])
        await _seed_features(async_session, list(id_map.values()))
        # Add an unprocessed LIKE interaction
        await _seed_interaction(
            async_session, user_id, id_map[50001], event_type="LIKE", processed=False
        )
        await async_session.commit()

        request = GenerateFeedRequest(user_id=user_id, feed_size=10)
        response = await use_case.execute(request)

        # At least 1 event was processed inline
        assert response.meta["profile_events_processed"] >= 1

    # ------------------------------------------------------------------
    # Test 6: feed meta populated
    # ------------------------------------------------------------------

    async def test_feed_meta_populated(self, async_session, session_factory):
        """Meta has all required fields."""
        user_id = uuid.uuid4()
        use_case = _make_use_case(session_factory)

        await _seed_feed_config(async_session)
        await _seed_profile(async_session, user_id)
        id_map = await _seed_raw_posts(async_session, [60001, 60002])
        await _seed_features(async_session, list(id_map.values()))
        await async_session.commit()

        request = GenerateFeedRequest(user_id=user_id, feed_size=10)
        response = await use_case.execute(request)

        required_meta_keys = {
            "total",
            "candidates_scored",
            "profile_events_processed",
            "generation_time_ms",
        }
        assert required_meta_keys.issubset(set(response.meta.keys())), (
            f"Missing meta keys: {required_meta_keys - set(response.meta.keys())}"
        )
