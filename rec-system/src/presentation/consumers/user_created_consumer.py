"""Kafka consumer handler for the user.created topic."""
from __future__ import annotations

import json
import logging
from datetime import datetime
from uuid import UUID

from src.application.dto.kafka_events import UserCreatedEvent
from src.application.use_cases.handle_user_created import HandleUserCreatedUseCase
from src.infrastructure.messaging.kafka_consumer import KafkaConsumerWrapper

logger = logging.getLogger(__name__)


class UserCreatedConsumer:
    """Consumes messages from the user.created Kafka topic.

    Deserializes each message, constructs a UserCreatedEvent DTO, and dispatches
    to HandleUserCreatedUseCase. Malformed messages are logged and skipped.

    Lifecycle:
        - Call ``start()`` to start the underlying consumer.
        - Call ``stop()`` to signal graceful shutdown.
    """

    def __init__(
        self,
        consumer: KafkaConsumerWrapper,
        use_case: HandleUserCreatedUseCase,
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
            event = UserCreatedEvent(
                event_type=payload["event_type"],
                user_id=UUID(payload["user_id"]),
                email=payload["email"],
                timestamp=datetime.fromisoformat(payload["timestamp"].replace("Z", "+00:00")),
            )
        except (json.JSONDecodeError, KeyError, ValueError) as exc:
            logger.warning("user_created_consumer: skipping malformed message: %s", exc)
            return

        try:
            await self._use_case.execute(event)
        except Exception as exc:
            logger.error("user_created_consumer: error processing event user_id=%s: %s", event.user_id, exc)
