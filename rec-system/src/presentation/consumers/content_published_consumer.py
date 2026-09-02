"""Kafka consumer handler for the content.published topic."""
from __future__ import annotations

import json
import logging
from uuid import UUID

from src.application.services.hot_content_encoder import HotContentEncoder
from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper

logger = logging.getLogger(__name__)


class ContentPublishedConsumer:
    """Consumes messages from the content.published Kafka topic.

    When REC_FEATURE_HOT_ARRIVAL=true, encodes each incoming post immediately
    via HotContentEncoder instead of waiting for the background scheduler.
    Malformed messages are logged and skipped — the loop never dies on a bad message.

    Lifecycle:
        - Call ``start()`` to begin consuming (no-op when enabled=False).
        - Call ``stop()`` to signal graceful shutdown.
    """

    def __init__(
        self,
        consumer: KafkaConsumerWrapper,
        encoder: HotContentEncoder,
        enabled: bool,
    ) -> None:
        self._consumer = consumer
        self._encoder = encoder
        self._enabled = enabled
        self.running: bool = True

    async def start(self) -> None:
        """Start the Kafka consumer and begin processing messages.

        Returns immediately if the feature flag is disabled.
        """
        if not self._enabled:
            logger.info("content_published_consumer disabled (REC_FEATURE_HOT_ARRIVAL=false)")
            return
        await self._consumer.start()
        logger.info("content_published_consumer connected to content.published topic")
        self.running = True
        async for msg in self._consumer:
            if not self.running:
                break
            await self._process_message(msg)

    async def stop(self) -> None:
        """Signal graceful shutdown and stop the underlying consumer."""
        self.running = False
        if self._enabled:
            await self._consumer.stop()

    async def _process_message(self, msg) -> None:
        """Deserialize one Kafka message and dispatch to HotContentEncoder.

        Malformed payloads are logged and skipped. Encoder errors are also
        caught so the consumer loop continues on the next message.
        """
        try:
            payload = json.loads(msg.value.decode("utf-8"))
            content_id = UUID(payload["content_id"])
        except (json.JSONDecodeError, KeyError, ValueError, TypeError) as exc:
            logger.warning(
                "content_published_consumer: skipping malformed message: %s", exc
            )
            return

        try:
            await self._encoder.process(content_id)
        except Exception as exc:
            logger.exception(
                "content_published_consumer: error encoding content_id=%s: %s",
                content_id,
                exc,
            )
