"""Pydantic schemas for /categories endpoint."""
from __future__ import annotations

from typing import List

from pydantic import BaseModel


class CategoryItemSchema(BaseModel):
    """Single category item in the response."""

    id: str
    name: str
    icon: str


class CategoriesResponseSchema(BaseModel):
    """Response schema for GET /categories endpoint."""

    categories: List[CategoryItemSchema]
    min_select: int
    max_select: int
