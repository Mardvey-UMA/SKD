"""DTOs for cold-start API."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import List
from uuid import UUID


@dataclass
class ColdStartResponse:
    """Response DTO for GET /recommendations/cold-start."""

    items: List[UUID]
    count: int
    generated_at: datetime
