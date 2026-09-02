"""GetCategoriesUseCase application use case."""
from __future__ import annotations

from src.application.dto.categories import CategoriesResponse, CategoryItem
from src.domain.interfaces.category_repository import CategoryRepository
from src.domain.interfaces.config_repository import ConfigRepository


class GetCategoriesUseCase:
    """Returns the list of active categories for the onboarding UI.

    Reads categories from the reference table and onboarding params from rec_config.
    Implements GET /categories from design/08-api-contracts.md.
    """

    def __init__(
        self,
        category_repo: CategoryRepository,
        config_repo: ConfigRepository,
    ) -> None:
        self._category_repo = category_repo
        self._config_repo = config_repo

    async def execute(self, locale: str = "ru") -> CategoriesResponse:
        """Return active categories with min/max select bounds.

        Args:
            locale: Language locale (only 'ru' supported; parameter accepted but ignored).

        Returns:
            CategoriesResponse with categories list and selection bounds.
        """
        categories = await self._category_repo.get_active_categories()
        config = await self._config_repo.get_config("onboarding_params") or {}

        min_select: int = config.get("min_topics", 3)
        max_select: int = config.get("max_topics", 10)

        items = [
            CategoryItem(id=cat.id, name=cat.name, icon=cat.icon)
            for cat in categories
        ]

        return CategoriesResponse(
            categories=items,
            min_select=min_select,
            max_select=max_select,
        )
