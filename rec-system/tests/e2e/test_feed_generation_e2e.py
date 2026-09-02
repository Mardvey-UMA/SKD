"""E2E tests for POST /generate-feed endpoint.

Task 52: Full feed generation flow via HTTP with real PostgreSQL.
"""
from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone, timedelta

import pytest
from httpx import AsyncClient
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


async def _seed_rec_config(session: AsyncSession, feed_config: dict | None = None) -> None:
    """Upsert a 'feed' config row used by GenerateFeedUseCase."""
    default_feed = {
        "max_age_hours": 168,
        "freshness_candidate_limit": 50,
        "embedding_candidate_limit": 50,
    }
    value = feed_config or default_feed
    value_json = json.dumps(value)

    await session.execute(
        text(
            "INSERT INTO rec_config (key, value, description, updated_at) "
            "VALUES ('feed', cast(:val as jsonb), '', NOW()) "
            "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value"
        ).bindparams(val=value_json),
    )
    await session.commit()


async def _seed_user_profile(
    session: AsyncSession,
    user_id: str,
    topic_vector: dict | None = None,
    embedding: list[float] | None = None,
) -> None:
    """Insert a rec_profiles row for the given user."""
    tv = topic_vector or {"technology": 0.8, "science": 0.6}
    sentiment_prefs = {"positive": 0.5, "neutral": 0.3, "negative": 0.2}
    format_prefs = {"avg_length": 300.0, "avg_complexity": 0.5}

    await session.execute(
        text(
            "INSERT INTO rec_profiles "
            "    (user_id, topic_vector, embedding, sentiment_prefs, format_prefs, interaction_count, created_at, last_updated) "
            "VALUES "
            "    (cast(:uid as uuid), cast(:tv as jsonb), NULL, cast(:sp as jsonb), cast(:fp as jsonb), 0, NOW(), NOW()) "
            "ON CONFLICT (user_id) DO UPDATE "
            "    SET topic_vector = EXCLUDED.topic_vector, "
            "        last_updated = EXCLUDED.last_updated"
        ).bindparams(
            uid=user_id,
            tv=json.dumps(tv),
            sp=json.dumps(sentiment_prefs),
            fp=json.dumps(format_prefs),
        ),
    )
    await session.commit()


async def _seed_post(
    session: AsyncSession,
    post_id: int,
    post_date: datetime | None = None,
    topic_1: str = "technology",
    topic_1_score: float = 0.9,
) -> None:
    """Insert a posts_raw + posts_features row."""
    if post_date is None:
        post_date = datetime.now(timezone.utc) - timedelta(hours=1)

    await session.execute(
        text(
            "INSERT INTO posts_raw (id, channel_id, content, post_date, is_processed) "
            "VALUES (:post_id, 1, 'Test content', :post_date, TRUE) "
            "ON CONFLICT (id) DO NOTHING"
        ).bindparams(post_id=post_id, post_date=post_date),
    )

    await session.execute(
        text(
            "INSERT INTO posts_features "
            "    (post_id, text_length, word_count, reading_time, complexity, "
            "     is_short_form, is_long_form, "
            "     topic_1, topic_1_score, sentiment, sentiment_score, "
            "     entities_persons, entities_organizations, entities_locations, "
            "     processed_at) "
            "VALUES "
            "    (:post_id, 100, 20, 1.0, 0.5, "
            "     TRUE, FALSE, "
            "     :topic_1, :t1score, 'positive', 0.8, "
            "     cast('[]' as jsonb), cast('[]' as jsonb), cast('[]' as jsonb), "
            "     NOW()) "
            "ON CONFLICT (post_id) DO NOTHING"
        ).bindparams(post_id=post_id, topic_1=topic_1, t1score=topic_1_score),
    )
    await session.commit()


