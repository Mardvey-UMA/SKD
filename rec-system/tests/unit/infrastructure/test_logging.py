"""Unit tests for ContextJsonFormatter and configure_logging."""
import json
import logging
import os
from io import StringIO

import pytest

from src.infrastructure.logging import (
    ContextJsonFormatter,
    configure_logging,
    request_id_var,
    user_id_var,
)


@pytest.fixture(autouse=True)
def reset_context():
    tok_r = request_id_var.set(None)
    tok_u = user_id_var.set(None)
    yield
    request_id_var.reset(tok_r)
    user_id_var.reset(tok_u)


def _capture_log(level: int = logging.INFO) -> tuple[logging.Logger, StringIO]:
    stream = StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(ContextJsonFormatter())
    logger = logging.getLogger(f"test.{id(stream)}")
    logger.handlers = []
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False
    return logger, stream


def _parse(stream: StringIO) -> dict:
    return json.loads(stream.getvalue().strip())


def test_configure_logging_installs_json_formatter():
    configure_logging()
    root = logging.getLogger()
    assert any(isinstance(h.formatter, ContextJsonFormatter) for h in root.handlers)


def test_log_line_has_canonical_keys():
    logger, stream = _capture_log()
    logger.info("test_event")
    record = _parse(stream)
    for key in ("timestamp", "level", "logger", "service", "environment", "request_id", "user_id", "message"):
        assert key in record, f"missing key: {key}"


def test_service_always_rec_system():
    logger, stream = _capture_log()
    logger.info("x")
    assert _parse(stream)["service"] == "rec-system"


def test_request_id_populated_when_set():
    tok = request_id_var.set("req-abc-123")
    try:
        logger, stream = _capture_log()
        logger.info("x")
        assert _parse(stream)["request_id"] == "req-abc-123"
    finally:
        request_id_var.reset(tok)


def test_request_id_null_when_not_set():
    logger, stream = _capture_log()
    logger.info("x")
    assert _parse(stream)["request_id"] is None


def test_environment_from_env_var(monkeypatch):
    monkeypatch.setenv("ENV", "production")
    logger, stream = _capture_log()
    logger.info("x")
    assert _parse(stream)["environment"] == "production"


def test_environment_defaults_to_development(monkeypatch):
    monkeypatch.delenv("ENV", raising=False)
    logger, stream = _capture_log()
    logger.info("x")
    assert _parse(stream)["environment"] == "development"


def test_extra_fields_in_output():
    logger, stream = _capture_log()
    logger.info("feed_generated", extra={"count_returned": 30, "latency_ms": 145})
    record = _parse(stream)
    assert record["count_returned"] == 30
    assert record["latency_ms"] == 145


def test_timestamp_is_utc_iso8601():
    logger, stream = _capture_log()
    logger.info("x")
    ts = _parse(stream)["timestamp"]
    assert ts.endswith("Z")
    assert "T" in ts


def test_level_is_uppercase():
    logger, stream = _capture_log()
    logger.info("x")
    assert _parse(stream)["level"] == "INFO"
