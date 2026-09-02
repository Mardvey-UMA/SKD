"""RED test: InteractionsBatchConsumer crashes on float timestamp.

Bug site: src/presentation/consumers/interactions_batch_consumer.py:65
  timestamp=datetime.fromisoformat(item["timestamp"].replace("Z", "+00:00"))

When item["timestamp"] is a float (epoch seconds sent by user-interactions-service),
calling .replace() on a float raises AttributeError which is NOT caught by the
except (json.JSONDecodeError, KeyError, ValueError, TypeError) block at line 76.

This test MUST FAIL (RED) until Phase P1 implements the fix.
Fix: accept both float/int (epoch) and str (ISO) forms.
"""
from __future__ import annotations

import json
import pytest
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4


FLOAT_TIMESTAMP = 1745066096.789  # epoch seconds — the problematic form


def _make_float_timestamp_message() -> MagicMock:
    """Build a Kafka message where item['timestamp'] is a float."""
    user_id = str(uuid4())
    content_id = str(uuid4())
    payload = {
        "event_type": "user.interactions.batch",
        "user_id": user_id,
        "batch_ts": "2026-04-19T12:00:00Z",
        "interactions": [
            {
                "content_id": content_id,
                "action_type": "view",
                "duration_sec": 5,
                "timestamp": FLOAT_TIMESTAMP,  # ← float, not ISO string
            }
        ],
    }
    msg = MagicMock()
    msg.value = json.dumps(payload).encode("utf-8")
    return msg


class TestInteractionsBatchConsumerFloatTimestamp:
    """Verifies the consumer handles a float timestamp without crashing."""

    @pytest.fixture
    def use_case_mock(self):
        return AsyncMock()

    @pytest.fixture
    def consumer_wrapper_mock(self):
        return AsyncMock()

    @pytest.fixture
    def consumer(self, consumer_wrapper_mock, use_case_mock):
        from src.presentation.consumers.interactions_batch_consumer import (
            InteractionsBatchConsumer,
        )
        from src.application.use_cases.handle_interactions_batch import (
            HandleInteractionsBatchUseCase,
        )

        return InteractionsBatchConsumer(
            consumer=consumer_wrapper_mock,
            use_case=use_case_mock,
        )

    @pytest.mark.asyncio
    async def test_process_message_with_float_timestamp_does_not_raise(
        self, consumer, use_case_mock
    ):
        """Consumer must NOT raise when item['timestamp'] is a float epoch value.

        Currently FAILS with:
          AttributeError: 'float' object has no attribute 'replace'
        at interactions_batch_consumer.py:65.
        """
        msg = _make_float_timestamp_message()
        # Should complete without raising — currently crashes with AttributeError
        await consumer._process_message(msg)

    @pytest.mark.asyncio
    async def test_process_message_with_float_timestamp_calls_use_case(
        self, consumer, use_case_mock
    ):
        """After fixing float timestamp, use_case.execute must be called once."""
        msg = _make_float_timestamp_message()
        await consumer._process_message(msg)
        use_case_mock.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_batch_ts_as_float_does_not_raise(self, consumer, use_case_mock):
        """Also verify batch_ts as float is handled.

        user-interactions-service sends batchTs: Instant — may also arrive as float.
        """
        user_id = str(uuid4())
        content_id = str(uuid4())
        payload = {
            "event_type": "user.interactions.batch",
            "user_id": user_id,
            "batch_ts": FLOAT_TIMESTAMP,  # ← batch_ts also as float
            "interactions": [
                {
                    "content_id": content_id,
                    "action_type": "click",
                    "duration_sec": None,
                    "timestamp": "2026-04-19T12:00:00Z",  # item ts as ISO string
                }
            ],
        }
        msg = MagicMock()
        msg.value = json.dumps(payload).encode("utf-8")
        # Should not raise — currently crashes on batch_ts line 74 with same bug
        await consumer._process_message(msg)
