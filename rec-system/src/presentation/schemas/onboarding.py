"""Pydantic schemas for /onboarding endpoint."""
from __future__ import annotations

from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel, Field


class OnboardingRequestSchema(BaseModel):
    """Request schema for POST /onboarding endpoint."""

    user_id: UUID
    categories: List[str] = Field(min_length=3)
    source_content_ids: Optional[List[UUID]] = Field(default=None, max_length=10)


class OnboardingResponseSchema(BaseModel):
    """Response schema for POST /onboarding endpoint."""

    user_id: UUID
    profile_initialized: bool


class ErrorResponseSchema(BaseModel):
    """Error response schema for 404/503 responses."""

    error: str
    user_id: Optional[UUID] = None
    message: str
