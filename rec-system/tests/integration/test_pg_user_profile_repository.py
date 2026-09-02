"""Integration tests for PgUserProfileRepository against a real PostgreSQL DB.

Task 48 — Phase 13: Integration Tests.
Depends on: Task 31 (PgUserProfileRepository implementation).
"""
from __future__ import annotations

import uuid
from datetime import datetime

import pytest

from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector
from src.infrastructure.persistence.pg_user_profile_repository import (
    PgUserProfileRepository,
)


# ---------------------------------------------------------------------------
# Helper: build a minimal UserProfile
# ---------------------------------------------------------------------------


def _make_profile(
    user_id: uuid.UUID | None = None,
    interaction_count: int = 5,
    embedding: list[float] | None = None,
    topic_weights: dict | None = None,
) -> UserProfile:
    if user_id is None:
        user_id = uuid.uuid4()
    if topic_weights is None:
        topic_weights = {"технологии": 0.8, "спорт": 0.2}

    now = datetime.utcnow()
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector(topic_weights),
        sentiment_prefs=SentimentPrefs({"POSITIVE": 0.6, "NEGATIVE": 0.2, "NEUTRAL": 0.2}),
        format_prefs=FormatPrefs(avg_length=300.0, avg_complexity=0.4),
        interaction_count=interaction_count,
        created_at=now,
        last_updated=now,
        embedding=embedding,
    )


@pytest.mark.integration
class TestPgUserProfileRepositoryIntegration:
    """Integration tests for PgUserProfileRepository with a real PostgreSQL DB."""

    @pytest.fixture
    def repo(self, session_factory) -> PgUserProfileRepository:
        return PgUserProfileRepository(session_factory)

    # ------------------------------------------------------------------
    # Test 1: save and get profile round-trip
    # ------------------------------------------------------------------

    async def test_save_and_get_profile(self, repo):
        """Save a profile and retrieve it by user_id, all fields intact."""
        user_id = uuid.uuid4()
        profile = _make_profile(user_id=user_id, interaction_count=10)

        await repo.save(profile)
        retrieved = await repo.get_by_user_id(user_id)

        assert retrieved is not None
        assert retrieved.user_id == user_id
        assert retrieved.interaction_count == 10
        assert retrieved.format_prefs.avg_length == pytest.approx(300.0, abs=1e-4)
        assert retrieved.format_prefs.avg_complexity == pytest.approx(0.4, abs=1e-4)

    # ------------------------------------------------------------------
    # Test 2: TopicVector JSONB round-trip
    # ------------------------------------------------------------------

    async def test_topic_vector_round_trip(self, repo):
        """JSONB TopicVector stored and retrieved preserves normalized values."""
        user_id = uuid.uuid4()
        # Provide specific weights — TopicVector normalizes them
        raw_weights = {"технологии": 3.0, "наука": 1.0}
        profile = _make_profile(user_id=user_id, topic_weights=raw_weights)
        expected_tv = TopicVector(raw_weights)

        await repo.save(profile)
        retrieved = await repo.get_by_user_id(user_id)

        assert retrieved is not None
        for topic in TopicVector.TOPICS:
            assert retrieved.topic_vector.get(topic) == pytest.approx(
                expected_tv.get(topic), abs=1e-5
            ), f"Mismatch for topic '{topic}'"

    # ------------------------------------------------------------------
    # Test 3: null embedding round-trip
    # ------------------------------------------------------------------

    async def test_null_embedding_round_trip(self, repo):
        """Profile saved with None embedding returns None on retrieval."""
        user_id = uuid.uuid4()
        profile = _make_profile(user_id=user_id, embedding=None)

        await repo.save(profile)
        retrieved = await repo.get_by_user_id(user_id)

        assert retrieved is not None
        assert retrieved.embedding is None

    # ------------------------------------------------------------------
    # Test 4: 312-dim embedding round-trip
    # ------------------------------------------------------------------

    async def test_embedding_round_trip(self, repo):
        """Profile saved with a 312-dim embedding returns identical vector."""
        user_id = uuid.uuid4()
        original_embedding = [float(i) / 312.0 for i in range(312)]
        profile = _make_profile(user_id=user_id, embedding=original_embedding)

        await repo.save(profile)
        retrieved = await repo.get_by_user_id(user_id)

        assert retrieved is not None
        assert retrieved.embedding is not None
        assert len(retrieved.embedding) == 312
        for orig, got in zip(original_embedding, retrieved.embedding):
            assert abs(orig - got) < 1e-4, f"Embedding mismatch: {orig} vs {got}"

    # ------------------------------------------------------------------
    # Test 5: update existing profile (upsert)
    # ------------------------------------------------------------------

    async def test_update_existing_profile(self, repo):
        """Save, then save again with updated fields; verify the updated values."""
        user_id = uuid.uuid4()
        profile_v1 = _make_profile(user_id=user_id, interaction_count=1)
        await repo.save(profile_v1)

        # Update
        now = datetime.utcnow()
        profile_v2 = UserProfile(
            user_id=user_id,
            topic_vector=TopicVector({"спорт": 1.0}),
            sentiment_prefs=SentimentPrefs({"POSITIVE": 0.9, "NEGATIVE": 0.05, "NEUTRAL": 0.05}),
            format_prefs=FormatPrefs(avg_length=500.0, avg_complexity=0.8),
            interaction_count=42,
            created_at=profile_v1.created_at,
            last_updated=now,
            embedding=[0.5] * 312,
        )
        await repo.save(profile_v2)

        retrieved = await repo.get_by_user_id(user_id)

        assert retrieved is not None
        assert retrieved.interaction_count == 42
        assert retrieved.format_prefs.avg_length == pytest.approx(500.0, abs=1e-4)
        assert retrieved.format_prefs.avg_complexity == pytest.approx(0.8, abs=1e-4)
        assert retrieved.embedding is not None
        assert len(retrieved.embedding) == 312

    # ------------------------------------------------------------------
    # Test 6: get nonexistent profile returns None
    # ------------------------------------------------------------------

    async def test_get_nonexistent_returns_none(self, repo):
        """Looking up an unknown user_id returns None."""
        unknown_id = uuid.uuid4()
        result = await repo.get_by_user_id(unknown_id)
        assert result is None
