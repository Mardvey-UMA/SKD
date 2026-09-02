"""DTOs for categories API."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import List


@dataclass
class CategoryItem:
    """Single category item in the response."""

    id: str
    name: str
    icon: str


@dataclass
class CategoriesResponse:
    """Response DTO for GET /categories."""

    categories: List[CategoryItem]
    min_select: int
    max_select: int
