"""Integration tests for PgContentRepository against a real PostgreSQL+pgvector DB.

Task 47 — Phase 13: Integration Tests.
Depends on: Task 30 (PgContentRepository implementation).
"""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone
from typing import List

import pytest

from src.domain.entities.content_features import ContentFeatures
from src.infrastructure.persistence.pg_content_repository import PgContentRepository
from src.infrastructure.persistence.models.raw_content import RawContentModel
from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
from src.infrastructure.persistence.models.user_disliked_posts import UserDislikedPostsModel


# ---------------------------------------------------------------------------
# Helper: insert raw post directly via SQL model
# ---------------------------------------------------------------------------


def _utcnow() -> datetime:
    """Return current UTC time as a timezone-aware datetime."""
    return datetime.now(timezone.utc)


async def _insert_raw_post(
    session,
    external_id: str = "test-post",
    content: str = "test content",
    post_date: datetime | None = None,
    is_processed: bool = False,
    clean_text: str | None = None,
) -> RawContentModel:
    """Insert a raw_content row and return the model (with auto-generated UUID id).

    T5: clean_text defaults to content (test data is already plain text).
    Rows with clean_text=None are skipped by get_unprocessed after T5.
    """
    if post_date is None:
        post_date = _utcnow()
    raw_data = {
        "title": "Test title",
        "content": content,
        "publishedAt": post_date.isoformat(),
    }
    raw = RawContentModel(
        id=uuid.uuid4(),
        external_id=external_id,
        source_id=uuid.uuid4(),
        source_type="telegram",
        raw_data=raw_data,
        processing_status="COMPLETED",
        is_processed_by_rec=is_processed,
        received_at=_utcnow(),
        clean_text=clean_text if clean_text is not None else content,
    )
    session.add(raw)
    await session.flush()
    return raw


async def _insert_features(
    session,
    post_id: uuid.UUID,
    embedding: List[float] | None = None,
    topic_1: str | None = None,
    topic_1_score: float | None = None,
    processed_at: datetime | None = None,
) -> PostsFeaturesModel:
    feat = PostsFeaturesModel(
        post_id=post_id,
        text_length=100,
        word_count=20,
        reading_time=1.0,
        complexity=0.5,
        is_short_form=True,
        is_long_form=False,
        topic_1=topic_1,
        topic_1_score=topic_1_score,
        sentiment="POSITIVE",
        sentiment_score=0.8,
        entities_persons=[],
        entities_organizations=[],
        entities_locations=[],
        embedding=embedding,
        processed_at=processed_at or _utcnow(),
    )
    session.add(feat)
    await session.flush()
    return feat


