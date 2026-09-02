import numpy as np
import pytest


class TestDbMigrations:
    @pytest.mark.integration
    def test_migrations_apply_cleanly(self, pg_connection):
        cur = pg_connection.cursor()
        cur.execute("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
        tables = {row[0] for row in cur.fetchall()}
        assert "raw_content" in tables
        assert "articles" in tables
        assert "similarities" in tables
        assert "config" in tables

    @pytest.mark.integration
    def test_config_seed_data(self, pg_connection):
        cur = pg_connection.cursor()
        cur.execute("SELECT key, value FROM config ORDER BY key")
        config = {row[0]: row[1] for row in cur.fetchall()}
        assert config["threshold_duplicate"] == "0.85"
        assert config["threshold_related"] == "0.70"
        assert config["batch_size"] == "64"

    @pytest.mark.integration
    def test_pgvector_extension(self, pg_connection):
        cur = pg_connection.cursor()
        cur.execute("SELECT extname FROM pg_extension WHERE extname = 'vector'")
        assert cur.fetchone() is not None


class TestRawContentRepoIntegration:
    @pytest.mark.integration
    def test_insert_and_fetch(self, pg_connection):
        from src.domain.entities.raw_content import RawContent
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )

        repo = Psycopg2RawContentRepository(conn=pg_connection)
        rc = RawContent(
            id=None, kafka_topic="news", kafka_key="k1",
            raw_text="Integration test text", source="test",
            published_at=None, ingested_at=None,
            status="pending", batch_id=None,
        )
        ids = repo.insert_batch([rc])
        assert len(ids) == 1

        batch = repo.fetch_pending_batch(batch_size=10)
        assert len(batch) >= 1
        assert batch[0].raw_text == "Integration test text"

    @pytest.mark.integration
    def test_mark_done(self, pg_connection):
        from src.domain.entities.raw_content import RawContent
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )

        repo = Psycopg2RawContentRepository(conn=pg_connection)
        rc = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="To be done", source=None,
            published_at=None, ingested_at=None,
            status="pending", batch_id=None,
        )
        ids = repo.insert_batch([rc])
        batch = repo.fetch_pending_batch(batch_size=10)
        repo.mark_done([b.id for b in batch])
        batch2 = repo.fetch_pending_batch(batch_size=10)
        done_ids = {b.id for b in batch2}
        assert ids[0] not in done_ids


class TestArticleRepoIntegration:
    @pytest.mark.integration
    def test_save_and_find_by_hash(self, pg_connection):
        from src.domain.entities.article import Article
        from src.domain.entities.raw_content import RawContent
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )

        rc_repo = Psycopg2RawContentRepository(conn=pg_connection)
        rc = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Article text", source=None,
            published_at=None, ingested_at=None,
            status="pending", batch_id=None,
        )
        rc_ids = rc_repo.insert_batch([rc])

        repo = Psycopg2ArticleRepository(conn=pg_connection)
        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=None, raw_content_id=rc_ids[0],
            content_hash=ContentHash("a" * 64),
            normalized_text="article text", embedding=emb,
            source="test", created_at=None,
        )
        article_id = repo.save_article(article)
        assert article_id > 0

        found = repo.find_by_hashes([ContentHash("a" * 64)])
        assert "a" * 64 in found
        assert found["a" * 64] == article_id

    @pytest.mark.integration
    def test_search_neighbors(self, pg_connection):
        from src.domain.entities.article import Article
        from src.domain.entities.raw_content import RawContent
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )

        rc_repo = Psycopg2RawContentRepository(conn=pg_connection)
        repo = Psycopg2ArticleRepository(conn=pg_connection)

        rc = RawContent(
            id=None, kafka_topic="news", kafka_key=None,
            raw_text="Search test", source=None,
            published_at=None, ingested_at=None,
            status="pending", batch_id=None,
        )
        rc_ids = rc_repo.insert_batch([rc])
        emb_vec = np.random.randn(1024).astype(np.float32)
        emb_vec = emb_vec / np.linalg.norm(emb_vec)
        emb = Embedding(emb_vec)
        article = Article(
            id=None, raw_content_id=rc_ids[0],
            content_hash=ContentHash("c" * 64),
            normalized_text="search test", embedding=emb,
            source=None, created_at=None,
        )
        repo.save_article(article)

        results = repo.search_neighbors(emb_vec, top_k=5, window_hours=72)
        assert len(results) >= 1


class TestSimilarityRepoIntegration:
    @pytest.mark.integration
    def test_save_and_upsert(self, pg_connection):
        from src.domain.entities.article import Article
        from src.domain.entities.raw_content import RawContent
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.rel_type import RelationType
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )
        from src.infrastructure.persistence.psycopg2_similarity_repo import (
            Psycopg2SimilarityRepository,
        )

        rc_repo = Psycopg2RawContentRepository(conn=pg_connection)
        art_repo = Psycopg2ArticleRepository(conn=pg_connection)
        sim_repo = Psycopg2SimilarityRepository(conn=pg_connection)

        rc1 = RawContent(
            id=None, kafka_topic="n", kafka_key=None, raw_text="A",
            source=None, published_at=None, ingested_at=None,
            status="pending", batch_id=None,
        )
        rc2 = RawContent(
            id=None, kafka_topic="n", kafka_key=None, raw_text="B",
            source=None, published_at=None, ingested_at=None,
            status="pending", batch_id=None,
        )
        rc_ids = rc_repo.insert_batch([rc1, rc2])

        a1 = Article(
            id=None, raw_content_id=rc_ids[0],
            content_hash=ContentHash("d" * 64),
            normalized_text="a", embedding=None,
            source=None, created_at=None,
        )
        a2 = Article(
            id=None, raw_content_id=rc_ids[1],
            content_hash=ContentHash("e" * 64),
            normalized_text="b", embedding=None,
            source=None, created_at=None,
        )
        id1 = art_repo.save_article(a1)
        id2 = art_repo.save_article(a2)

        sim = Similarity.create(id1, id2, 0.80, RelationType.RELATED)
        sim_repo.save_one(sim)

        sim2 = Similarity.create(id1, id2, 0.90, RelationType.DUPLICATE)
        sim_repo.save_one(sim2)

        cur = pg_connection.cursor()
        a, b = sorted([id1, id2])
        cur.execute(
            "SELECT score FROM similarities WHERE article_a = %s AND article_b = %s",
            (a, b),
        )
        row = cur.fetchone()
        assert row[0] >= 0.90


class TestConfigRepoIntegration:
    @pytest.mark.integration
    def test_load_thresholds(self, pg_connection):
        from src.infrastructure.persistence.psycopg2_config_repo import (
            Psycopg2ConfigRepository,
        )

        repo = Psycopg2ConfigRepository(conn=pg_connection)
        th_dup, th_rel = repo.load_thresholds()
        assert th_dup == 0.85
        assert th_rel == 0.70
