from unittest.mock import MagicMock

import pytest


class TestPsycopg2SimilarityRepository:
    @pytest.fixture
    def mock_conn(self):
        conn = MagicMock()
        cursor = MagicMock()
        conn.cursor.return_value = cursor
        return conn

    @pytest.fixture
    def sut(self, mock_conn):
        from src.infrastructure.persistence.psycopg2_similarity_repo import (
            Psycopg2SimilarityRepository,
        )

        return Psycopg2SimilarityRepository(conn=mock_conn)

    @pytest.mark.unit
    def test_implements_port(self, sut):
        from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort

        assert isinstance(sut, SimilarityRepositoryPort)

    @pytest.mark.unit
    def test_save_batch(self, sut, mock_conn):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        sims = [
            Similarity.create(1, 2, 0.92, RelationType.DUPLICATE),
            Similarity.create(1, 3, 0.75, RelationType.RELATED),
        ]
        sut.save_batch(sims)
        cursor = mock_conn.cursor.return_value
        cursor.execute.assert_called()

    @pytest.mark.unit
    def test_save_batch_uses_upsert(self, sut, mock_conn):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        sims = [Similarity.create(1, 2, 0.92, RelationType.DUPLICATE)]
        sut.save_batch(sims)
        cursor = mock_conn.cursor.return_value
        sql = cursor.execute.call_args[0][0]
        assert "ON CONFLICT" in sql
        assert "GREATEST" in sql

    @pytest.mark.unit
    def test_save_batch_uses_data_flow_schema(self, sut, mock_conn):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        sims = [Similarity.create(1, 2, 0.92, RelationType.DUPLICATE)]
        sut.save_batch(sims)
        cursor = mock_conn.cursor.return_value
        sql = cursor.execute.call_args[0][0]
        assert "data_flow.similarities" in sql

    @pytest.mark.unit
    def test_save_one(self, sut, mock_conn):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        sim = Similarity.create(10, 20, 1.0, RelationType.EXACT)
        sut.save_one(sim)
        cursor = mock_conn.cursor.return_value
        cursor.execute.assert_called()

    @pytest.mark.unit
    def test_save_empty_batch(self, sut, mock_conn):
        sut.save_batch([])
        cursor = mock_conn.cursor.return_value
        cursor.execute.assert_not_called()
