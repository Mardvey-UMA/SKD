from unittest.mock import MagicMock

import pytest


class TestPsycopg2ConfigRepository:
    @pytest.fixture
    def mock_conn(self):
        conn = MagicMock()
        cursor = MagicMock()
        conn.cursor.return_value = cursor
        return conn

    @pytest.fixture
    def sut(self, mock_conn):
        from src.infrastructure.persistence.psycopg2_config_repo import (
            Psycopg2ConfigRepository,
        )

        return Psycopg2ConfigRepository(conn=mock_conn)

    @pytest.mark.unit
    def test_implements_port(self, sut):
        from src.domain.interfaces.config_port import ConfigPort

        assert isinstance(sut, ConfigPort)

    @pytest.mark.unit
    def test_queries_data_flow_dedup_config(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        sut.load_thresholds()
        sql = cursor.execute.call_args[0][0]
        assert "data_flow.dedup_config" in sql

    @pytest.mark.unit
    def test_load_thresholds(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = [
            ("threshold_duplicate", "0.85"),
            ("threshold_related", "0.70"),
        ]
        th_dup, th_rel = sut.load_thresholds()
        assert th_dup == 0.85
        assert th_rel == 0.70

    @pytest.mark.unit
    def test_load_thresholds_defaults(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = []
        th_dup, th_rel = sut.load_thresholds()
        assert th_dup == 0.85
        assert th_rel == 0.70

    @pytest.mark.unit
    def test_load_search_params(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = [
            ("search_top_k", "20"),
            ("search_window_hours", "72"),
        ]
        top_k, window = sut.load_search_params()
        assert top_k == 20
        assert window == 72

    @pytest.mark.unit
    def test_load_batch_params(self, sut, mock_conn):
        cursor = mock_conn.cursor.return_value
        cursor.fetchall.return_value = [
            ("batch_size", "64"),
            ("encoding_batch_size", "32"),
        ]
        batch, encoding = sut.load_batch_params()
        assert batch == 64
        assert encoding == 32
