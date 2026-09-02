"""Unit tests for parse_event_timestamp."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone

from src.infrastructure.messaging.timestamp_parser import parse_event_timestamp


@pytest.mark.unit
class TestParseEventTimestamp:
    def test_iso_string_with_z_suffix(self):
        result = parse_event_timestamp("2026-04-19T12:34:56.789Z")
        assert result == datetime(2026, 4, 19, 12, 34, 56, 789000, tzinfo=timezone.utc)

    def test_iso_string_with_offset(self):
        result = parse_event_timestamp("2026-04-19T12:34:56+00:00")
        assert result == datetime(2026, 4, 19, 12, 34, 56, tzinfo=timezone.utc)

    def test_epoch_seconds_int(self):
        result = parse_event_timestamp(1745066096)
        assert isinstance(result, datetime)
        assert result.tzinfo == timezone.utc
        assert result.timestamp() == pytest.approx(1745066096)

    def test_epoch_seconds_float(self):
        result = parse_event_timestamp(1745066096.789)
        assert isinstance(result, datetime)
        assert result.tzinfo == timezone.utc
        assert result.timestamp() == pytest.approx(1745066096.789, abs=1e-3)

    def test_epoch_milliseconds_int(self):
        result = parse_event_timestamp(1745066096789)
        assert isinstance(result, datetime)
        assert result.tzinfo == timezone.utc
        assert result.timestamp() == pytest.approx(1745066096.789, abs=1e-3)

    def test_epoch_milliseconds_float(self):
        result = parse_event_timestamp(1745066096789.0)
        assert isinstance(result, datetime)
        assert result.tzinfo == timezone.utc
        assert result.timestamp() == pytest.approx(1745066096.789, abs=1e-3)

    @pytest.mark.parametrize("bad", [None, [], {}, "hello", "not-a-date"])
    def test_invalid_input_raises_value_error(self, bad):
        with pytest.raises((ValueError, TypeError)):
            parse_event_timestamp(bad)
