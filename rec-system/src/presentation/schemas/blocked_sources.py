"""Pydantic schemas for /users/{user_id}/blocked-sources endpoints (Phase 1 user-sources)."""
from __future__ import annotations

from datetime import datetime
from typing import List
from uuid import UUID

from pydantic import BaseModel


class BlockSourceRequestSchema(BaseModel):
    """POST body for creating a new blocked source."""

    source_id: UUID


class BlockedSourceItemSchema(BaseModel):
    """One entry in the blocked-sources list response."""

    source_id: UUID
    blocked_at: datetime


class BlockedSourcesListResponseSchema(BaseModel):
    """GET response listing all sources a user has blocked."""

    items: List[BlockedSourceItemSchema]
    count: int
