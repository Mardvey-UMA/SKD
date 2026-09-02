from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from src.domain.entities.category import Category


class CategoryRepository(ABC):
    """Abstract port for reading categories from the reference table."""

    @abstractmethod
    async def get_active_categories(self) -> list[Category]:
        """Return all active categories ordered by sort_order."""
        ...

    @abstractmethod
    async def get_category_ids(self) -> list[str]:
        """Return the IDs of all active categories ordered by sort_order."""
        ...
