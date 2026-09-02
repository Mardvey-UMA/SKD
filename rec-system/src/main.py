"""FastAPI application entrypoint for the recommendation service."""
from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from src.infrastructure.logging import configure_logging
from src.presentation.api.middleware.request_context import RequestContextMiddleware

# Configure structured JSON logging at module level so all subsequent
# loggers (APScheduler, job logs, etc.) inherit the JSON formatter.
configure_logging()

from src.application.use_cases.get_recommendations import GetRecommendationsUseCase
from src.application.use_cases.get_cold_start import GetColdStartUseCase
from src.application.use_cases.get_categories import GetCategoriesUseCase
from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.infrastructure.container import ApplicationContainer
from src.presentation.api.recommendations_router import (
    recommendations_router,
    get_recommendations_use_case,
    get_cold_start_use_case,
)
from src.presentation.api.categories_router import categories_router, get_categories_use_case
from src.presentation.api.onboarding_router import onboarding_router, get_onboard_user_use_case
from src.presentation.api.health_router import health_router, get_health_checker
from src.presentation.api.metrics_router import metrics_router
from src.presentation.api.blocked_sources_router import (
    blocked_sources_router,
    get_blocked_sources_repository,
)
from src.presentation.api.explain_router import explain_router, get_explainer
from src.presentation.dependencies.user_profile_guard import get_user_profile_repository
from src.infrastructure.health import HealthChecker

_DEFAULT_DATABASE_URL = (
    "postgresql+asyncpg://rec_user:rec_pass@localhost:5432/content_agg_db"
)
_DEFAULT_REDIS_URL = "redis://localhost:6379/0"
_DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"

# T5: token-aware H+M+T truncation limits (tunable via env vars)
_DEFAULT_REC_MAX_NLP_TOKENS = 512
_DEFAULT_REC_MAX_CLEAN_TEXT_BYTES = 1_000_000
_DEFAULT_REC_TRUNCATE_TEXT_BYTES = 500_000
_DEFAULT_REC_CONFIG_CACHE_TTL = 60


