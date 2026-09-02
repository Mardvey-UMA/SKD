"""Unit tests for PgCategoryRepository."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from src.domain.interfaces.category_repository import CategoryRepository


class TestPgCategoryRepository:
    """Tests for PgCategoryRepository."""

    def test_implements_interface(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository

        session_factory = MagicMock()
        repo = PgCategoryRepository(session_factory=session_factory)
        assert isinstance(repo, CategoryRepository)

    @pytest.mark.asyncio
    async def test_get_active_categories_returns_list(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository
        from src.infrastructure.persistence.models.category import CategoryModel
        from src.domain.entities.category import Category

        model = CategoryModel()
        model.id = "технологии"
        model.name = "Технологии"
        model.icon = "laptop"
        model.sort_order = 3
        model.is_active = True

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [model]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgCategoryRepository(session_factory=session_factory)
        result = await repo.get_active_categories()

        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], Category)
        assert result[0].id == "технологии"
        assert result[0].name == "Технологии"
        assert result[0].icon == "laptop"
        assert result[0].sort_order == 3
        assert result[0].is_active is True

    @pytest.mark.asyncio
    async def test_get_active_categories_filters_inactive(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository
        from src.infrastructure.persistence.models.category import CategoryModel

        # Repository filters by is_active=True in query -- so DB returns only active ones
        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgCategoryRepository(session_factory=session_factory)
        result = await repo.get_active_categories()

        # Verify query includes is_active filter
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "is_active" in compiled
        assert "categories" in compiled

    @pytest.mark.asyncio
    async def test_get_active_categories_ordered_by_sort_order(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgCategoryRepository(session_factory=session_factory)
        await repo.get_active_categories()

        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "sort_order" in compiled

    @pytest.mark.asyncio
    async def test_get_category_ids_returns_strings(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository
        from src.infrastructure.persistence.models.category import CategoryModel

        models = []
        for i, name in enumerate(["технологии", "наука", "спорт"]):
            m = CategoryModel()
            m.id = name
            m.name = name.capitalize()
            m.icon = "icon"
            m.sort_order = i + 1
            m.is_active = True
            models.append(m)

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = models
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgCategoryRepository(session_factory=session_factory)
        ids = await repo.get_category_ids()

        assert isinstance(ids, list)
        assert all(isinstance(i, str) for i in ids)
        assert "технологии" in ids
        assert "наука" in ids

    @pytest.mark.asyncio
    async def test_get_active_categories_empty_table(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgCategoryRepository(session_factory=session_factory)
        result = await repo.get_active_categories()

        assert result == []

    @pytest.mark.asyncio
    async def test_get_category_ids_empty_table(self):
        from src.infrastructure.persistence.pg_category_repository import PgCategoryRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgCategoryRepository(session_factory=session_factory)
        ids = await repo.get_category_ids()

        assert ids == []
