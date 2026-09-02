"""Integration test fixtures — testcontainers PostgreSQL with pgvector."""
import ast
import os
from unittest.mock import MagicMock

import numpy as np
import psycopg2
import pytest
from testcontainers.postgres import PostgresContainer

from src.domain.interfaces.encoder_port import EncoderPort


def _run_migrations(conn):
    """Run all migration upgrade() functions against the connection."""
    cur = conn.cursor()
    versions_dir = "src/infrastructure/persistence/migrations/versions"
    migration_files = sorted(
        f for f in os.listdir(versions_dir)
        if f.endswith(".py") and not f.startswith("__")
    )

    class FakeOp:
        @staticmethod
        def execute(sql):
            cur.execute(sql)

    for mf in migration_files:
        filepath = os.path.join(versions_dir, mf)
        with open(filepath) as f:
            content = f.read()

        tree = ast.parse(content)
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef) and node.name == "upgrade":
                ns = {"op": FakeOp}
                func_code = compile(
                    ast.Module(body=[node], type_ignores=[]),
                    filepath, "exec",
                )
                exec(func_code, ns)
                ns["upgrade"]()
                break
    conn.commit()


@pytest.fixture(scope="session")
def pg_container():
    with PostgresContainer("pgvector/pgvector:pg16", driver=None) as pg:
        url = pg.get_connection_url().replace("+psycopg2", "")
        conn = psycopg2.connect(url)
        conn.autocommit = True
        _run_migrations(conn)
        conn.close()
        yield pg


@pytest.fixture(scope="function")
def pg_connection(pg_container):
    url = pg_container.get_connection_url().replace("+psycopg2", "")
    conn = psycopg2.connect(url)
    conn.autocommit = False
    yield conn
    conn.rollback()
    # Clean data for next test
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("DELETE FROM similarities")
    cur.execute("DELETE FROM articles")
    cur.execute("DELETE FROM raw_content")
    cur.execute("DELETE FROM config")
    cur.execute("""
        INSERT INTO config (key, value, description) VALUES
        ('threshold_duplicate', '0.85', 'Score >= this is DUPLICATE'),
        ('threshold_related', '0.70', 'Score >= this is RELATED'),
        ('search_top_k', '20', 'Nearest neighbors per article'),
        ('search_window_hours', '72', 'Time window for NN search in hours'),
        ('batch_size', '64', 'Raw content rows per worker cycle'),
        ('encoding_batch_size', '32', 'Model encode batch size')
    """)
    conn.close()


@pytest.fixture
def mock_encoder():
    mock = MagicMock(spec=EncoderPort)

    def encode_side_effect(texts, batch_size=32):
        return np.random.randn(len(texts), 1024).astype(np.float32)

    mock.encode_batch.side_effect = encode_side_effect
    return mock


@pytest.fixture
def mock_encoder_similar():
    base_vec = np.random.randn(1024).astype(np.float32)
    base_vec = base_vec / np.linalg.norm(base_vec)
    mock = MagicMock(spec=EncoderPort)

    def encode_side_effect(texts, batch_size=32):
        # Use very small noise to keep cosine similarity > 0.95
        noise = np.random.randn(len(texts), 1024).astype(np.float32) * 0.001
        vecs = base_vec + noise
        norms = np.linalg.norm(vecs, axis=1, keepdims=True)
        return vecs / norms

    mock.encode_batch.side_effect = encode_side_effect
    return mock