async def _delete_posts(session: AsyncSession, post_ids: list[int]) -> None:
    """Clean up seeded posts after test."""
    for pid in post_ids:
        await session.execute(
            text("DELETE FROM posts_features WHERE post_id = :pid").bindparams(pid=pid)
        )
        await session.execute(
            text("DELETE FROM posts_raw WHERE id = :pid").bindparams(pid=pid)
        )
    await session.commit()


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.e2e
class TestFeedGenerationE2E:
    """End-to-end tests for the feed generation flow."""

    @pytest.mark.asyncio
    async def test_generate_feed_returns_scored_list(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """POST /generate-feed returns a feed list with post_id and score pairs."""
        user_id = str(uuid.uuid4())
        post_ids = [900001, 900002, 900003]

        await _seed_rec_config(e2e_db_session)
        await _seed_user_profile(e2e_db_session, user_id)
        for pid in post_ids:
            await _seed_post(e2e_db_session, pid)

        try:
            response = await e2e_client.post(
                "/generate-feed",
                json={"user_id": user_id, "feed_size": 10},
            )
            assert response.status_code == 200
            body = response.json()
            assert "feed" in body
            assert isinstance(body["feed"], list)
            assert len(body["feed"]) > 0

            item = body["feed"][0]
            assert "post_id" in item
            assert "score" in item
            assert isinstance(item["post_id"], int)
            assert isinstance(item["score"], float)
        finally:
            await _delete_posts(e2e_db_session, post_ids)

    @pytest.mark.asyncio
    async def test_response_matches_api_contract(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """Feed response has feed list + meta with all 4 required fields."""
        user_id = str(uuid.uuid4())
        post_ids = [900010, 900011]

        await _seed_rec_config(e2e_db_session)
        await _seed_user_profile(e2e_db_session, user_id)
        for pid in post_ids:
            await _seed_post(e2e_db_session, pid)

        try:
            response = await e2e_client.post(
                "/generate-feed",
                json={"user_id": user_id, "feed_size": 10},
            )
            assert response.status_code == 200
            body = response.json()

            # Top-level keys
            assert "feed" in body
            assert "meta" in body

            meta = body["meta"]
            assert "feed_size" in meta
            assert "user_id" in meta
            assert "total" in meta
            assert "candidates_scored" in meta
            assert "profile_events_processed" in meta
            assert "generation_time_ms" in meta
        finally:
            await _delete_posts(e2e_db_session, post_ids)

    @pytest.mark.asyncio
    async def test_feed_sorted_by_score_desc(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """Feed items are returned in descending score order."""
        user_id = str(uuid.uuid4())
        post_ids = [900020, 900021, 900022, 900023, 900024]

        await _seed_rec_config(e2e_db_session)
        await _seed_user_profile(e2e_db_session, user_id)
        for i, pid in enumerate(post_ids):
            # Vary topic_1_score to produce different scores
            await _seed_post(e2e_db_session, pid, topic_1_score=0.9 - i * 0.1)

        try:
            response = await e2e_client.post(
                "/generate-feed",
                json={"user_id": user_id, "feed_size": 20},
            )
            assert response.status_code == 200
            feed = response.json()["feed"]

            if len(feed) > 1:
                scores = [item["score"] for item in feed]
                for i in range(len(scores) - 1):
                    assert scores[i] >= scores[i + 1], (
                        f"Feed not sorted desc: score[{i}]={scores[i]} < score[{i+1}]={scores[i+1]}"
                    )
        finally:
            await _delete_posts(e2e_db_session, post_ids)

    @pytest.mark.asyncio
    async def test_meta_has_generation_time(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """meta.generation_time_ms is a non-negative integer."""
        user_id = str(uuid.uuid4())
        post_ids = [900030, 900031]

        await _seed_rec_config(e2e_db_session)
        await _seed_user_profile(e2e_db_session, user_id)
        for pid in post_ids:
            await _seed_post(e2e_db_session, pid)

        try:
            response = await e2e_client.post(
                "/generate-feed",
                json={"user_id": user_id, "feed_size": 10},
            )
            assert response.status_code == 200
            meta = response.json()["meta"]
            assert meta["generation_time_ms"] >= 0
        finally:
            await _delete_posts(e2e_db_session, post_ids)

    @pytest.mark.asyncio
    async def test_empty_corpus_returns_empty_feed(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """When no posts exist for this user, response is 200 with empty feed."""
        user_id = str(uuid.uuid4())

        await _seed_rec_config(e2e_db_session)
        # Seed profile but NO posts
        await _seed_user_profile(e2e_db_session, user_id)

        response = await e2e_client.post(
            "/generate-feed",
            json={"user_id": user_id, "feed_size": 10},
        )

        assert response.status_code == 200
        body = response.json()
        assert body["feed"] == []

    @pytest.mark.asyncio
    async def test_cold_start_user(
        self,
        e2e_client: AsyncClient,
        e2e_db_session: AsyncSession,
    ):
        """User with no profile (cold start) gets 200 with empty feed.

        Cold-start users have no rec_profiles row — GenerateFeedUseCase returns
        an empty feed when profile is None.
        """
        # Use a user_id that has no profile and no interactions
        user_id = str(uuid.uuid4())
        post_ids = [900040, 900041]

        await _seed_rec_config(e2e_db_session)
        # Seed posts but no profile for this user
        for pid in post_ids:
            await _seed_post(e2e_db_session, pid)

        try:
            response = await e2e_client.post(
                "/generate-feed",
                json={"user_id": user_id, "feed_size": 10},
            )
            assert response.status_code == 200
            body = response.json()
            # Cold-start: no profile -> empty feed
            assert body["feed"] == []
        finally:
            await _delete_posts(e2e_db_session, post_ids)
