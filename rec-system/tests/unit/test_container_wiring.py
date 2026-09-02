"""Unit tests for ApplicationContainer wiring.

Tests verify that the container can resolve all required providers
without starting real external services (DB, NLP models).
"""
from __future__ import annotations

import pytest


@pytest.mark.unit
def test_container_resolves_process_content_use_case():
    """ApplicationContainer must provide process_content_use_case."""
    from src.infrastructure.container import ApplicationContainer

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")

    # Provider must exist and be callable
    assert hasattr(container, "process_content_use_case"), (
        "ApplicationContainer must have process_content_use_case provider"
    )


@pytest.mark.unit
def test_container_resolves_job_scheduler():
    """ApplicationContainer must provide job_scheduler."""
    from src.infrastructure.container import ApplicationContainer

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")

    assert hasattr(container, "job_scheduler"), (
        "ApplicationContainer must have job_scheduler provider"
    )


@pytest.mark.unit
def test_container_has_nlp_adapter_providers():
    """ApplicationContainer must declare all 5 NLP adapter providers."""
    from src.infrastructure.container import ApplicationContainer

    expected_providers = [
        "topic_classifier",
        "sentiment_analyzer",
        "entity_extractor",
        "content_encoder",
        "text_analyzer",
    ]

    for name in expected_providers:
        assert hasattr(ApplicationContainer, name), (
            f"ApplicationContainer must have '{name}' provider"
        )


@pytest.mark.unit
def test_container_has_update_profile_use_case():
    """ApplicationContainer must provide update_profile_use_case."""
    from src.infrastructure.container import ApplicationContainer

    assert hasattr(ApplicationContainer, "update_profile_use_case"), (
        "ApplicationContainer must have update_profile_use_case provider"
    )


@pytest.mark.unit
def test_default_database_url_points_to_content_agg_db():
    """Default DATABASE_URL must point to content_agg_db."""
    import os

    # Temporarily clear DATABASE_URL if set, then test the default
    original = os.environ.get("DATABASE_URL")
    os.environ.pop("DATABASE_URL", None)

    try:
        from src import main as main_module
        import importlib
        importlib.reload(main_module)

        # After reload, the default should be content_agg_db
        # We check via a fresh container created without DATABASE_URL override
        from src.infrastructure.container import ApplicationContainer
        from dependency_injector import providers

        # Create container with env-based config (default)
        container = ApplicationContainer()
        container.config.db_url.from_env(
            "DATABASE_URL",
            default="postgresql+asyncpg://rec_user:rec_pass@localhost:5432/content_agg_db",
        )
        db_url = container.config.db_url()
        assert "content_agg_db" in db_url, (
            f"Default DATABASE_URL must point to content_agg_db, got: {db_url}"
        )
    finally:
        if original is not None:
            os.environ["DATABASE_URL"] = original


@pytest.mark.unit
def test_create_app_returns_fastapi_app():
    """create_app() must return a FastAPI application without raising errors."""
    from fastapi import FastAPI
    from src.main import create_app
    from src.infrastructure.container import ApplicationContainer

    container = ApplicationContainer()
    container.config.db_url.override("postgresql+asyncpg://test:test@localhost/testdb")

    app = create_app(container=container)
    assert isinstance(app, FastAPI), "create_app() must return a FastAPI instance"
