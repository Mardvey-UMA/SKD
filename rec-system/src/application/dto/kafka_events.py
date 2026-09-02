"""DTOs for Kafka message schemas."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Optional
from uuid import UUID


@dataclass
class ContentPublishedEvent:
    """DTO for content.published Kafka event (produced by parser-service).

    Assumed schema — parser-service defines the contract; rec-system consumes defensively.
    Only content_id is required for hot-encoding; all other fields are optional extras.
    """

    content_id: UUID
    event_id: Optional[UUID] = None
    occurred_at: Optional[datetime] = None
    source_id: Optional[UUID] = None
    source_type: Optional[str] = None
    priority: Optional[str] = None


@dataclass
class UserCreatedEvent:
    """DTO for user.created Kafka event."""

    event_type: str
    user_id: UUID
    email: str
    timestamp: datetime


@dataclass
class InteractionItem:
    """Single interaction within a batch event."""

    content_id: UUID
    action_type: str
    duration_sec: Optional[float]
    timestamp: datetime


@dataclass
class InteractionsBatchEvent:
    """DTO for user.interactions.batch Kafka event."""

    event_type: str
    user_id: UUID
    interactions: List[InteractionItem]
    batch_ts: datetime
