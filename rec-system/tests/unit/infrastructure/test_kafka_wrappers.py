"""Unit tests for Kafka producer, consumer wrappers and KafkaEventPublisher."""
from __future__ import annotations

import json
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

from src.domain.interfaces.event_publisher import EventPublisher


class TestKafkaProducerWrapper:
    def test_importable(self):
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper
        assert KafkaProducerWrapper is not None

    @pytest.mark.asyncio
    async def test_start_calls_producer_start(self):
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = AsyncMock()
        wrapper = KafkaProducerWrapper(producer=mock_producer)
        await wrapper.start()
        mock_producer.start.assert_called_once()

    @pytest.mark.asyncio
    async def test_stop_calls_producer_stop(self):
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = AsyncMock()
        wrapper = KafkaProducerWrapper(producer=mock_producer)
        await wrapper.stop()
        mock_producer.stop.assert_called_once()

    @pytest.mark.asyncio
    async def test_send_calls_producer_send(self):
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = AsyncMock()
        mock_producer.send_and_wait = AsyncMock()
        wrapper = KafkaProducerWrapper(producer=mock_producer)

        topic = "recommendations.updated"
        key = b"user-id"
        value = b'{"event_type": "recommendations.updated"}'

        await wrapper.send(topic=topic, key=key, value=value)
        mock_producer.send_and_wait.assert_called_once_with(topic, key=key, value=value)


class TestKafkaConsumerWrapper:
    def test_importable(self):
        from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper
        assert KafkaConsumerWrapper is not None

    @pytest.mark.asyncio
    async def test_start_calls_consumer_start(self):
        from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper

        mock_consumer = AsyncMock()
        wrapper = KafkaConsumerWrapper(consumer=mock_consumer)
        await wrapper.start()
        mock_consumer.start.assert_called_once()

    @pytest.mark.asyncio
    async def test_stop_calls_consumer_stop(self):
        from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper

        mock_consumer = AsyncMock()
        wrapper = KafkaConsumerWrapper(consumer=mock_consumer)
        await wrapper.stop()
        mock_consumer.stop.assert_called_once()

    @pytest.mark.asyncio
    async def test_iterate_yields_messages(self):
        from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper

        msg1 = MagicMock()
        msg1.value = b'{"data": 1}'
        msg2 = MagicMock()
        msg2.value = b'{"data": 2}'

        async def async_gen():
            yield msg1
            yield msg2

        mock_consumer = MagicMock()
        mock_consumer.__aiter__ = MagicMock(return_value=async_gen())
        wrapper = KafkaConsumerWrapper(consumer=mock_consumer)

        messages = []
        async for msg in wrapper:
            messages.append(msg)

        assert len(messages) == 2
        assert messages[0].value == b'{"data": 1}'


class TestKafkaEventPublisher:
    def test_implements_event_publisher_interface(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        publisher = KafkaEventPublisher(producer=mock_producer)
        assert isinstance(publisher, EventPublisher)

    @pytest.mark.asyncio
    async def test_publish_sends_to_correct_topic(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        await publisher.publish_recommendations_updated(user_id=user_id, reason="onboarding_complete")

        mock_producer.send.assert_called_once()
        call_kwargs = mock_producer.send.call_args.kwargs
        assert call_kwargs["topic"] == "recommendations.updated"

    @pytest.mark.asyncio
    async def test_publish_uses_user_id_as_key(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        await publisher.publish_recommendations_updated(user_id=user_id, reason="manual")

        call_kwargs = mock_producer.send.call_args.kwargs
        assert call_kwargs["key"] == user_id.encode("utf-8")

    @pytest.mark.asyncio
    async def test_publish_message_has_correct_event_type(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        await publisher.publish_recommendations_updated(user_id=user_id, reason="interactions_processed")

        call_kwargs = mock_producer.send.call_args.kwargs
        payload = json.loads(call_kwargs["value"].decode("utf-8"))
        assert payload["event_type"] == "recommendations.updated"

    @pytest.mark.asyncio
    async def test_publish_message_has_correct_reason(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        reason = "onboarding_complete"
        await publisher.publish_recommendations_updated(user_id=user_id, reason=reason)

        call_kwargs = mock_producer.send.call_args.kwargs
        payload = json.loads(call_kwargs["value"].decode("utf-8"))
        assert payload["reason"] == reason

    @pytest.mark.asyncio
    async def test_publish_message_has_correct_user_id(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        await publisher.publish_recommendations_updated(user_id=user_id, reason="manual")

        call_kwargs = mock_producer.send.call_args.kwargs
        payload = json.loads(call_kwargs["value"].decode("utf-8"))
        assert payload["user_id"] == user_id

    @pytest.mark.asyncio
    async def test_publish_message_has_valid_iso8601_timestamp(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper
        from datetime import datetime

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        await publisher.publish_recommendations_updated(user_id=user_id, reason="manual")

        call_kwargs = mock_producer.send.call_args.kwargs
        payload = json.loads(call_kwargs["value"].decode("utf-8"))

        # Must be parseable as ISO8601
        ts = payload["timestamp"]
        # Try parsing it
        parsed = datetime.fromisoformat(ts.replace("Z", "+00:00"))
        assert parsed is not None

    @pytest.mark.asyncio
    async def test_publish_value_is_valid_json_bytes(self):
        from src.infrastructure.messaging.kafka_event_publisher import KafkaEventPublisher
        from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

        mock_producer = MagicMock(spec=KafkaProducerWrapper)
        mock_producer.send = AsyncMock()
        publisher = KafkaEventPublisher(producer=mock_producer)

        user_id = str(uuid4())
        await publisher.publish_recommendations_updated(user_id=user_id, reason="manual")

        call_kwargs = mock_producer.send.call_args.kwargs
        value = call_kwargs["value"]
        assert isinstance(value, bytes)
        parsed = json.loads(value.decode("utf-8"))
        assert isinstance(parsed, dict)
