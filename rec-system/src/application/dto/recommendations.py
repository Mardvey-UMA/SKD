"""DTOs for recommendations API."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Optional
from uuid import UUID


@dataclass
class RecommendationsRequest:
    """Request DTO for POST /recommendations."""

    user_id: UUID
    count: int = 120
    include_source_ids: Optional[List[UUID]] = None
    exclude_source_ids: Optional[List[UUID]] = None
    ranking_mode: str = "NORMAL"
    include_breakdown: bool = False


@dataclass
class RecommendationsResponse:
    """Response DTO for POST /recommendations."""

    user_id: UUID
    items: List[UUID]
    count: int
    generated_at: datetime
    items_detailed: Optional[List] = None
    latency_breakdown: Optional[dict] = None
    profile_snapshot: Optional[dict] = None
    feature_flags: Optional[dict] = None
