from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from uuid import UUID


@dataclass
class UserInteraction:
    """Raw user event stored in user_interactions table.

    Kotlin writes these records; Python reads and processes them.
    Composite identity: id (BIGSERIAL PK).
    """

    id: int
    event_id: UUID
    user_id: str
    post_id: int
    event_type: str  # IMPRESSION | OPEN | CLOSE | LIKE | DISLIKE | BOOKMARK
    duration_ms: Optional[int]
    scroll_pct: Optional[float]
    max_scroll_pct: Optional[float]
    created_at: datetime
    processed: bool

    @property
    def is_processed(self) -> bool:
        """Return True when this interaction has already been processed."""
        return self.processed

    def has_paired_close(self) -> bool:
        """Return True when this event type is OPEN.

        An OPEN event is the only event that can have a paired CLOSE.
        This is a lightweight helper used during signal classification:
        if the event is OPEN it *may* have a corresponding CLOSE event;
        callers are responsible for actually locating that CLOSE event.
        """
        return self.event_type == "OPEN"
