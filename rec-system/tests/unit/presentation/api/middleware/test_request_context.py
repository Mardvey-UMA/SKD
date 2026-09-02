"""Unit tests for RequestContextMiddleware."""
import json
import logging
from io import StringIO

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from src.infrastructure.logging import ContextJsonFormatter, request_id_var, user_id_var
from src.presentation.api.middleware.request_context import RequestContextMiddleware


@pytest.fixture
def app_with_middleware():
    app = FastAPI()
    app.add_middleware(RequestContextMiddleware)

    @app.get("/test-log")
    def test_endpoint():
        logger = logging.getLogger("test.middleware")
        logger.info("endpoint_hit")
        return {"ok": True}

    return app


@pytest.fixture
def client(app_with_middleware):
    return TestClient(app_with_middleware)


def _setup_json_capture() -> tuple[logging.Logger, StringIO]:
    stream = StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(ContextJsonFormatter())
    logger = logging.getLogger("test.middleware")
    logger.handlers = [handler]
    logger.setLevel(logging.DEBUG)
    logger.propagate = False
    return logger, stream


def test_request_id_propagated_to_context(client):
    _, stream = _setup_json_capture()
    client.get("/test-log", headers={"X-Request-Id": "abc-123"})
    record = json.loads(stream.getvalue().strip())
    assert record["request_id"] == "abc-123"


def test_user_id_propagated_to_context(client):
    _, stream = _setup_json_capture()
    client.get("/test-log", headers={"X-User-Id": "user-uuid-456"})
    record = json.loads(stream.getvalue().strip())
    assert record["user_id"] == "user-uuid-456"


def test_request_id_null_when_header_absent(client):
    _, stream = _setup_json_capture()
    client.get("/test-log")
    record = json.loads(stream.getvalue().strip())
    assert record["request_id"] is None


def test_context_resets_after_response():
    assert request_id_var.get() is None
    assert user_id_var.get() is None
