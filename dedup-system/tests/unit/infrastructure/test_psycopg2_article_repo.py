from unittest.mock import MagicMock
from uuid import uuid4

import numpy as np
import pytest


class TestPsycopg2ArticleRepository:
    @pytest.fixture
    def mock_conn(self):
        conn = MagicMock()
        cursor = MagicMock()
        conn.cursor.return_value = cursor
        return conn

    @pytest.fixture
    def sut(self, mock_conn):
        from src.infrastructure.persistence.psycopg2_article_repo import (
            Psycopg2ArticleRepository,
        )

        return Psycopg2ArticleRepository(conn=mock_conn)

    @pytest.mark.unit
    def test_implements_port(self, sut):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        assert isinstance(sut, ArticleRepositoryPort)

    @pytest.mark.unit
    def test_save_article_with_embedding(self, sut, mock_conn):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding

        cursor = mock_conn.cursor.return_value
        cursor.fetchone.return_value = (42,)
        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=None, raw_content_id=uuid4(),
            content_hash=ContentHash("a" * 64),
            normalized_text="text", embedding=emb,
            source="reuters", created_at=None,
        )
        result = sut.save_article(article)
        assert result == 42
        assert cursor.execute.call_count >= 1

    @pytest.mark.unit
    def test_save_article_uses_data_flow_schema(self, sut, mock_conn):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding

        cursor = mock_conn.cursor.return_value
        cursor.fetchone.return_value = (42,)
        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=None, raw_content_id=uuid4(),
            content_hash=ContentHash("a" * 64),
            normalized_text="text", embedding=emb,
            source="reuters", created_at=None,
        )
        sut.save_article(article)
        all_sqls = [call[0][0] for call in cursor.execute.call_args_list]
        assert any("data_flow.articles" in sql for sql in all_sqls)

    @pytest.mark.unit
    def test_save_article_casts_uuid(self, sut, mock_conn):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash

        cursor = mock_conn.cursor.return_value
        cursor.fetchone.return_value = (43,)
        article = Article(
            id=None, raw_content_id=uuid4(),
            content_hash=ContentHash("b" * 64),
            normalized_text="text", embedding=None,
            source=None, created_at=None,
        )
        sut.save_article(article)
        all_sqls = [call[0][0] for call in cursor.execute.call_args_list]
        assert any("::uuid" in sql for sql in all_sqls)

    @pytest.mark.unit
    def test_save_article_without_embedding(self, sut, mock_conn):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash

        cursor = mock_conn.cursor.return_value
        cursor.fetchone.return_value = (43,)
        article = Article(
            id=None, raw_content_id=uuid4(),
            content_hash=ContentHash("b" * 64),
            normalized_text="text", embedding=None,
            source=None, created_at=None,
        )
        result = sut.save_article(article)
        assert result == 43

    @pytest.mark.unit
    def test_find_by_hashes(self, sut, mock_conn):
        from src.domain.value_objects.content_hash import ContentHash

        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = [
            (10, "a" * 64),
            (20, "b" * 64),
        ]
        hashes = [ContentHash("a" * 64), ContentHash("b" * 64)]
        result = sut.find_by_hashes(hashes)
        assert result == {"a" * 64: 10, "b" * 64: 20}

    @pytest.mark.unit
    def test_find_by_hashes_uses_data_flow_schema(self, sut, mock_conn):
        from src.domain.value_objects.content_hash import ContentHash

        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.find_by_hashes([ContentHash("a" * 64)])
        sql = cursor.execute.call_args[0][0]
        assert "data_flow.articles" in sql

    @pytest.mark.unit
    def test_search_neighbors(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = [(50, 0.92), (51, 0.78)]
        emb = np.random.randn(1024).astype(np.float32)
        result = sut.search_neighbors(emb, top_k=20, window_hours=72)
        assert len(result) == 2
        assert result[0] == (50, 0.92)

    @pytest.mark.unit
    def test_search_neighbors_uses_cosine_operator(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        emb = np.random.randn(1024).astype(np.float32)
        sut.search_neighbors(emb, top_k=20, window_hours=72)
        sql = cursor.execute.call_args[0][0]
        assert "<=>" in sql
        assert "data_flow.articles" in sql

    @pytest.mark.unit
    def test_save_article_uses_savepoint(self, sut, mock_conn):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding

        cursor = mock_conn.cursor.return_value
        cursor.fetchone.return_value = (42,)
        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=None, raw_content_id=uuid4(),
            content_hash=ContentHash("a" * 64),
            normalized_text="text", embedding=emb,
            source="reuters", created_at=None,
        )
        sut.save_article(article)
        all_sqls = [call[0][0] for call in cursor.execute.call_args_list]
        assert any("SAVEPOINT" in sql for sql in all_sqls)
        assert any("RELEASE SAVEPOINT" in sql for sql in all_sqls)

    @pytest.mark.unit
    def test_save_article_rollback_on_error(self, sut, mock_conn):
        import psycopg2

        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding

        cursor = mock_conn.cursor.return_value

        def raise_on_insert(sql, *args):
            if "INSERT" in sql:
                raise psycopg2.DataError("NaN in vector")

        cursor.execute.side_effect = raise_on_insert
        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=None, raw_content_id=uuid4(),
            content_hash=ContentHash("a" * 64),
            normalized_text="text", embedding=emb,
            source="reuters", created_at=None,
        )
        with pytest.raises(psycopg2.DataError):
            sut.save_article(article)
        all_sqls = [call[0][0] for call in cursor.execute.call_args_list]
        assert any("ROLLBACK TO SAVEPOINT" in sql for sql in all_sqls)
