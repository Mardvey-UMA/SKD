"""Thin async wrapper around AIOKafkaProducer."""
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from aiokafka import AIOKafkaProducer


class KafkaProducerWrapper:
    """Wraps AIOKafkaProducer with start/stop/send lifecycle methods."""

    def __init__(self, producer: "AIOKafkaProducer") -> None:
        self._producer = producer

    async def start(self) -> None:
        """Start the underlying Kafka producer."""
        await self._producer.start()

    async def stop(self) -> None:
        """Stop the underlying Kafka producer."""
        await self._producer.stop()

    async def send(self, topic: str, key: bytes, value: bytes) -> None:
        """Send a message to the given topic and wait for acknowledgement."""
        await self._producer.send_and_wait(topic, key=key, value=value)
