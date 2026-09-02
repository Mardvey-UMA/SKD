from unittest.mock import MagicMock
from uuid import uuid4

import pytest


class TestPsycopg2RawContentRepository:
    @pytest.fixture
    def mock_conn(self):
        conn = MagicMock()
        cursor = MagicMock()
        conn.cursor.return_value = cursor
        return conn

    @pytest.fixture
    def sut(self, mock_conn):
        from src.infrastructure.persistence.psycopg2_raw_content_repo import (
            Psycopg2RawContentRepository,
        )

        return Psycopg2RawContentRepository(conn=mock_conn)

    @pytest.mark.unit
    def test_implements_port(self, sut):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        assert isinstance(sut, RawContentRepositoryPort)

    @pytest.mark.unit
    def test_fetch_pending_batch_returns_raw_content_list(self, sut, mock_conn):
        uid = uuid4()
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = [
            (uid, "Some title", "Some body", "HABR", "ext-1", None),
        ]
        result = sut.fetch_pending_batch(batch_size=64)
        assert len(result) == 1
        assert result[0].id == uid
        assert result[0].title == "Some title"
        assert result[0].content_body == "Some body"
        assert result[0].source_type == "HABR"

    @pytest.mark.unit
    def test_fetch_pending_uses_skip_locked(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.fetch_pending_batch(batch_size=64)
        sql = cursor.execute.call_args[0][0]
        assert "FOR UPDATE SKIP LOCKED" in sql

    @pytest.mark.unit
    def test_fetch_pending_queries_data_flow_schema(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.fetch_pending_batch(batch_size=64)
        sql = cursor.execute.call_args[0][0]
        assert "data_flow.raw_content" in sql

    @pytest.mark.unit
    def test_fetch_pending_filters_by_processing_status_and_dedup_flag(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.fetch_pending_batch(batch_size=64)
        sql = cursor.execute.call_args[0][0]
        assert "processing_status" in sql
        assert "COMPLETED" in sql
        assert "is_processed_by_dedup" in sql

    @pytest.mark.unit
    def test_fetch_pending_extracts_jsonb_fields(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.fetch_pending_batch(batch_size=64)
        sql = cursor.execute.call_args[0][0]
        assert "raw_data->>" in sql

    @pytest.mark.unit
    def test_mark_processed(self, sut, mock_conn):
        ids = [uuid4(), uuid4()]
        sut.mark_processed(ids)
        cursor = mock_conn.cursor.return_value
        cursor.execute.assert_called()
        mock_conn.commit.assert_called()

    @pytest.mark.unit
    def test_mark_processed_uses_data_flow_schema(self, sut, mock_conn):
        ids = [uuid4()]
        sut.mark_processed(ids)
        cursor = mock_conn.cursor.return_value
        sql = cursor.execute.call_args[0][0]
        assert "data_flow.raw_content" in sql
        assert "is_processed_by_dedup" in sql

    @pytest.mark.unit
    def test_mark_processed_empty_list_no_query(self, sut, mock_conn):
        sut.mark_processed([])
        cursor = mock_conn.cursor.return_value
        cursor.execute.assert_not_called()

    @pytest.mark.unit
    def test_fetch_pending_selects_clean_text(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.fetch_pending_batch(batch_size=64)
        sql = cursor.execute.call_args[0][0]
        assert "clean_text" in sql

    @pytest.mark.unit
    def test_fetch_pending_filters_null_clean_text(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.fetch_pending_batch(batch_size=64)
        sql = cursor.execute.call_args[0][0]
        assert "clean_text IS NOT NULL" in sql
