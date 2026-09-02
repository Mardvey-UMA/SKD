from __future__ import annotations

from abc import ABC, abstractmethod


class EventPublisher(ABC):
    """Abstract port for publishing domain events to Kafka."""

    @abstractmethod
    async def publish_recommendations_updated(
        self,
        user_id: str,
        reason: str,
    ) -> None:
        """Publish a recommendations.updated event.

        *reason* must be one of: onboarding_complete | interactions_processed | manual
        """
        ...
