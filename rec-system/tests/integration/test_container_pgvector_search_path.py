"""Regression test: ApplicationContainer's async engine must resolve pgvector operators.

Failure mode (without fix in container.py):
    asyncpg.exceptions.UndefinedFunctionError: operator does not exist: vector <=> vector

Root cause: pgvector types/operators are installed in 'data_flow' schema.
If the engine connects with default search_path (public only), those operators are invisible.

Fix: connect_args={"server_settings": {"search_path": "data_flow,public"}} in container.py.

This test is self-contained — it does NOT use the shared session-scoped conftest
(which is broken due to migration 009 FK to published_content).
"""
from __future__ import annotations

import pytest
import sqlalchemy
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from testcontainers.postgres import PostgresContainer

from src.infrastructure.container import ApplicationContainer


pytestmark = pytest.mark.integration


# Override the session-scoped autouse fixture from the parent conftest.
# That fixture (run_migrations_once) fails on migration 009 FK to published_content —
# a pre-existing breakage unrelated to this test. Our test manages its own container.
@pytest.fixture(scope="session", autouse=True)
def run_migrations_once():  # noqa: F811 — intentional override
    """No-op: this test file manages its own DB container."""


@pytest.mark.asyncio
async def test_container_engine_resolves_pgvector_operators_via_search_path():
    """ApplicationContainer engine must resolve pgvector <=> via search_path.

    Setup: pgvector extension installed in 'data_flow' schema (not 'public').
    Without search_path="data_flow,public" the ::vector cast fails:
        UndefinedObject: type "vector" does not exist
    With the fix: cast resolves and distance is a valid float in [0, 2].
    """
    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        # --- sync setup: install pgvector into data_flow schema ---
        sync_url = pg.get_connection_url()
        if "psycopg2" not in sync_url:
            sync_url = sync_url.replace("postgresql://", "postgresql+psycopg2://", 1)

        sync_engine = sqlalchemy.create_engine(sync_url)
        with sync_engine.connect() as conn:
            conn.execute(text("CREATE SCHEMA IF NOT EXISTS data_flow"))
            # Install pgvector into data_flow (not public).
            # This means ::vector and <=> are only resolvable if data_flow is in search_path.
            conn.execute(text("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA data_flow"))
            conn.commit()
        sync_engine.dispose()

        # --- async URL for ApplicationContainer ---
        async_url = pg.get_connection_url()
        async_url = async_url.replace("postgresql+psycopg2://", "postgresql://", 1)
        async_url = async_url.replace("postgresql://", "postgresql+asyncpg://", 1)

        # --- wire the DI container ---
        container = ApplicationContainer()
        container.config.db_url.from_value(async_url)

        engine = container.async_engine()
        session_factory = async_sessionmaker(
            engine, class_=AsyncSession, expire_on_commit=False
        )

        try:
            async with session_factory() as session:
                result = await session.execute(
                    text(
                        "SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector AS dist"
                    )
                )
                row = result.fetchone()
                assert row is not None
                dist = float(row[0])
                assert 0.0 <= dist <= 2.0, f"Unexpected cosine distance: {dist}"
        finally:
            await engine.dispose()
            container.async_engine.reset()