def create_app(container: ApplicationContainer | None = None) -> FastAPI:
    """Create and configure the FastAPI application.

    Args:
        container: Optional pre-configured DI container (useful for testing).

    Returns:
        Configured FastAPI application with all routers included.
    """
    if container is None:
        container = ApplicationContainer()
        container.config.db_url.from_env(
            "DATABASE_URL",
            default=_DEFAULT_DATABASE_URL,
        )
        container.config.redis_url.from_env(
            "REDIS_URL",
            default=_DEFAULT_REDIS_URL,
        )
        container.config.kafka_bootstrap_servers.from_env(
            "KAFKA_BOOTSTRAP_SERVERS",
            default=_DEFAULT_KAFKA_BOOTSTRAP_SERVERS,
        )
        container.config.rec_max_nlp_tokens.from_env(
            "REC_MAX_NLP_TOKENS",
            default=_DEFAULT_REC_MAX_NLP_TOKENS,
            as_=int,
        )
        container.config.rec_max_clean_text_bytes.from_env(
            "REC_MAX_CLEAN_TEXT_BYTES",
            default=_DEFAULT_REC_MAX_CLEAN_TEXT_BYTES,
            as_=int,
        )
        container.config.rec_truncate_text_bytes.from_env(
            "REC_TRUNCATE_TEXT_BYTES",
            default=_DEFAULT_REC_TRUNCATE_TEXT_BYTES,
            as_=int,
        )
        container.config.rec_config_cache_ttl.from_env(
            "REC_CONFIG_CACHE_TTL_SECONDS",
            default=_DEFAULT_REC_CONFIG_CACHE_TTL,
            as_=int,
        )

    app_version = os.environ.get("APP_VERSION", "0.1.0")

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        """Manage application startup and shutdown lifecycle."""
        # Startup: wire the container
        container.wire(modules=[
            "src.presentation.api.recommendations_router",
            "src.presentation.api.categories_router",
            "src.presentation.api.onboarding_router",
            "src.presentation.api.blocked_sources_router",
            "src.presentation.api.explain_router",
            "src.presentation.dependencies.user_profile_guard",
        ])

        # --- Verify Redis connection (MANDATORY) ---
        redis_client = container.redis_client()
        try:
            await redis_client.ping()
        except Exception as exc:
            raise RuntimeError(f"Redis is unavailable: {exc}") from exc

        # --- Verify Kafka connection and start producer (MANDATORY) ---
        kafka_producer = container.kafka_producer_wrapper()
        try:
            await kafka_producer.start()
        except Exception as exc:
            raise RuntimeError(f"Kafka is unavailable: {exc}") from exc

        # --- Start Kafka consumers as background tasks ---
        user_created_consumer = container.user_created_consumer()
        interactions_batch_consumer = container.interactions_batch_consumer()

        consumer_task_user_created = asyncio.create_task(
            user_created_consumer.start(),
            name="user_created_consumer",
        )
        consumer_task_interactions_batch = asyncio.create_task(
            interactions_batch_consumer.start(),
            name="interactions_batch_consumer",
        )

        # --- Hot-content consumer (REC_FEATURE_HOT_ARRIVAL) ---
        hot_arrival_enabled = (
            os.environ.get("REC_FEATURE_HOT_ARRIVAL", "false").lower() == "true"
        )
        hot_content_consumer = container.content_published_consumer()
        if hot_arrival_enabled:
            consumer_task_hot_content = asyncio.create_task(
                hot_content_consumer.start(),
                name="content_published_consumer",
            )
        else:
            consumer_task_hot_content = None

        # --- Start background job scheduler ---
        scheduler = container.job_scheduler()

        # Wire cold-start refresh job with Redis + content_repo dependencies
        content_repo = container.content_repo()
        cache_client = container.redis_cache_client()
        scheduler.set_cold_start_dependencies(
            content_repo=content_repo,
            cache_client=cache_client,
        )

        # Wire history cleanup job with history_repo dependency
        history_repo = container.recommendation_history_repo()
        scheduler.set_history_cleanup_dependencies(history_repo=history_repo)

        scheduler.register_content_processing_job()
        scheduler.register_profile_update_job()
        scheduler.register_cold_start_refresh_job()
        scheduler.register_history_cleanup_job()
        scheduler.start()

        # Wire health checker with real infrastructure dependencies
        health_checker = HealthChecker(
            engine=container.async_engine(),
            redis_client=redis_client,
            interactions_consumer=interactions_batch_consumer,
        )
        app.dependency_overrides[get_health_checker] = lambda: health_checker

        yield

        # Shutdown: stop consumers, stop producer, close Redis, stop scheduler
        await user_created_consumer.stop()
        await interactions_batch_consumer.stop()
        if hot_arrival_enabled:
            await hot_content_consumer.stop()

        # Cancel consumer tasks gracefully
        tasks_to_cancel = [consumer_task_user_created, consumer_task_interactions_batch]
        if consumer_task_hot_content is not None:
            tasks_to_cancel.append(consumer_task_hot_content)
        for task in tasks_to_cancel:
            if not task.done():
                task.cancel()
                try:
                    await task
                except (asyncio.CancelledError, Exception):
                    pass

        await kafka_producer.stop()
        await redis_client.aclose()

        scheduler.stop()
        # dependency-injector versions inconsistently return coroutine vs None
        shutdown_result = container.shutdown_resources()
        if asyncio.iscoroutine(shutdown_result):
            await shutdown_result

    application = FastAPI(
        title="Recommendation Service",
        description="Python-based personalized feed recommendation service",
        version=app_version,
        lifespan=lifespan,
    )

    # Override FastAPI dependency providers with container factories.
    def _recommendations_dep() -> "GetRecommendationsUseCase":
        return container.get_recommendations_use_case()

    def _cold_start_dep() -> "GetColdStartUseCase":
        return container.get_cold_start_use_case()

    def _categories_dep() -> "GetCategoriesUseCase":
        return container.get_categories_use_case()

    def _onboard_user_dep() -> "OnboardUserUseCase":
        return container.onboard_user_use_case()

    def _user_profile_repo_dep():
        return container.user_profile_repo()

    def _blocked_sources_repo_dep():
        return container.blocked_sources_repo()

    def _explainer_dep():
        from src.application.services.scoring_explainer import ScoringExplainer
        return ScoringExplainer(
            scorer=container.scorer(),
            content_repo=container.content_repo(),
            profile_repo=container.user_profile_repo(),
            entity_interest_repo=container.entity_interest_repo(),
            config_repo=container.config_repo(),
        )

    application.dependency_overrides[get_recommendations_use_case] = _recommendations_dep
    application.dependency_overrides[get_cold_start_use_case] = _cold_start_dep
    application.dependency_overrides[get_categories_use_case] = _categories_dep
    application.dependency_overrides[get_onboard_user_use_case] = _onboard_user_dep
    application.dependency_overrides[get_user_profile_repository] = _user_profile_repo_dep
    application.dependency_overrides[get_blocked_sources_repository] = _blocked_sources_repo_dep
    application.dependency_overrides[get_explainer] = _explainer_dep

    # Register middleware
    application.add_middleware(RequestContextMiddleware)

    # Register routers
    application.include_router(recommendations_router)
    application.include_router(categories_router)
    application.include_router(onboarding_router)
    application.include_router(health_router)
    application.include_router(metrics_router)
    application.include_router(blocked_sources_router)
    application.include_router(explain_router)

    return application


app = create_app()
