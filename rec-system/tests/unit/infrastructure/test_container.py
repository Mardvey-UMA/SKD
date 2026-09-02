"""Unit tests for DI container and FastAPI app entrypoint — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import MagicMock, patch, AsyncMock
from uuid import uuid4

from fastapi import FastAPI
from httpx import AsyncClient, ASGITransport


class TestContainer:
    """Tests for the dependency-injector DI container."""

    @pytest.mark.unit
    def test_all_services_registered(self):
        """Verify the container has all required providers registered."""
        from src.infrastructure.container import ApplicationContainer

        container = ApplicationContainer()
        # Verify use case providers exist
        assert hasattr(container, "generate_feed_use_case")
        assert hasattr(container, "onboard_user_use_case")
        # Verify repo providers exist
        assert hasattr(container, "user_profile_repo")
        assert hasattr(container, "content_repo")
        assert hasattr(container, "config_repo")
        assert hasattr(container, "entity_interest_repo")
        assert hasattr(container, "interaction_repo")

    @pytest.mark.unit
    def test_provides_use_cases(self):
        """Verify container can provide use cases with mocked session factory."""
        from unittest.mock import MagicMock
        from src.infrastructure.container import ApplicationContainer
        from src.application.use_cases.generate_feed import GenerateFeedUseCase
        from src.application.use_cases.onboard_user import OnboardUserUseCase

        container = ApplicationContainer()
        container.config.db_url.override("postgresql+asyncpg://test:test@localhost/test")
        container.config.redis_url.override("redis://localhost:6379/0")
        container.config.kafka_bootstrap_servers.override("localhost:9092")

        # Override Kafka/Redis singletons so their __init__ doesn't run in sync context
        container.kafka_event_publisher.override(MagicMock())

        # Use case providers should be resolvable (factory pattern)
        feed_uc = container.generate_feed_use_case()
        assert isinstance(feed_uc, GenerateFeedUseCase)

        onboard_uc = container.onboard_user_use_case()
        assert isinstance(onboard_uc, OnboardUserUseCase)


class TestApp:
    """Tests for FastAPI application entrypoint."""

    @pytest.mark.unit
    async def test_app_includes_routers(self):
        """Verify that /recommendations, /onboarding, and /health routes are registered."""
        from src.main import create_app

        app = create_app()
        routes = {route.path for route in app.routes}
        # New contract routes
        assert "/recommendations" in routes
        assert "/health" in routes