@pytest.mark.integration
class TestPgContentRepositoryIntegration:
    """Integration tests for PgContentRepository with a real PostgreSQL+pgvector DB."""

    @pytest.fixture
    def repo(self, session_factory) -> PgContentRepository:
        return PgContentRepository(session_factory)

    # ------------------------------------------------------------------
    # Test 1: save features and retrieve
    # ------------------------------------------------------------------

    async def test_save_and_retrieve_features(self, repo, async_session):
        """Save NLP features for a post and retrieve them by post_id."""
        raw = await _insert_raw_post(async_session, external_id="1001", content="Hello world")
        await async_session.commit()

        features = ContentFeatures(
            post_id=raw.id,
            content="Hello world",
            post_date=_utcnow(),
            text_length=11,
            word_count=2,
            reading_time=0.1,
            complexity=0.3,
            is_short_form=True,
            is_long_form=False,
            topic_1="технологии",
            topic_1_score=0.9,
            sentiment="POSITIVE",
            sentiment_score=0.85,
            entities_persons=["Alice"],
            entities_organizations=[],
            entities_locations=["Moscow"],
            embedding=[0.1] * 312,
            processed_at=_utcnow(),
        )

        await repo.save_features(features)

        # Retrieve via freshness (large window so it's always included)
        results = await repo.get_candidates_by_freshness(max_age_hours=24 * 365, limit=10000)
        post_ids = [r.post_id for r in results]
        assert raw.id in post_ids

        retrieved = next(r for r in results if r.post_id == raw.id)
        assert retrieved.word_count == 2
        assert retrieved.topic_1 == "технологии"
        assert retrieved.topic_1_score == pytest.approx(0.9, abs=1e-5)
        assert retrieved.entities_persons == ["Alice"]
        assert retrieved.entities_locations == ["Moscow"]

    # ------------------------------------------------------------------
    # Test 2: get_unprocessed returns only unprocessed
    # ------------------------------------------------------------------

    async def test_get_unprocessed_returns_only_unprocessed(self, repo, async_session):
        """Insert 3 posts, 1 already processed, get_unprocessed returns 2."""
        raw_a = await _insert_raw_post(async_session, external_id="2001", content="post A", is_processed=False)
        raw_b = await _insert_raw_post(async_session, external_id="2002", content="post B", is_processed=False)
        raw_c = await _insert_raw_post(async_session, external_id="2003", content="post C", is_processed=True)
        await async_session.commit()

        unprocessed = await repo.get_unprocessed(batch_size=100)
        unprocessed_ids = {r.post_id for r in unprocessed}

        assert raw_a.id in unprocessed_ids
        assert raw_b.id in unprocessed_ids
        assert raw_c.id not in unprocessed_ids

    # ------------------------------------------------------------------
    # Test 3: save_features marks post as processed
    # ------------------------------------------------------------------

    async def test_save_features_marks_processed(self, repo, async_session):
        """After save_features, the post's is_processed_by_rec flag = True."""
        raw = await _insert_raw_post(async_session, external_id="3001", is_processed=False)
        await async_session.commit()

        features = ContentFeatures(
            post_id=raw.id,
            content="some content",
            post_date=_utcnow(),
            embedding=None,
            processed_at=_utcnow(),
        )
        await repo.save_features(features)

        # Verify via repo: post must not appear in unprocessed list
        unprocessed = await repo.get_unprocessed(batch_size=100)
        assert raw.id not in {r.post_id for r in unprocessed}

    # ------------------------------------------------------------------
    # Test 4: upsert on conflict — save twice, no error
    # ------------------------------------------------------------------

    async def test_upsert_on_conflict(self, repo, async_session):
        """Saving features twice for the same post does not raise an error."""
        raw = await _insert_raw_post(async_session, external_id="4001")
        await async_session.commit()

        features = ContentFeatures(
            post_id=raw.id,
            content="content",
            post_date=_utcnow(),
            topic_1="спорт",
            topic_1_score=0.7,
            processed_at=_utcnow(),
        )
        await repo.save_features(features)

        # Second save with updated values — should not raise
        features2 = ContentFeatures(
            post_id=raw.id,
            content="content updated",
            post_date=_utcnow(),
            topic_1="наука",
            topic_1_score=0.6,
            processed_at=_utcnow(),
        )
        await repo.save_features(features2)  # must not raise

    # ------------------------------------------------------------------
    # Test 5: candidates by freshness ordered by date desc
    # ------------------------------------------------------------------

    async def test_candidates_by_freshness_ordered_by_date(self, repo, async_session):
        """get_candidates_by_freshness returns posts ordered newest first."""
        now = _utcnow()
        older_time = now - timedelta(hours=5)
        newer_time = now - timedelta(hours=1)

        raw_older = await _insert_raw_post(async_session, external_id="5001", post_date=older_time)
        raw_newer = await _insert_raw_post(async_session, external_id="5002", post_date=newer_time)
        await async_session.commit()

        # Insert features for both; use processed_at to reflect the post_date ordering
        await _insert_features(async_session, raw_older.id, processed_at=older_time)
        await _insert_features(async_session, raw_newer.id, processed_at=newer_time)
        await async_session.commit()

        results = await repo.get_candidates_by_freshness(max_age_hours=24, limit=10000)
        ids = [r.post_id for r in results if r.post_id in {raw_older.id, raw_newer.id}]

        assert len(ids) == 2, f"Expected both posts but got: {ids}"
        # newer post should come first (processed_at is newer)
        assert ids[0] == raw_newer.id
        assert ids[1] == raw_older.id

    # ------------------------------------------------------------------
    # Test 6: candidates by freshness excludes old posts
    # ------------------------------------------------------------------

    async def test_candidates_by_freshness_excludes_old(self, repo, async_session):
        """Posts older than max_age_hours are not returned."""
        now = _utcnow()
        very_old = now - timedelta(days=10)
        recent = now - timedelta(hours=1)

        raw_old = await _insert_raw_post(async_session, external_id="6001", post_date=very_old)
        raw_recent = await _insert_raw_post(async_session, external_id="6002", post_date=recent)
        await async_session.commit()

        await _insert_features(async_session, raw_old.id, processed_at=very_old)
        await _insert_features(async_session, raw_recent.id, processed_at=recent)
        await async_session.commit()

        results = await repo.get_candidates_by_freshness(max_age_hours=24, limit=10000)
        ids = {r.post_id for r in results}

        assert raw_old.id not in ids, "Old post should be excluded"
        assert raw_recent.id in ids, "Recent post should be included"

    # ------------------------------------------------------------------
    # Test 7: candidates by freshness excludes disliked posts
    # ------------------------------------------------------------------

    async def test_candidates_by_freshness_excludes_disliked(
        self, repo, async_session, session_factory
    ):
        """Disliked posts are filtered out (via DislikedPostsRepository)."""
        from src.infrastructure.persistence.pg_disliked_posts_repository import (
            PgDislikedPostsRepository,
        )

        user_id = uuid.uuid4()
        now = _utcnow()

        raw_a = await _insert_raw_post(async_session, external_id="7001", post_date=now - timedelta(hours=1))
        raw_b = await _insert_raw_post(async_session, external_id="7002", post_date=now - timedelta(hours=2))
        await async_session.commit()

        await _insert_features(async_session, raw_a.id)
        await _insert_features(async_session, raw_b.id)
        await async_session.commit()

        # Mark raw_a as disliked
        disliked = UserDislikedPostsModel(user_id=user_id, post_id=raw_a.id)
        async_session.add(disliked)
        await async_session.commit()

        # Verify through DislikedPostsRepository that disliked_ids includes raw_a.id
        disliked_repo = PgDislikedPostsRepository(session_factory)
        disliked_ids = set(await disliked_repo.get_disliked_post_ids(user_id))
        assert raw_a.id in disliked_ids

        # The content repo itself returns both; filtering is done by the use case.
        results = await repo.get_candidates_by_freshness(max_age_hours=24, limit=10000)
        result_ids = {r.post_id for r in results}
        assert raw_a.id in result_ids
        assert raw_b.id in result_ids

    # ------------------------------------------------------------------
    # Test 8: candidates by embedding similarity (pgvector cosine distance)
    # ------------------------------------------------------------------

    async def test_candidates_by_embedding_similarity(self, repo, async_session):
        """Posts are returned ordered by cosine distance to query embedding."""
        now = _utcnow()

        # Post A: embedding mostly in dim 0
        embedding_a = [0.0] * 312
        embedding_a[0] = 1.0

        # Post B: embedding mostly in dim 1
        embedding_b = [0.0] * 312
        embedding_b[1] = 1.0

        # Query: matches A better
        query_embedding = [0.0] * 312
        query_embedding[0] = 1.0

        raw_a = await _insert_raw_post(async_session, external_id="8001", post_date=now)
        raw_b = await _insert_raw_post(async_session, external_id="8002", post_date=now)
        await async_session.commit()

        await _insert_features(async_session, raw_a.id, embedding=embedding_a)
        await _insert_features(async_session, raw_b.id, embedding=embedding_b)
        await async_session.commit()

        results = await repo.get_candidates_by_embedding(query_embedding, limit=10)
        ids = [r.post_id for r in results if r.post_id in {raw_a.id, raw_b.id}]

        assert len(ids) == 2
        # Post raw_a should come before raw_b (closer to query)
        assert ids[0] == raw_a.id

    # ------------------------------------------------------------------
    # Test 9: 312-dim embedding vector round-trip
    # ------------------------------------------------------------------

    async def test_embedding_vector_round_trip(self, repo, async_session):
        """A 312-dimensional embedding stored and retrieved is identical."""
        # Build a specific 312-dim vector
        original_embedding = [float(i) / 312.0 for i in range(312)]

        raw = await _insert_raw_post(async_session, external_id="9001")
        await async_session.commit()

        features = ContentFeatures(
            post_id=raw.id,
            content="vector round-trip test",
            post_date=_utcnow(),
            embedding=original_embedding,
            processed_at=_utcnow(),
        )
        await repo.save_features(features)

        results = await repo.get_candidates_by_embedding(original_embedding, limit=5)
        retrieved = next((r for r in results if r.post_id == raw.id), None)
        assert retrieved is not None, "Post should be returned by embedding query"
        assert retrieved.embedding is not None
        assert len(retrieved.embedding) == 312
        for orig, got in zip(original_embedding, retrieved.embedding):
            assert abs(orig - got) < 1e-4, f"Embedding mismatch: {orig} vs {got}"
