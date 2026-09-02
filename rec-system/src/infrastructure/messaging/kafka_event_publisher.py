"""Kafka implementation of EventPublisher."""
from __future__ import annotations

import json
from datetime import datetime, timezone

from src.domain.interfaces.event_publisher import EventPublisher
from src.infrastructure.messaging.kafka_producer import KafkaProducerWrapper

RECOMMENDATIONS_UPDATED_TOPIC = "recommendations.updated"


class KafkaEventPublisher(EventPublisher):
    """Publishes domain events to Kafka topics.

    Kafka is mandatory -- no fallback. Exceptions propagate to caller.
    """

    def __init__(self, producer: KafkaProducerWrapper) -> None:
        self._producer = producer

    async def publish_recommendations_updated(self, user_id: str, reason: str) -> None:
        """Publish a recommendations.updated event to Kafka.

        Args:
            user_id: UUID string identifying the user.
            reason: One of: onboarding_complete | interactions_processed | manual
        """
        timestamp = datetime.now(timezone.utc).isoformat()
        payload = {
            "event_type": "recommendations.updated",
            "user_id": user_id,
            "reason": reason,
            "timestamp": timestamp,
        }
        value = json.dumps(payload).encode("utf-8")
        key = str(user_id).encode("utf-8")

        await self._producer.send(
            topic=RECOMMENDATIONS_UPDATED_TOPIC,
            key=key,
            value=value,
        )
