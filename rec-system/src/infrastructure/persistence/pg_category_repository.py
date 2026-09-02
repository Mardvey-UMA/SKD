"""PostgreSQL implementation of CategoryRepository."""
from __future__ import annotations

from typing import Callable, List

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.entities.category import Category
from src.domain.interfaces.category_repository import CategoryRepository
from src.infrastructure.persistence.models.category import CategoryModel


class PgCategoryRepository(CategoryRepository):
    """PostgreSQL adapter for categories reference table."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    def _map_to_entity(self, model: CategoryModel) -> Category:
        """Map CategoryModel to Category domain entity."""
        return Category(
            id=model.id,
            name=model.name,
            icon=model.icon,
            sort_order=model.sort_order,
            is_active=model.is_active,
        )

    async def get_active_categories(self) -> List[Category]:
        """Return all active categories ordered by sort_order."""
        stmt = (
            select(CategoryModel)
            .where(CategoryModel.is_active == True)  # noqa: E712
            .order_by(CategoryModel.sort_order)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [self._map_to_entity(row) for row in rows]

    async def get_category_ids(self) -> List[str]:
        """Return IDs of all active categories ordered by sort_order."""
        categories = await self.get_active_categories()
        return [cat.id for cat in categories]
