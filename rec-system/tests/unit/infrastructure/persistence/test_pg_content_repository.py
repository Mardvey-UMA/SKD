"""Unit tests for PgContentRepository -- Task 5: data_flow.raw_content."""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, call
from uuid import uuid4

import pytest

from src.domain.entities.content_features import ContentFeatures
from src.domain.interfaces.content_repository import ContentRepository
from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
from src.infrastructure.persistence.models.raw_content import RawContentModel


class TestPgContentRepository:
    """Tests for PgContentRepository."""

    def test_implements_content_repository(self):
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        repo = PgContentRepository(session_factory=MagicMock())
        assert isinstance(repo, ContentRepository)

    # ------------------------------------------------------------------
    # get_unprocessed
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_unprocessed_polls_raw_content(self):
        """Query must target raw_content with COMPLETED + is_processed_by_rec=False."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_unprocessed(batch_size=10)

        assert isinstance(result, list)
        mock_session.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_unprocessed_maps_raw_content_to_content_features(self):
        """Should map RawContentModel rows to ContentFeatures with UUID post_id."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        post_uuid = uuid4()
        source_uuid = uuid4()
        raw_row = MagicMock(spec=RawContentModel)
        raw_row.id = post_uuid
        raw_row.source_id = source_uuid
        raw_row.source_type = "TELEGRAM"
        raw_row.clean_text = "Hello world"  # T5: read clean_text directly
        raw_row.raw_data = {
            "title": "Test Title",
            "publishedAt": "2024-01-15T10:00:00Z",
        }

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [raw_row]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_unprocessed(batch_size=5)

        assert len(result) == 1
        cf = result[0]
        assert isinstance(cf, ContentFeatures)
        assert cf.post_id == post_uuid
        assert cf.title == "Test Title"
        assert cf.source_type == "TELEGRAM"
        assert cf.source_id == source_uuid

    @pytest.mark.asyncio
    async def test_get_unprocessed_uses_clean_text_directly(self):
        """T5: content comes from clean_text (pre-cleaned), not from strip_html(raw_data['content'])."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        raw_row = MagicMock(spec=RawContentModel)
        raw_row.id = uuid4()
        raw_row.source_type = "RSS"
        raw_row.clean_text = "Plain text"  # already clean — no HTML
        raw_row.raw_data = {
            "title": "T",
            "publishedAt": "2024-01-15T10:00:00Z",
        }

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [raw_row]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_unprocessed(batch_size=5)

        assert len(result) == 1
        assert result[0].content == "Plain text"

    @pytest.mark.asyncio
    async def test_get_unprocessed_filters_null_source_id(self):
        """Phase 1: get_unprocessed SQL must filter source_id IS NOT NULL."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        executed_stmts = []

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []

        async def capture_execute(stmt, *args, **kwargs):
            executed_stmts.append(stmt)
            return mock_result

        mock_session.execute = capture_execute

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        await repo.get_unprocessed(batch_size=10)

        assert len(executed_stmts) == 1
        sql_str = str(executed_stmts[0].compile(compile_kwargs={"literal_binds": False})).lower()
        assert "source_id" in sql_str, (
            f"Expected 'source_id' in SQL, got: {sql_str}"
        )
        # two IS NOT NULL predicates expected: clean_text and source_id
        assert sql_str.count("is not null") >= 2, (
            f"Expected two IS NOT NULL filters (clean_text, source_id): {sql_str}"
        )

    @pytest.mark.asyncio
    async def test_get_unprocessed_requires_clean_text_not_null(self):
        """T5: get_unprocessed SQL must filter clean_text IS NOT NULL."""
        import re
        from sqlalchemy import select
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository
        from src.infrastructure.persistence.models.raw_content import RawContentModel

        executed_stmts = []

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []

        async def capture_execute(stmt, *args, **kwargs):
            executed_stmts.append(stmt)
            return mock_result

        mock_session.execute = capture_execute

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        await repo.get_unprocessed(batch_size=10)

        assert len(executed_stmts) == 1
        stmt = executed_stmts[0]
        compiled = stmt.compile(compile_kwargs={"literal_binds": False})
        sql_str = str(compiled).lower()
        assert "clean_text" in sql_str, f"Expected 'clean_text' in SQL, got: {sql_str}"
        assert "is not null" in sql_str or "is_not_null" in sql_str or "notnull" in sql_str or "!= null" in sql_str, (
            f"Expected IS NOT NULL filter in SQL, got: {sql_str}"
        )

    @pytest.mark.asyncio
    async def test_get_unprocessed_uuid_post_id(self):
        """post_id on returned ContentFeatures must be a UUID (not int)."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        post_uuid = uuid4()
        raw_row = MagicMock(spec=RawContentModel)
        raw_row.id = post_uuid
        raw_row.source_type = "TELEGRAM"
        raw_row.raw_data = {
            "title": "T",
            "content": "plain",
            "publishedAt": "2024-01-15T10:00:00Z",
        }

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [raw_row]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_unprocessed(batch_size=5)

        assert isinstance(result[0].post_id, uuid.UUID)

    # ------------------------------------------------------------------
    # save_features
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_save_features_upserts_posts_features(self):
        """save_features must execute at least one statement (upsert into posts_features)."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))

        features = ContentFeatures(
            post_id=uuid4(),
            content="test content",
            post_date=datetime(2024, 1, 1),
            text_length=100,
            word_count=20,
            topic_1="tech",
            topic_1_score=0.9,
            sentiment="POSITIVE",
            sentiment_score=0.8,
            embedding=[0.1, 0.2, 0.3],
        )

        await repo.save_features(features)

        assert mock_session.execute.call_count >= 1
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_save_features_updates_is_processed_by_rec_flag(self):
        """save_features must also UPDATE raw_content.is_processed_by_rec=true."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))

        post_id = uuid4()
        features = ContentFeatures(
            post_id=post_id,
            content="content",
            post_date=datetime(2024, 1, 1),
            source_id=uuid4(),
            source_type="TELEGRAM",
        )

        await repo.save_features(features)

        # Both upsert and flag update → at least 2 execute calls
        assert mock_session.execute.call_count >= 2

    @pytest.mark.asyncio
    async def test_save_features_persists_source_id(self):
        """Phase 1: save_features INSERT must include source_id and source_type columns."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        executed_stmts = []

        mock_session = AsyncMock()

        async def capture_execute(stmt, *args, **kwargs):
            executed_stmts.append(stmt)
            return MagicMock()

        mock_session.execute = capture_execute
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))

        post_id = uuid4()
        source_id = uuid4()
        features = ContentFeatures(
            post_id=post_id,
            content="content",
            post_date=datetime(2024, 1, 1),
            source_id=source_id,
            source_type="TELEGRAM",
        )

        await repo.save_features(features)

        # The first execute is the upsert into posts_features; must include source_id / source_type
        insert_sql = str(executed_stmts[0].compile(compile_kwargs={"literal_binds": False})).lower()
        assert "source_id" in insert_sql, (
            f"Expected source_id in INSERT SQL, got: {insert_sql}"
        )
        assert "source_type" in insert_sql, (
            f"Expected source_type in INSERT SQL, got: {insert_sql}"
        )

    # ------------------------------------------------------------------
    # get_candidates_by_freshness
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_candidates_by_freshness_returns_list(self):
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_candidates_by_freshness(max_age_hours=24, limit=50)

        assert isinstance(result, list)
        mock_session.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_candidates_by_freshness_accepts_exclude_source_ids(self):
        """Phase 1: freshness candidate query accepts exclude_source_ids kwarg."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_candidates_by_freshness(
            max_age_hours=24,
            limit=50,
            exclude_source_ids=[uuid4(), uuid4()],
        )
        assert isinstance(result, list)

    @pytest.mark.asyncio
    async def test_get_candidates_by_embedding_accepts_exclude_source_ids(self):
        """Phase 1: embedding candidate query accepts exclude_source_ids kwarg."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_candidates_by_embedding(
            embedding=[0.1] * 312,
            limit=50,
            exclude_source_ids=[uuid4()],
        )
        assert isinstance(result, list)

    # ------------------------------------------------------------------
    # get_candidates_by_sources (Phase 1 — new method)
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_candidates_by_sources_returns_list(self):
        """Phase 1: get_candidates_by_sources exists and returns a list."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_candidates_by_sources(
            include_source_ids=[uuid4(), uuid4()],
            exclude_source_ids=[],
            max_age_hours=48,
            limit=200,
        )
        assert isinstance(result, list)
        mock_session.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_candidates_by_sources_empty_include_returns_empty_no_db(self):
        """Phase 1: empty include_source_ids returns [] without hitting DB."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_factory = MagicMock()
        repo = PgContentRepository(session_factory=mock_factory)
        result = await repo.get_candidates_by_sources(
            include_source_ids=[],
            exclude_source_ids=[],
            max_age_hours=48,
            limit=200,
        )
        assert result == []
        mock_factory.assert_not_called()

    # ------------------------------------------------------------------
    # get_by_ids
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_by_ids_empty_input_returns_empty_list_no_db_call(self):
        """Empty post_ids must return [] without hitting the DB."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_factory = MagicMock()
        repo = PgContentRepository(session_factory=mock_factory)
        result = await repo.get_by_ids([])

        assert result == []
        mock_factory.assert_not_called()

    @pytest.mark.asyncio
    async def test_get_by_ids_returns_matching_content_features(self):
        """Should return ContentFeatures for each matching PostsFeaturesModel row."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        post_uuid = uuid4()
        source_uuid = uuid4()
        row = MagicMock(spec=PostsFeaturesModel)
        row.post_id = post_uuid
        row.source_id = source_uuid
        row.source_type = "TELEGRAM"
        row.processed_at = datetime(2024, 1, 15, tzinfo=timezone.utc)
        row.text_length = 500
        row.word_count = 80
        row.reading_time = 2
        row.complexity = 0.5
        row.is_short_form = False
        row.is_long_form = False
        row.topic_1 = "технологии"
        row.topic_1_score = 0.9
        row.topic_2 = None
        row.topic_2_score = None
        row.topic_3 = None
        row.topic_3_score = None
        row.sentiment = "POSITIVE"
        row.sentiment_score = 0.8
        row.entities_persons = []
        row.entities_organizations = []
        row.entities_locations = []
        row.embedding = None

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [row]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_by_ids([post_uuid])

        assert len(result) == 1
        assert isinstance(result[0], ContentFeatures)
        assert result[0].post_id == post_uuid
        assert result[0].source_id == source_uuid
        mock_session.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_by_ids_not_found_returns_empty_list(self):
        """When no rows match the given IDs, return an empty list."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_by_ids([uuid4(), uuid4()])

        assert result == []
        mock_session.execute.assert_called_once()

    # ------------------------------------------------------------------
    # get_candidates_by_embedding
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_candidates_by_embedding_returns_list(self):
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        embedding = [0.1] * 312
        result = await repo.get_candidates_by_embedding(embedding=embedding, limit=50)

        assert isinstance(result, list)
        mock_session.execute.assert_called_once()

    # ------------------------------------------------------------------
    # _UnionFind helper
    # ------------------------------------------------------------------

    def test_union_find_groups_connected_elements(self):
        from src.infrastructure.persistence.pg_content_repository import _UnionFind

        uf = _UnionFind()
        uf.union(1, 2)
        uf.union(2, 3)

        assert uf.find(1) == uf.find(3)

    def test_union_find_disconnected_elements_have_different_roots(self):
        from src.infrastructure.persistence.pg_content_repository import _UnionFind

        uf = _UnionFind()
        uf.union(1, 2)

        assert uf.find(1) == uf.find(2)
        assert uf.find(3) != uf.find(1)

    # ------------------------------------------------------------------
    # get_dedup_clusters
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_dedup_clusters_empty_input_returns_empty_dict_no_db_call(self):
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_factory = MagicMock()
        repo = PgContentRepository(session_factory=mock_factory)
        result = await repo.get_dedup_clusters([])

        assert result == {}
        mock_factory.assert_not_called()

    @pytest.mark.asyncio
    async def test_get_dedup_clusters_with_edges_groups_correctly(self):
        """Posts A and B share an EXACT edge -> same cluster; C alone -> unique."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository
        from src.infrastructure.persistence.models.article import ArticleModel
        from src.infrastructure.persistence.models.similarity import SimilarityModel

        post_a = uuid4()
        post_b = uuid4()
        post_c = uuid4()

        art_a = MagicMock(spec=ArticleModel)
        art_a.id = 10
        art_a.raw_content_id = post_a

        art_b = MagicMock(spec=ArticleModel)
        art_b.id = 20
        art_b.raw_content_id = post_b

        art_c = MagicMock(spec=ArticleModel)
        art_c.id = 30
        art_c.raw_content_id = post_c

        sim = MagicMock(spec=SimilarityModel)
        sim.article_a = 10
        sim.article_b = 20

        articles_result = MagicMock()
        articles_result.scalars.return_value.all.return_value = [art_a, art_b, art_c]

        sims_result = MagicMock()
        sims_result.scalars.return_value.all.return_value = [sim]

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock(side_effect=[articles_result, sims_result])

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_dedup_clusters([post_a, post_b, post_c])

        assert isinstance(result, dict)
        assert result[post_a] == result[post_b]
        assert result[post_c] != result[post_a]

    @pytest.mark.asyncio
    async def test_get_dedup_clusters_no_edges_each_post_unique_cluster(self):
        """When no similarity edges exist, each post gets a unique cluster ID."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository
        from src.infrastructure.persistence.models.article import ArticleModel
        from src.infrastructure.persistence.models.similarity import SimilarityModel

        post_a = uuid4()
        post_b = uuid4()

        art_a = MagicMock(spec=ArticleModel)
        art_a.id = 10
        art_a.raw_content_id = post_a

        art_b = MagicMock(spec=ArticleModel)
        art_b.id = 20
        art_b.raw_content_id = post_b

        articles_result = MagicMock()
        articles_result.scalars.return_value.all.return_value = [art_a, art_b]

        sims_result = MagicMock()
        sims_result.scalars.return_value.all.return_value = []

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock(side_effect=[articles_result, sims_result])

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_dedup_clusters([post_a, post_b])

        assert result[post_a] != result[post_b]

    # ------------------------------------------------------------------
    # get_related_pairs
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_related_pairs_empty_input_returns_empty_dict_no_db_call(self):
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_factory = MagicMock()
        repo = PgContentRepository(session_factory=mock_factory)
        result = await repo.get_related_pairs([])

        assert result == {}
        mock_factory.assert_not_called()

    @pytest.mark.asyncio
    async def test_get_related_pairs_returns_symmetric_adjacency(self):
        """If A RELATED B, result must contain A->B and B->A."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository
        from src.infrastructure.persistence.models.article import ArticleModel
        from src.infrastructure.persistence.models.similarity import SimilarityModel

        post_a = uuid4()
        post_b = uuid4()

        art_a = MagicMock(spec=ArticleModel)
        art_a.id = 10
        art_a.raw_content_id = post_a

        art_b = MagicMock(spec=ArticleModel)
        art_b.id = 20
        art_b.raw_content_id = post_b

        sim = MagicMock(spec=SimilarityModel)
        sim.article_a = 10
        sim.article_b = 20

        articles_result = MagicMock()
        articles_result.scalars.return_value.all.return_value = [art_a, art_b]

        sims_result = MagicMock()
        sims_result.scalars.return_value.all.return_value = [sim]

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock(side_effect=[articles_result, sims_result])

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_related_pairs([post_a, post_b])

        assert post_b in result.get(post_a, set())
        assert post_a in result.get(post_b, set())

    # ------------------------------------------------------------------
    # get_published_ids
    # ------------------------------------------------------------------

    @pytest.mark.asyncio
    async def test_get_published_ids_empty_input_returns_empty_dict_no_db_call(self):
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository

        mock_factory = MagicMock()
        repo = PgContentRepository(session_factory=mock_factory)
        result = await repo.get_published_ids([])

        assert result == {}
        mock_factory.assert_not_called()

    @pytest.mark.asyncio
    async def test_get_published_ids_returns_correct_uuid_mapping(self):
        """Should map content_id -> published_content.id correctly."""
        from src.infrastructure.persistence.pg_content_repository import PgContentRepository
        from src.infrastructure.persistence.models.published_content import PublishedContentModel

        raw_id = uuid4()
        pub_id = uuid4()

        row = MagicMock(spec=PublishedContentModel)
        row.id = pub_id
        row.content_id = raw_id

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [row]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgContentRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_published_ids([raw_id])

        assert result == {raw_id: pub_id}
        mock_session.execute.assert_called_once()
