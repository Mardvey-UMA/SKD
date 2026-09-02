"""Structured JSON logging for the rec-system service."""
from __future__ import annotations

import logging
import os
from contextvars import ContextVar
from datetime import datetime, timezone

from pythonjsonlogger import json as pythonjsonlogger_json

request_id_var: ContextVar[str | None] = ContextVar("request_id", default=None)
user_id_var: ContextVar[str | None] = ContextVar("user_id", default=None)


class ContextJsonFormatter(pythonjsonlogger_json.JsonFormatter):
    """JSON log formatter that injects request-scoped context fields."""

    def add_fields(self, log_record: dict, record: logging.LogRecord, message_dict: dict) -> None:
        super().add_fields(log_record, record, message_dict)
        ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")
        log_record["timestamp"] = ts[:-3] + "Z"
        log_record["level"] = (log_record.get("level") or record.levelname).upper()
        log_record["logger"] = record.name
        log_record["service"] = "rec-system"
        log_record["environment"] = os.getenv("ENV", "development")
        log_record["request_id"] = request_id_var.get()
        log_record["user_id"] = user_id_var.get()
        for k in ("levelname", "asctime"):
            log_record.pop(k, None)


def configure_logging() -> None:
    """Replace root logger handlers with a single JSON stream handler."""
    handler = logging.StreamHandler()
    handler.setFormatter(ContextJsonFormatter())
    root = logging.getLogger()
    for h in list(root.handlers):
        root.removeHandler(h)
    root.addHandler(handler)
    root.setLevel(os.getenv("LOG_LEVEL", "INFO").upper())
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("aiokafka").setLevel(logging.WARNING)
