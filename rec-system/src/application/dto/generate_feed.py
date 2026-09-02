from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional
from uuid import UUID


@dataclass
class GenerateFeedRequest:
    user_id: UUID
    feed_size: int = 200
    include_source_ids: Optional[List[UUID]] = None
    exclude_source_ids: Optional[List[UUID]] = None
    ranking_mode: str = "NORMAL"
    include_breakdown: bool = False


@dataclass
class GenerateFeedResponse:
    feed: List[Dict]
    meta: Dict
    items_detailed: Optional[List[Dict]] = None
    latency_breakdown: Optional[Dict[str, int]] = None
    profile_snapshot: Optional[Dict] = None
    feature_flags: Optional[Dict] = None
