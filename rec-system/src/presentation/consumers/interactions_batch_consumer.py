"""Kafka consumer handler for the user.interactions.batch topic."""
from __future__ import annotations

import json
import logging
from uuid import UUID

from src.application.dto.kafka_events import InteractionItem, InteractionsBatchEvent
from src.application.use_cases.handle_interactions_batch import HandleInteractionsBatchUseCase
from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper
from src.infrastructure.messaging.timestamp_parser import parse_event_timestamp
from src.infrastructure.metrics import (
    rec_kafka_events_consumed_total,
    rec_kafka_events_failed_total,
)

logger = logging.getLogger(__name__)


class InteractionsBatchConsumer:
    """Consumes messages from the user.interactions.batch Kafka topic.

    Deserializes each message, constructs an InteractionsBatchEvent DTO, and dispatches
    to HandleInteractionsBatchUseCase. Malformed messages are logged and skipped.

    Lifecycle:
        - Call ``start()`` to start the underlying consumer.
        - Call ``stop()`` to signal graceful shutdown.
    """

    def __init__(
        self,
        consumer: KafkaConsumerWrapper,
        use_case: HandleInteractionsBatchUseCase,
    ) -> None:
        self._consumer = consumer
        self._use_case = use_case
        self.running: bool = True

    async def start(self) -> None:
        """Start the Kafka consumer and begin processing messages."""
        await self._consumer.start()
        self.running = True
        async for msg in self._consumer:
            if not self.running:
                break
            await self._process_message(msg)

    async def stop(self) -> None:
        """Signal graceful shutdown and stop the underlying consumer."""
        self.running = False
        await self._consumer.stop()

    async def _process_message(self, msg) -> None:
        """Deserialize and dispatch a single Kafka message.

        Malformed messages are logged and skipped — no exception is propagated.
        """
        try:
            payload = json.loads(msg.value.decode("utf-8"))
            raw_interactions = payload["interactions"]
            raw_batch_ts = payload["batch_ts"]

            interactions = [
                InteractionItem(
                    content_id=UUID(item["content_id"]),
                    action_type=item["action_type"],
                    duration_sec=item.get("duration_sec"),
                    timestamp=parse_event_timestamp(item["timestamp"]),
                )
                for item in raw_interactions
            ]

            event = InteractionsBatchEvent(
                event_type=payload["event_type"],
                user_id=UUID(payload["user_id"]),
                interactions=interactions,
                batch_ts=parse_event_timestamp(raw_batch_ts),
            )
        except (json.JSONDecodeError, KeyError, ValueError, TypeError, AttributeError) as exc:
            logger.warning("interactions_batch_consumer: skipping malformed message: %s", exc)
            return

        try:
            await self._use_case.execute(event)
            rec_kafka_events_consumed_total.inc()
        except Exception as exc:
            rec_kafka_events_failed_total.inc()
            logger.error(
                "interactions_batch_consumer: error processing batch for user_id=%s: %s",
                event.user_id,
                exc,
            )
