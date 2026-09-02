"""Tests for GetCategoriesUseCase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock

from src.application.dto.categories import CategoriesResponse, CategoryItem
from src.application.use_cases.get_categories import GetCategoriesUseCase
from src.domain.entities.category import Category


def make_category(id: str, sort_order: int, is_active: bool = True) -> Category:
    return Category(
        id=id,
        name=id.capitalize(),
        icon="icon",
        sort_order=sort_order,
        is_active=is_active,
    )


@pytest.fixture
def active_categories():
    return [
        make_category("технологии", 1),
        make_category("наука", 2),
        make_category("спорт", 3),
    ]


@pytest.fixture
def category_repo(active_categories):
    repo = AsyncMock()
    repo.get_active_categories.return_value = active_categories
    return repo


@pytest.fixture
def config_repo():
    repo = AsyncMock()
    repo.get_config.return_value = {"min_topics": 3, "max_topics": 10}
    return repo


@pytest.fixture
def sut(category_repo, config_repo):
    return GetCategoriesUseCase(
        category_repo=category_repo,
        config_repo=config_repo,
    )


@pytest.mark.unit
class TestGetCategoriesUseCase:
    @pytest.mark.asyncio
    async def test_execute_returns_categories_response(self, sut):
        """execute returns a CategoriesResponse."""
        result = await sut.execute()
        assert isinstance(result, CategoriesResponse)

    @pytest.mark.asyncio
    async def test_categories_have_correct_structure(self, sut):
        """Each category item has id, name, icon."""
        result = await sut.execute()
        assert len(result.categories) == 3
        for item in result.categories:
            assert isinstance(item, CategoryItem)
            assert item.id
            assert item.name
            assert item.icon

    @pytest.mark.asyncio
    async def test_categories_ordered_by_sort_order(self, sut):
        """Categories are returned in sort_order from the repo."""
        result = await sut.execute()
        ids = [c.id for c in result.categories]
        assert ids == ["технологии", "наука", "спорт"]

    @pytest.mark.asyncio
    async def test_only_active_categories_returned(self, category_repo, config_repo):
        """Repo already returns only active categories; use case passes them through."""
        category_repo.get_active_categories.return_value = [
            make_category("технологии", 1),
        ]
        sut = GetCategoriesUseCase(category_repo=category_repo, config_repo=config_repo)
        result = await sut.execute()
        assert len(result.categories) == 1
        assert result.categories[0].id == "технологии"

    @pytest.mark.asyncio
    async def test_min_select_defaults_to_3(self, category_repo):
        """min_select defaults to 3 when onboarding_params is missing."""
        config_repo = AsyncMock()
        config_repo.get_config.return_value = {}
        sut = GetCategoriesUseCase(category_repo=category_repo, config_repo=config_repo)
        result = await sut.execute()
        assert result.min_select == 3

    @pytest.mark.asyncio
    async def test_max_select_defaults_to_10(self, category_repo):
        """max_select defaults to 10 when onboarding_params is missing."""
        config_repo = AsyncMock()
        config_repo.get_config.return_value = {}
        sut = GetCategoriesUseCase(category_repo=category_repo, config_repo=config_repo)
        result = await sut.execute()
        assert result.max_select == 10

    @pytest.mark.asyncio
    async def test_min_max_from_config(self, sut):
        """min_select and max_select read from onboarding_params config."""
        result = await sut.execute()
        assert result.min_select == 3
        assert result.max_select == 10

    @pytest.mark.asyncio
    async def test_empty_categories_list(self, category_repo, config_repo):
        """Returns empty categories list when repo returns empty."""
        category_repo.get_active_categories.return_value = []
        sut = GetCategoriesUseCase(category_repo=category_repo, config_repo=config_repo)
        result = await sut.execute()
        assert result.categories == []

    @pytest.mark.asyncio
    async def test_locale_param_accepted(self, sut):
        """execute accepts locale parameter without error."""
        result = await sut.execute(locale="ru")
        assert isinstance(result, CategoriesResponse)
