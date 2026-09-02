"""Unit tests for expanded /health endpoint with per-dependency checks."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from src.infrastructure.health import HealthChecker


class TestHealthCheckerDatabase:
    """Tests for HealthChecker.check_database()."""

    @pytest.mark.unit
    async def test_ok_when_engine_is_none(self):
        checker = HealthChecker(engine=None)
        result = await checker.check_database()
        assert result == "ok"

    @pytest.mark.unit
    async def test_ok_when_select_succeeds(self):
        mock_conn = AsyncMock()
        ctx = AsyncMock()
        ctx.__aenter__.return_value = mock_conn
        ctx.__aexit__ = AsyncMock(return_value=False)
        mock_engine = MagicMock()
        mock_engine.connect.return_value = ctx

        checker = HealthChecker(engine=mock_engine)
        result = await checker.check_database()
        assert result == "ok"

    @pytest.mark.unit
    async def test_fail_when_engine_raises(self):
        mock_engine = MagicMock()
        mock_engine.connect.side_effect = Exception("connection refused")

        checker = HealthChecker(engine=mock_engine)
        result = await checker.check_database()
        assert result.startswith("fail:")


class TestHealthCheckerRedis:
    """Tests for HealthChecker.check_redis()."""

    @pytest.mark.unit
    async def test_ok_when_redis_is_none(self):
        checker = HealthChecker(redis_client=None)
        result = await checker.check_redis()
        assert result == "ok"

    @pytest.mark.unit
    async def test_ok_when_ping_succeeds(self):
        mock_redis = AsyncMock()
        mock_redis.ping.return_value = True

        checker = HealthChecker(redis_client=mock_redis)
        result = await checker.check_redis()
        assert result == "ok"

    @pytest.mark.unit
    async def test_fail_when_ping_raises(self):
        mock_redis = AsyncMock()
        mock_redis.ping.side_effect = Exception("redis unavailable")

        checker = HealthChecker(redis_client=mock_redis)
        result = await checker.check_redis()
        assert result.startswith("fail:")


class TestHealthCheckerKafkaConsumer:
    """Tests for HealthChecker.check_kafka_consumer()."""

    @pytest.mark.unit
    async def test_ok_when_consumer_is_none(self):
        checker = HealthChecker(interactions_consumer=None)
        result = await checker.check_kafka_consumer()
        assert result == "ok"

    @pytest.mark.unit
    async def test_ok_when_consumer_running(self):
        mock_consumer = MagicMock()
        mock_consumer.running = True

        checker = HealthChecker(interactions_consumer=mock_consumer)
        result = await checker.check_kafka_consumer()
        assert result == "ok"

    @pytest.mark.unit
    async def test_fail_when_consumer_not_running(self):
        mock_consumer = MagicMock()
        mock_consumer.running = False

        checker = HealthChecker(interactions_consumer=mock_consumer)
        result = await checker.check_kafka_consumer()
        assert result.startswith("fail:")


class TestHealthCheckerRun:
    """Tests for HealthChecker.run() aggregation."""

    def _make_ok_engine(self):
        mock_conn = AsyncMock()
        ctx = AsyncMock()
        ctx.__aenter__.return_value = mock_conn
        ctx.__aexit__ = AsyncMock(return_value=False)
        mock_engine = MagicMock()
        mock_engine.connect.return_value = ctx
        return mock_engine

    @pytest.mark.unit
    async def test_all_ok_returns_status_ok(self):
        mock_redis = AsyncMock()
        mock_redis.ping.return_value = True
        mock_consumer = MagicMock()
        mock_consumer.running = True

        checker = HealthChecker(
            engine=self._make_ok_engine(),
            redis_client=mock_redis,
            interactions_consumer=mock_consumer,
        )
        result = await checker.run()

        assert result["status"] == "ok"
        assert result["service"] == "rec-system"
        assert result["checks"]["database"] == "ok"
        assert result["checks"]["redis"] == "ok"
        assert result["checks"]["kafka_consumer"] == "ok"

    @pytest.mark.unit
    async def test_db_failure_returns_degraded(self):
        mock_engine = MagicMock()
        mock_engine.connect.side_effect = Exception("DB down")
        mock_redis = AsyncMock()
        mock_redis.ping.return_value = True
        mock_consumer = MagicMock()
        mock_consumer.running = True

        checker = HealthChecker(
            engine=mock_engine,
            redis_client=mock_redis,
            interactions_consumer=mock_consumer,
        )
        result = await checker.run()

        assert result["status"] == "degraded"
        assert result["checks"]["database"].startswith("fail:")
        assert result["checks"]["redis"] == "ok"

    @pytest.mark.unit
    async def test_kafka_not_running_returns_degraded(self):
        mock_consumer = MagicMock()
        mock_consumer.running = False

        checker = HealthChecker(interactions_consumer=mock_consumer)
        result = await checker.run()

        assert result["status"] == "degraded"
        assert result["checks"]["kafka_consumer"].startswith("fail:")

    @pytest.mark.unit
    async def test_default_checker_returns_ok(self):
        checker = HealthChecker()
        result = await checker.run()

        assert result["status"] == "ok"
        assert "checks" in result
        assert "service" in result


class TestHealthEndpointExpanded:
    """Tests for GET /health with expanded response via HTTP."""

    def _make_app(self, checker: HealthChecker):
        from fastapi import FastAPI
        from src.presentation.api.health_router import health_router, get_health_checker

        app = FastAPI()
        app.include_router(health_router)
        app.dependency_overrides[get_health_checker] = lambda: checker
        return app

    @pytest.mark.unit
    async def test_happy_path_all_checks_ok(self):
        from httpx import AsyncClient, ASGITransport

        mock_redis = AsyncMock()
        mock_redis.ping.return_value = True
        mock_consumer = MagicMock()
        mock_consumer.running = True

        checker = HealthChecker(redis_client=mock_redis, interactions_consumer=mock_consumer)
        app = self._make_app(checker)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")

        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "ok"
        assert body["service"] == "rec-system"
        assert "version" in body
        assert body["checks"]["database"] == "ok"
        assert body["checks"]["redis"] == "ok"
        assert body["checks"]["kafka_consumer"] == "ok"

    @pytest.mark.unit
    async def test_db_failure_returns_degraded_via_http(self):
        from httpx import AsyncClient, ASGITransport

        mock_engine = MagicMock()
        mock_engine.connect.side_effect = Exception("DB down")

        checker = HealthChecker(engine=mock_engine)
        app = self._make_app(checker)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")

        body = response.json()
        assert body["status"] == "degraded"
        assert body["checks"]["database"].startswith("fail:")

    @pytest.mark.unit
    async def test_kafka_not_running_via_http(self):
        from httpx import AsyncClient, ASGITransport

        mock_consumer = MagicMock()
        mock_consumer.running = False

        checker = HealthChecker(interactions_consumer=mock_consumer)
        app = self._make_app(checker)

        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get("/health")

        body = response.json()
        assert body["status"] == "degraded"
        assert body["checks"]["kafka_consumer"].startswith("fail:")
