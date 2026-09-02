"""Thin async wrapper around AIOKafkaConsumer."""
from __future__ import annotations

from typing import AsyncIterator, TYPE_CHECKING

if TYPE_CHECKING:
    from aiokafka import AIOKafkaConsumer


class KafkaConsumerWrapper:
    """Wraps AIOKafkaConsumer with start/stop/iterate lifecycle methods."""

    def __init__(self, consumer: "AIOKafkaConsumer") -> None:
        self._consumer = consumer

    async def start(self) -> None:
        """Start the underlying Kafka consumer."""
        await self._consumer.start()

    async def stop(self) -> None:
        """Stop the underlying Kafka consumer."""
        await self._consumer.stop()

    def __aiter__(self):
        """Return the underlying consumer's async iterator."""
        return self._consumer.__aiter__()
