"""E2E test fixtures for the recommendation service.

Provides:
- Session-scoped PostgreSQL testcontainer with pgvector
- Alembic migration run (once per session)
- FastAPI test app with real repos but mocked NLP
- httpx AsyncClient for HTTP calls
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import AsyncIterator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from testcontainers.postgres import PostgresContainer

PROJECT_ROOT = Path(__file__).parent.parent.parent


# ---------------------------------------------------------------------------
# Session-scoped DB container (starts once, shared across all E2E tests)
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def e2e_postgres():
    """Start a PostgreSQL+pgvector testcontainer for E2E tests."""
    with PostgresContainer(image="ankane/pgvector:latest") as pg:
        yield pg


@pytest.fixture(scope="session")
def e2e_sync_url(e2e_postgres: PostgresContainer) -> str:
    """Return psycopg2-compatible sync URL for alembic."""
    url = e2e_postgres.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


@pytest.fixture(scope="session")
def e2e_async_url(e2e_postgres: PostgresContainer) -> str:
    """Return asyncpg-compatible async URL for SQLAlchemy async engine."""
    url = e2e_postgres.get_connection_url()
    if "psycopg2" in url:
        url = url.replace("postgresql+psycopg2://", "postgresql+asyncpg://", 1)
    elif url.startswith("postgresql://"):
        url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
    return url


@pytest.fixture(scope="session", autouse=True)
def e2e_migrations(e2e_sync_url: str) -> None:
    """Run alembic upgrade head once for the entire E2E session."""
    from alembic import command
    from alembic.config import Config

    cfg = Config(str(PROJECT_ROOT / "alembic.ini"))
    cfg.set_main_option("sqlalchemy.url", e2e_sync_url)
    command.upgrade(cfg, "heads")


# ---------------------------------------------------------------------------
# Per-test (function-scoped) async engine, session factory, and HTTP client.
#
# Session-scoped asyncpg connections get pinned to the first test's event
# loop; subsequent tests (with their own loop) fail with
# "Future attached to a different loop".  The simplest fix is function-scoped
# engine/session-factory so each test owns its own asyncpg connection pool.
# ---------------------------------------------------------------------------


@pytest_asyncio.fixture
async def e2e_session_factory(e2e_async_url: str):
    """Create a per-test async session factory pointing to the E2E test DB."""
    engine = create_async_engine(e2e_async_url, echo=False)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    yield factory
    await engine.dispose()


@pytest_asyncio.fixture
async def e2e_client(e2e_session_factory) -> AsyncIterator[AsyncClient]:
    """Create a FastAPI test app with real DB repos and no NLP models."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.session_factory.override(e2e_session_factory)

    app = create_app(container=container)

    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        yield client


@pytest_asyncio.fixture
async def e2e_db_session(e2e_session_factory) -> AsyncIterator[AsyncSession]:
    """Provide a raw async session for test data seeding and DB assertions."""
    async with e2e_session_factory() as session:
        yield session
        await session.rollback()
