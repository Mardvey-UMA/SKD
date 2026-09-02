"""Unit tests for Kafka consumer handlers — RED phase."""
from __future__ import annotations

import json
import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4


class TestUserCreatedConsumer:
    """Tests for UserCreatedConsumer (user.created topic)."""

    @pytest.fixture
    def use_case_mock(self):
        mock = AsyncMock()
        mock.execute = AsyncMock()
        return mock

    @pytest.fixture
    def consumer_wrapper_mock(self):
        mock = AsyncMock()
        return mock

    def test_importable(self):
        from src.presentation.consumers.user_created_consumer import UserCreatedConsumer
        assert UserCreatedConsumer is not None

    @pytest.mark.asyncio
    async def test_deserializes_valid_json_into_user_created_event(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.user_created_consumer import UserCreatedConsumer
        from src.application.dto.kafka_events import UserCreatedEvent

        user_id = str(uuid4())
        message = {
            "event_type": "user.created",
            "user_id": user_id,
            "email": "test@example.com",
            "timestamp": "2026-04-03T12:00:00Z",
        }
        msg = MagicMock()
        msg.value = json.dumps(message).encode("utf-8")

        async def async_gen():
            yield msg

        consumer_wrapper_mock.__aiter__ = MagicMock(return_value=async_gen())

        consumer = UserCreatedConsumer(
            consumer=consumer_wrapper_mock,
            use_case=use_case_mock,
        )

        await consumer._process_message(msg)

        use_case_mock.execute.assert_called_once()
        call_arg = use_case_mock.execute.call_args[0][0]
        assert isinstance(call_arg, UserCreatedEvent)
        assert str(call_arg.user_id) == user_id

    @pytest.mark.asyncio
    async def test_calls_handle_user_created_use_case_with_correct_event(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.user_created_consumer import UserCreatedConsumer

        user_id = str(uuid4())
        message = {
            "event_type": "user.created",
            "user_id": user_id,
            "email": "user@example.com",
            "timestamp": "2026-04-03T12:00:00Z",
        }
        msg = MagicMock()
        msg.value = json.dumps(message).encode("utf-8")

        consumer = UserCreatedConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)
        await consumer._process_message(msg)

        use_case_mock.execute.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_malformed_json_is_logged_and_skipped(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.user_created_consumer import UserCreatedConsumer

        msg = MagicMock()
        msg.value = b"this is not json {"

        consumer = UserCreatedConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)

        # Should not raise
        await consumer._process_message(msg)
        # Use case should NOT be called
        use_case_mock.execute.assert_not_called()

    @pytest.mark.asyncio
    async def test_start_and_stop_lifecycle(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.user_created_consumer import UserCreatedConsumer

        consumer = UserCreatedConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)
        await consumer.stop()
        # After stop, running flag should be False
        assert consumer.running is False


class TestInteractionsBatchConsumer:
    """Tests for InteractionsBatchConsumer (user.interactions.batch topic)."""

    @pytest.fixture
    def use_case_mock(self):
        mock = AsyncMock()
        mock.execute = AsyncMock()
        return mock

    @pytest.fixture
    def consumer_wrapper_mock(self):
        mock = AsyncMock()
        return mock

    def test_importable(self):
        from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer
        assert InteractionsBatchConsumer is not None

    @pytest.mark.asyncio
    async def test_deserializes_valid_json_into_interactions_batch_event(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer
        from src.application.dto.kafka_events import InteractionsBatchEvent

        user_id = str(uuid4())
        content_id = str(uuid4())
        message = {
            "event_type": "user.interactions.batch",
            "user_id": user_id,
            "interactions": [
                {
                    "content_id": content_id,
                    "action_type": "click",
                    "duration_sec": 45,
                    "timestamp": "2026-04-03T12:00:00Z",
                }
            ],
            "batch_ts": "2026-04-03T12:01:00Z",
        }
        msg = MagicMock()
        msg.value = json.dumps(message).encode("utf-8")

        consumer = InteractionsBatchConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)
        await consumer._process_message(msg)

        use_case_mock.execute.assert_called_once()
        call_arg = use_case_mock.execute.call_args[0][0]
        assert isinstance(call_arg, InteractionsBatchEvent)
        assert str(call_arg.user_id) == user_id
        assert len(call_arg.interactions) == 1

    @pytest.mark.asyncio
    async def test_calls_handle_interactions_batch_use_case(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer

        user_id = str(uuid4())
        content_id = str(uuid4())
        message = {
            "event_type": "user.interactions.batch",
            "user_id": user_id,
            "interactions": [
                {
                    "content_id": content_id,
                    "action_type": "view",
                    "duration_sec": 10,
                    "timestamp": "2026-04-03T12:00:00Z",
                }
            ],
            "batch_ts": "2026-04-03T12:01:00Z",
        }
        msg = MagicMock()
        msg.value = json.dumps(message).encode("utf-8")

        consumer = InteractionsBatchConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)
        await consumer._process_message(msg)

        use_case_mock.execute.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_malformed_json_is_logged_and_skipped(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer

        msg = MagicMock()
        msg.value = b"invalid json {"

        consumer = InteractionsBatchConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)

        # Should not raise
        await consumer._process_message(msg)
        # Use case should NOT be called
        use_case_mock.execute.assert_not_called()

    @pytest.mark.asyncio
    async def test_malformed_batch_message_missing_interactions_is_skipped(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer

        user_id = str(uuid4())
        # Valid JSON but missing required 'interactions' field
        message = {
            "event_type": "user.interactions.batch",
            "user_id": user_id,
            # Missing 'interactions' and 'batch_ts'
        }
        msg = MagicMock()
        msg.value = json.dumps(message).encode("utf-8")

        consumer = InteractionsBatchConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)

        # Should not raise
        await consumer._process_message(msg)
        # Use case should NOT be called
        use_case_mock.execute.assert_not_called()

    @pytest.mark.asyncio
    async def test_stop_sets_running_false(self, use_case_mock, consumer_wrapper_mock):
        from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer

        consumer = InteractionsBatchConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)
        await consumer.stop()
        assert consumer.running is False
