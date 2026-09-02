"""Pydantic schemas for /recommendations and /recommendations/cold-start endpoints."""
from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Dict, List, Optional
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class RankingMode(str, Enum):
    """Phase 1 user-sources: ranking strategy selection.

    NORMAL — 6-component Scorer + DiversityFilter (default, backward compatible).
    NARROW — cosine+recency NarrowScorer, no diversity; requires include_source_ids.
    """

    NORMAL = "NORMAL"
    NARROW = "NARROW"


class RecommendationsRequestSchema(BaseModel):
    """Request schema for POST /recommendations endpoint."""

    user_id: UUID
    count: int = Field(default=120, ge=1)
    include_source_ids: Optional[List[UUID]] = None
    exclude_source_ids: Optional[List[UUID]] = None
    ranking_mode: RankingMode = RankingMode.NORMAL


class FeedItemDetail(BaseModel):
    """Per-item scoring breakdown for include_breakdown=true responses (P7)."""

    model_config = ConfigDict(populate_by_name=True)

    content_id: UUID
    raw_content_id: UUID
    final_score: float
    scoring_components: Dict[str, float]
    cold_start_applied: bool = False
    rerank_score: Optional[float] = None
    filtered_out_by: Optional[str] = None


class RecommendationsResponseSchema(BaseModel):
    """Response schema for POST /recommendations endpoint."""

    model_config = ConfigDict(populate_by_name=True)

    user_id: UUID
    items: List[UUID]
    count: int
    generated_at: datetime
    # P7 breakdown fields — present only when include_breakdown=true
    items_detailed: Optional[List[FeedItemDetail]] = None
    latency_breakdown: Optional[Dict[str, int]] = None
    profile_snapshot: Optional[Dict] = None
    feature_flags: Optional[Dict] = None


class ColdStartResponseSchema(BaseModel):
    """Response schema for GET /recommendations/cold-start endpoint."""

    items: List[UUID]
    count: int
    generated_at: datetime
