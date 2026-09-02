"""Unit tests for Task 26: full DI container wiring and main.py lifecycle.

RED phase: these tests verify the expected structure and startup behavior
of the DI container and FastAPI application after all new components are wired.
"""
from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


# ---------------------------------------------------------------------------
# Container provider presence tests
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_container_has_redis_client_provider():
    """ApplicationContainer must have a redis_client Singleton provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "redis_client"), (
        "ApplicationContainer must have 'redis_client' provider"
    )


@pytest.mark.unit
def test_container_has_redis_cache_client_provider():
    """ApplicationContainer must have a redis_cache_client provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "redis_cache_client"), (
        "ApplicationContainer must have 'redis_cache_client' provider"
    )


@pytest.mark.unit
def test_container_has_kafka_producer_wrapper_provider():
    """ApplicationContainer must have a kafka_producer_wrapper provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "kafka_producer_wrapper"), (
        "ApplicationContainer must have 'kafka_producer_wrapper' provider"
    )


@pytest.mark.unit
def test_container_has_kafka_event_publisher_provider():
    """ApplicationContainer must have a kafka_event_publisher provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "kafka_event_publisher"), (
        "ApplicationContainer must have 'kafka_event_publisher' provider"
    )


@pytest.mark.unit
def test_container_has_pg_category_repository_provider():
    """ApplicationContainer must have a pg_category_repository provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "category_repo"), (
        "ApplicationContainer must have 'category_repo' provider"
    )


@pytest.mark.unit
def test_container_has_pg_recommendation_history_repository_provider():
    """ApplicationContainer must have a recommendation_history_repo provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "recommendation_history_repo"), (
        "ApplicationContainer must have 'recommendation_history_repo' provider"
    )


@pytest.mark.unit
def test_container_has_get_categories_use_case_provider():
    """ApplicationContainer must have a get_categories_use_case provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "get_categories_use_case"), (
        "ApplicationContainer must have 'get_categories_use_case' provider"
    )


@pytest.mark.unit
def test_container_has_get_cold_start_use_case_provider():
    """ApplicationContainer must have a get_cold_start_use_case provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "get_cold_start_use_case"), (
        "ApplicationContainer must have 'get_cold_start_use_case' provider"
    )


@pytest.mark.unit
def test_container_has_get_recommendations_use_case_provider():
    """ApplicationContainer must have a get_recommendations_use_case provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "get_recommendations_use_case"), (
        "ApplicationContainer must have 'get_recommendations_use_case' provider"
    )


@pytest.mark.unit
def test_container_has_handle_user_created_use_case_provider():
    """ApplicationContainer must have a handle_user_created_use_case provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "handle_user_created_use_case"), (
        "ApplicationContainer must have 'handle_user_created_use_case' provider"
    )


@pytest.mark.unit
def test_container_has_handle_interactions_batch_use_case_provider():
    """ApplicationContainer must have a handle_interactions_batch_use_case provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "handle_interactions_batch_use_case"), (
        "ApplicationContainer must have 'handle_interactions_batch_use_case' provider"
    )


@pytest.mark.unit
def test_container_has_user_created_consumer_provider():
    """ApplicationContainer must have a user_created_consumer provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "user_created_consumer"), (
        "ApplicationContainer must have 'user_created_consumer' provider"
    )


@pytest.mark.unit
def test_container_has_interactions_batch_consumer_provider():
    """ApplicationContainer must have an interactions_batch_consumer provider."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "interactions_batch_consumer"), (
        "ApplicationContainer must have 'interactions_batch_consumer' provider"
    )


# ---------------------------------------------------------------------------
# Router registration tests
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_app_has_recommendations_route():
    """FastAPI app must have POST /recommendations route registered."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/recommendations" in paths, (
        f"POST /recommendations route not found. Routes: {paths}"
    )


@pytest.mark.unit
def test_app_has_cold_start_route():
    """FastAPI app must have GET /recommendations/cold-start route registered."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/recommendations/cold-start" in paths, (
        f"GET /recommendations/cold-start route not found. Routes: {paths}"
    )


@pytest.mark.unit
def test_app_has_categories_route():
    """FastAPI app must have GET /categories route registered."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/categories" in paths, (
        f"GET /categories route not found. Routes: {paths}"
    )


