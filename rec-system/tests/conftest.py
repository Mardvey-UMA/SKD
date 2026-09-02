"""Shared test fixtures for the recommendation system test suite."""

import pytest
import pytest_asyncio
from sqlalchemy import create_engine
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker


@pytest.fixture(scope="session")
def postgres_container():
    """Start a PostgreSQL testcontainer for the test session."""
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer("postgres:16") as container:
        yield container


@pytest.fixture(scope="session")
def postgres_engine(postgres_container):
    """Create a synchronous SQLAlchemy engine connected to the testcontainer."""
    url = postgres_container.get_connection_url()
    engine = create_engine(url)
    yield engine
    engine.dispose()


@pytest.fixture(scope="session")
def async_engine(postgres_container):
    """Create an async SQLAlchemy engine connected to the testcontainer."""
    sync_url = postgres_container.get_connection_url()
    # Convert sync URL to async URL (postgresql+asyncpg)
    async_url = sync_url.replace("postgresql+psycopg2://", "postgresql+asyncpg://", 1)
    if not async_url.startswith("postgresql+asyncpg://"):
        async_url = sync_url.replace("postgresql://", "postgresql+asyncpg://", 1)
    engine = create_async_engine(async_url, echo=False)
    yield engine


@pytest.fixture
async def db_session(async_engine):
    """Create an async database session for a single test (function-scoped)."""
    async_session_factory = sessionmaker(
        async_engine, class_=AsyncSession, expire_on_commit=False
    )
    async with async_session_factory() as session:
        yield session
        await session.rollback()
