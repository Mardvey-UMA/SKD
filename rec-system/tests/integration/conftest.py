"""Shared fixtures for integration tests (Tasks 47-50).

All integration tests use a single session-scoped PostgreSQL+pgvector container.
Alembic migrations run once before the test session.
Each test gets a function-scoped async session that rolls back after the test.
"""
from __future__ import annotations

from pathlib import Path

import pytest
from testcontainers.postgres import PostgresContainer
from sqlalchemy.ext.asyncio import (
    create_async_engine,
    async_sessionmaker,
    AsyncSession,
)

PROJECT_ROOT = Path(__file__).parent.parent.parent
ALEMBIC_INI = PROJECT_ROOT / "alembic.ini"


# ---------------------------------------------------------------------------
# Session-scoped container
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def pgvector_container_shared():
    """Start a single PostgreSQL+pgvector container for all integration tests."""
    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="session")
def sync_db_url(pgvector_container_shared):
    """psycopg2-compatible sync URL used by alembic."""
    url = pgvector_container_shared.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


@pytest.fixture(scope="session")
def async_db_url(pgvector_container_shared):
    """asyncpg-compatible URL used by SQLAlchemy async engine."""
    url = pgvector_container_shared.get_connection_url()
    # Strip any existing driver prefix and replace with asyncpg
    url = url.replace("postgresql+psycopg2://", "postgresql://", 1)
    url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
    return url


@pytest.fixture(scope="session", autouse=True)
def run_migrations_once(sync_db_url):
    """Run alembic upgrade head once before all integration tests."""
    from alembic import command
    from alembic.config import Config

    cfg = Config(str(ALEMBIC_INI))
    cfg.set_main_option("sqlalchemy.url", sync_db_url)
    # Only run the rec_data_flow branch (007+). The legacy 001-006 chain targets
    # rec_db and must not run on content_agg_db -- running "heads" would attempt
    # both branches and fail with DuplicateTable due to shared search_path.
    command.upgrade(cfg, "rec_data_flow@head")


# ---------------------------------------------------------------------------
# Function-scoped async session with rollback
# ---------------------------------------------------------------------------


@pytest.fixture
async def async_session(async_db_url) -> AsyncSession:
    """Provide a fresh async session per test; roll back after to keep DB clean."""
    engine = create_async_engine(async_db_url, echo=False)
    session_factory = async_sessionmaker(
        engine, class_=AsyncSession, expire_on_commit=False
    )
    async with session_factory() as session:
        yield session
        await session.rollback()
    await engine.dispose()


@pytest.fixture
def session_factory(async_db_url):
    """Return an async_sessionmaker bound to the shared container.

    Used by repository adapters that expect a callable session factory.
    Each call to the factory opens a new session.
    """
    from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

    engine = create_async_engine(async_db_url, echo=False)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    return factory