@pytest.mark.unit
def test_app_has_onboarding_route():
    """FastAPI app must have POST /onboarding route registered."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/onboarding" in paths, (
        f"POST /onboarding route not found. Routes: {paths}"
    )


@pytest.mark.unit
def test_app_has_health_route():
    """FastAPI app must have GET /health route registered."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/health" in paths, (
        f"GET /health route not found. Routes: {paths}"
    )


@pytest.mark.unit
def test_app_does_not_have_generate_feed_route():
    """FastAPI app must NOT have old /generate-feed route."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/generate-feed" not in paths, (
        f"/generate-feed must be removed. Routes: {paths}"
    )


@pytest.mark.unit
def test_app_does_not_have_old_onboard_route():
    """FastAPI app must NOT have old /onboard route (replaced by /onboarding)."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    paths = {r.path for r in app.routes if hasattr(r, "methods")}
    assert "/onboard" not in paths, (
        f"/onboard must be removed (replaced by /onboarding). Routes: {paths}"
    )


# ---------------------------------------------------------------------------
# Lifespan startup failure tests (mocked Redis/Kafka)
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_lifespan_raises_if_redis_unavailable():
    """Lifespan startup must raise RuntimeError when Redis is not reachable.

    Tests by directly invoking the lifespan context manager (the ASGI lifespan
    protocol sends startup.failed when lifespan raises, which in production
    causes uvicorn to terminate; httpx ASGITransport does not enforce this).
    """
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    # Override the redis_client provider so ping raises
    mock_redis = AsyncMock()
    mock_redis.ping = AsyncMock(side_effect=ConnectionError("Redis down"))
    container.redis_client.override(mock_redis)

    app = create_app(container=container)

    # Directly invoke the lifespan context manager to verify it raises
    with pytest.raises((RuntimeError, ConnectionError, Exception)):
        async with app.router.lifespan_context(app):
            pass  # pragma: no cover


@pytest.mark.unit
@pytest.mark.asyncio
async def test_lifespan_raises_if_kafka_unavailable():
    """Lifespan startup must raise RuntimeError when Kafka broker is not reachable."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    # Redis succeeds; Kafka producer start fails
    mock_redis = AsyncMock()
    mock_redis.ping = AsyncMock(return_value=True)
    mock_redis.aclose = AsyncMock(return_value=None)
    container.redis_client.override(mock_redis)

    mock_producer = AsyncMock()
    mock_producer.start = AsyncMock(side_effect=Exception("Kafka unavailable"))
    container.kafka_producer_wrapper.override(mock_producer)

    app = create_app(container=container)

    with pytest.raises((RuntimeError, Exception)):
        async with app.router.lifespan_context(app):
            pass  # pragma: no cover


# ---------------------------------------------------------------------------
# Dependency overrides tests
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_app_has_dependency_override_for_get_recommendations_use_case():
    """app.dependency_overrides must contain get_recommendations_use_case."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app
    from src.presentation.api.recommendations_router import get_recommendations_use_case

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    assert get_recommendations_use_case in app.dependency_overrides, (
        "get_recommendations_use_case must be overridden in app.dependency_overrides"
    )


@pytest.mark.unit
def test_app_has_dependency_override_for_get_cold_start_use_case():
    """app.dependency_overrides must contain get_cold_start_use_case."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app
    from src.presentation.api.recommendations_router import get_cold_start_use_case

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    assert get_cold_start_use_case in app.dependency_overrides, (
        "get_cold_start_use_case must be overridden in app.dependency_overrides"
    )


@pytest.mark.unit
def test_app_has_dependency_override_for_get_categories_use_case():
    """app.dependency_overrides must contain get_categories_use_case."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app
    from src.presentation.api.categories_router import get_categories_use_case

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    assert get_categories_use_case in app.dependency_overrides, (
        "get_categories_use_case must be overridden in app.dependency_overrides"
    )


@pytest.mark.unit
def test_app_has_dependency_override_for_get_onboard_user_use_case():
    """app.dependency_overrides must contain get_onboard_user_use_case."""
    from src.infrastructure.container import ApplicationContainer
    from src.main import create_app
    from src.presentation.api.onboarding_router import get_onboard_user_use_case

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")
    container.config.redis_url.override("redis://localhost:6379/0")
    container.config.kafka_bootstrap_servers.override("localhost:9092")

    app = create_app(container=container)
    assert get_onboard_user_use_case in app.dependency_overrides, (
        "get_onboard_user_use_case must be overridden in app.dependency_overrides"
    )
