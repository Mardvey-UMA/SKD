"""FastAPI router for GET /health endpoint."""
from __future__ import annotations

import os

from fastapi import APIRouter, Depends

from src.infrastructure.health import HealthChecker

health_router = APIRouter()

_DEFAULT_VERSION = "1.2.0"

# Shared no-dependency checker returned when the app hasn't wired real deps.
# All checks return "ok" so that tests that only care about the old contract
# (status/version) continue to pass without mocking infrastructure.
_DEFAULT_CHECKER = HealthChecker()


def get_health_checker() -> HealthChecker:
    return _DEFAULT_CHECKER


@health_router.get("/health")
async def health_check(checker: HealthChecker = Depends(get_health_checker)) -> dict:
    version = os.environ.get("APP_VERSION", _DEFAULT_VERSION)
    result = await checker.run()
    result["version"] = version
    return result
