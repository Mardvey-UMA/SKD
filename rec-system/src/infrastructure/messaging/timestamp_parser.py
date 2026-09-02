"""Robust timestamp parser for Kafka payloads."""
from __future__ import annotations

from datetime import datetime, timezone


def parse_event_timestamp(value) -> datetime:
    """Parse timestamp from a Kafka payload field.

    Accepts:
    - ISO 8601 string: '2026-04-19T12:34:56.789Z' or '2026-04-19T12:34:56+00:00'
    - Epoch seconds as int/float: 1745066096 / 1745066096.789
    - Epoch milliseconds as int/float: 1745066096789 / 1745066096789.0

    Raises ValueError for any other input type or unparseable string.
    """
    if isinstance(value, str):
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    if isinstance(value, (int, float)):
        # Heuristic: values above 10^12 are milliseconds, otherwise seconds
        if value > 10**12:
            return datetime.fromtimestamp(value / 1000, tz=timezone.utc)
        return datetime.fromtimestamp(value, tz=timezone.utc)
    raise ValueError(f"Cannot parse timestamp: {value!r}")
