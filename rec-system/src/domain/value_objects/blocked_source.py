"""BlockedSource value object — one row of the per-user source blacklist."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID


@dataclass(frozen=True)
class BlockedSource:
    """Represents a single (source_id, blocked_at) pair owned by a user."""

    source_id: UUID
    blocked_at: datetime
