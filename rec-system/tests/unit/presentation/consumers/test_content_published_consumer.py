"""Unit tests for ContentPublishedConsumer — RED phase."""
from __future__ import annotations

import json
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import UUID, uuid4


@pytest.mark.unit
class TestContentPublishedConsumer:
    """Tests for the content.published Kafka consumer handler."""

    @pytest.fixture
    def encoder_mock(self):
        mock = AsyncMock()
        mock.process = AsyncMock()
        return mock

    @pytest.fixture
    def consumer_wrapper_mock(self):
        return AsyncMock()

    def _make_consumer(self, encoder_mock, consumer_wrapper_mock, enabled=True):
        from src.presentation.consumers.content_published_consumer import ContentPublishedConsumer
        return ContentPublishedConsumer(
            consumer=consumer_wrapper_mock,
            encoder=encoder_mock,
            enabled=enabled,
        )

    def test_importable(self):
        from src.presentation.consumers.content_published_consumer import ContentPublishedConsumer
        assert ContentPublishedConsumer is not None

    @pytest.mark.asyncio
    async def test_start_returns_immediately_when_disabled(self, encoder_mock, consumer_wrapper_mock):
        """When enabled=False, start() exits without connecting to Kafka."""
        consumer = self._make_consumer(encoder_mock, consumer_wrapper_mock, enabled=False)
        await consumer.start()
        consumer_wrapper_mock.start.assert_not_called()
        encoder_mock.process.assert_not_called()

    @pytest.mark.asyncio
    async def test_start_logs_disabled_info(self, encoder_mock, consumer_wrapper_mock):
        """start() emits an INFO log about disabled state when flag is off."""
        consumer = self._make_consumer(encoder_mock, consumer_wrapper_mock, enabled=False)
        with patch("src.presentation.consumers.content_published_consumer.logger") as mock_logger:
            await consumer.start()
        mock_logger.info.assert_called_once()

    @pytest.mark.asyncio
    async def test_process_message_dispatches_to_encoder(self, encoder_mock, consumer_wrapper_mock):
        """_process_message() extracts content_id UUID and calls encoder.process()."""
        consumer = self._make_consumer(encoder_mock, consumer_wrapper_mock, enabled=True)
        content_id = str(uuid4())
        payload = {
            "event_id": str(uuid4()),
            "occurred_at": "2026-04-19T12:00:00Z",
            "content_id": content_id,
            "source_id": str(uuid4()),
            "source_type": "telegram",
            "priority": "normal",
        }
        msg = MagicMock()
        msg.value = json.dumps(payload).encode("utf-8")

        await consumer._process_message(msg)

        encoder_mock.process.assert_called_once()
        call_arg = encoder_mock.process.call_args[0][0]
        assert isinstance(call_arg, UUID)
        assert str(call_arg) == content_id

    @pytest.mark.asyncio
    async def test_process_message_skips_malformed_json(self, encoder_mock, consumer_wrapper_mock):
        """_process_message() logs warning and skips when JSON is invalid."""
        consumer = self._make_consumer(encoder_mock, consumer_wrapper_mock, enabled=True)
        msg = MagicMock()
        msg.value = b"not-valid-json"

        with patch("src.presentation.consumers.content_published_consumer.logger") as mock_logger:
            await consumer._process_message(msg)

        encoder_mock.process.assert_not_called()
        mock_logger.warning.assert_called_once()

    @pytest.mark.asyncio
    async def test_process_message_skips_missing_content_id(self, encoder_mock, consumer_wrapper_mock):
        """_process_message() logs warning when content_id key is absent."""
        consumer = self._make_consumer(encoder_mock, consumer_wrapper_mock, enabled=True)
        msg = MagicMock()
        msg.value = json.dumps({"event_id": str(uuid4()), "source_type": "rss"}).encode("utf-8")

        with patch("src.presentation.consumers.content_published_consumer.logger") as mock_logger:
            await consumer._process_message(msg)

        encoder_mock.process.assert_not_called()
        mock_logger.warning.assert_called_once()

    @pytest.mark.asyncio
    async def test_process_message_swallows_encoder_exception(self, encoder_mock, consumer_wrapper_mock):
        """Exceptions from encoder.process() are caught so the consumer loop continues."""
        consumer = self._make_consumer(encoder_mock, consumer_wrapper_mock, enabled=True)
        encoder_mock.process.side_effect = RuntimeError("NLP failed")
        content_id = str(uuid4())
        payload = {
            "event_id": str(uuid4()),
            "occurred_at": "2026-04-19T12:00:00Z",
            "content_id": content_id,
            "source_type": "telegram",
        }
        msg = MagicMock()
        msg.value = json.dumps(payload).encode("utf-8")

        # Should not raise
        with patch("src.presentation.consumers.content_published_consumer.logger"):
            await consumer._process_message(msg)
