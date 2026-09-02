"""Health checking for service dependencies."""
from __future__ import annotations

import asyncio
import logging
from typing import Optional

from sqlalchemy import text

logger = logging.getLogger(__name__)

_CHECK_TIMEOUT = 2.0


class HealthChecker:
    """Performs health checks against database, Redis, and Kafka consumer.

    When a dependency is None the corresponding check returns "ok" immediately
    (useful in tests that don't need real infrastructure).
    """

    def __init__(
        self,
        engine=None,
        redis_client=None,
        interactions_consumer=None,
    ) -> None:
        self._engine = engine
        self._redis = redis_client
        self._consumer = interactions_consumer

    async def check_database(self) -> str:
        if self._engine is None:
            return "ok"
        try:
            async def _ping():
                async with self._engine.connect() as conn:
                    await conn.execute(text("SELECT 1"))

            await asyncio.wait_for(_ping(), timeout=_CHECK_TIMEOUT)
            return "ok"
        except asyncio.TimeoutError:
            return "fail: connection timeout"
        except Exception as exc:
            return f"fail: {exc}"

    async def check_redis(self) -> str:
        if self._redis is None:
            return "ok"
        try:
            await asyncio.wait_for(self._redis.ping(), timeout=_CHECK_TIMEOUT)
            return "ok"
        except asyncio.TimeoutError:
            return "fail: ping timeout"
        except Exception as exc:
            return f"fail: {exc}"

    async def check_kafka_consumer(self) -> str:
        if self._consumer is None:
            return "ok"
        if not getattr(self._consumer, "running", False):
            return "fail: consumer not running"
        return "ok"

    async def run(self) -> dict[str, object]:
        db_status, redis_status, kafka_status = await asyncio.gather(
            self.check_database(),
            self.check_redis(),
            self.check_kafka_consumer(),
        )
        checks = {
            "database": db_status,
            "redis": redis_status,
            "kafka_consumer": kafka_status,
        }
        all_ok = all(v == "ok" for v in checks.values())
        return {
            "status": "ok" if all_ok else "degraded",
            "service": "rec-system",
            "checks": checks,
        }
