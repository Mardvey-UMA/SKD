"""FastAPI router for GET /categories endpoint."""
from __future__ import annotations

from fastapi import APIRouter, Depends

from src.application.use_cases.get_categories import GetCategoriesUseCase
from src.presentation.schemas.categories import CategoriesResponseSchema, CategoryItemSchema

categories_router = APIRouter()


async def get_categories_use_case() -> GetCategoriesUseCase:
    """Dependency provider for GetCategoriesUseCase.

    In production this is overridden by the DI container.
    """
    raise NotImplementedError("DI container must override get_categories_use_case")


@categories_router.get("/categories", response_model=CategoriesResponseSchema)
async def get_categories(
    locale: str = "ru",
    use_case: GetCategoriesUseCase = Depends(get_categories_use_case),
) -> CategoriesResponseSchema:
    """Return the category taxonomy for the onboarding UI.

    Args:
        locale: Language locale (only 'ru' supported; parameter accepted but ignored).
        use_case: GetCategoriesUseCase injected via DI.

    Returns:
        CategoriesResponseSchema with categories list and selection bounds.
    """
    result = await use_case.execute(locale=locale)

    category_items = [
        CategoryItemSchema(id=cat.id, name=cat.name, icon=cat.icon)
        for cat in result.categories
    ]
    return CategoriesResponseSchema(
        categories=category_items,
        min_select=result.min_select,
        max_select=result.max_select,
    )
