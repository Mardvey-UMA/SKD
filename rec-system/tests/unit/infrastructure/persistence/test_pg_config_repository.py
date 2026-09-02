"""Unit tests for PgConfigRepository."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock

from src.domain.interfaces.config_repository import ConfigRepository


class TestPgConfigRepository:
    """Tests for PgConfigRepository."""

    def test_implements_interface(self):
        from src.infrastructure.persistence.pg_config_repository import PgConfigRepository

        session_factory = MagicMock()
        repo = PgConfigRepository(session_factory=session_factory)
        assert isinstance(repo, ConfigRepository)

    @pytest.mark.asyncio
    async def test_get_config_by_key(self):
        from src.infrastructure.persistence.pg_config_repository import PgConfigRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_scalar = MagicMock()
        mock_scalar.value = {"freshness_weight": 0.3, "score_threshold": 0.1}
        mock_result.scalar_one_or_none = MagicMock(return_value=mock_scalar)
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgConfigRepository(session_factory=session_factory)
        result = await repo.get_config("scoring")

        assert isinstance(result, dict)
        assert result == {"freshness_weight": 0.3, "score_threshold": 0.1}

    @pytest.mark.asyncio
    async def test_missing_key_returns_none(self):
        from src.infrastructure.persistence.pg_config_repository import PgConfigRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none = MagicMock(return_value=None)
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgConfigRepository(session_factory=session_factory)
        result = await repo.get_config("nonexistent_key")

        assert result is None

    @pytest.mark.asyncio
    async def test_get_all_configs(self):
        from src.infrastructure.persistence.pg_config_repository import PgConfigRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()

        # Two config rows
        row1 = MagicMock()
        row1.key = "scoring"
        row1.value = {"freshness_weight": 0.3}
        row2 = MagicMock()
        row2.key = "retrieval"
        row2.value = {"limit": 100}
        mock_result.scalars = MagicMock(return_value=MagicMock(all=MagicMock(return_value=[row1, row2])))
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgConfigRepository(session_factory=session_factory)
        result = await repo.get_all_configs()

        assert isinstance(result, dict)
        assert "scoring" in result
        assert "retrieval" in result
