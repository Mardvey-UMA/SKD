"""Unit tests for PgBlockedSourcesRepository (Phase 1 user-sources).

Mocks the session factory — integration with a real DB is covered separately
in tests/integration/.
"""
from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4

import pytest


class TestPgBlockedSourcesRepository:
    def test_implements_port(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )

        repo = PgBlockedSourcesRepository(session_factory=MagicMock())
        assert isinstance(repo, BlockedSourcesRepository)

    @pytest.mark.asyncio
    async def test_get_source_ids_for_user_empty(self):
        """No rows → returns an empty set."""
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgBlockedSourcesRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_source_ids_for_user(uuid4())

        assert result == set()
        mock_session.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_source_ids_for_user_returns_set(self):
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )
        from src.infrastructure.persistence.models.rec_user_blocked_sources import (
            RecUserBlockedSourcesModel,
        )

        user_id = uuid4()
        s1 = uuid4()
        s2 = uuid4()

        row1 = MagicMock(spec=RecUserBlockedSourcesModel)
        row1.source_id = s1
        row2 = MagicMock(spec=RecUserBlockedSourcesModel)
        row2.source_id = s2

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [row1, row2]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgBlockedSourcesRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.get_source_ids_for_user(user_id)

        assert result == {s1, s2}

    @pytest.mark.asyncio
    async def test_add_uses_upsert_do_nothing(self):
        """add() must use INSERT ... ON CONFLICT DO NOTHING so re-adds are a no-op."""
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )

        executed_stmts = []

        mock_session = AsyncMock()

        async def capture_execute(stmt, *args, **kwargs):
            executed_stmts.append(stmt)
            return MagicMock()

        mock_session.execute = capture_execute
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgBlockedSourcesRepository(session_factory=MagicMock(return_value=mock_ctx))
        await repo.add(uuid4(), uuid4())

        assert len(executed_stmts) == 1
        sql_str = str(executed_stmts[0].compile(compile_kwargs={"literal_binds": False})).lower()
        assert "on conflict" in sql_str, (
            f"Expected ON CONFLICT DO NOTHING for idempotent insert, got: {sql_str}"
        )
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_remove_deletes_row(self):
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )

        executed_stmts = []

        mock_session = AsyncMock()

        async def capture_execute(stmt, *args, **kwargs):
            executed_stmts.append(stmt)
            result = MagicMock()
            result.rowcount = 1
            return result

        mock_session.execute = capture_execute
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgBlockedSourcesRepository(session_factory=MagicMock(return_value=mock_ctx))
        await repo.remove(uuid4(), uuid4())

        assert len(executed_stmts) == 1
        sql_str = str(executed_stmts[0].compile(compile_kwargs={"literal_binds": False})).lower()
        assert "delete" in sql_str
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_remove_nonexistent_does_not_raise(self):
        """remove() is idempotent: DELETE of a missing row returns without error."""
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )

        mock_session = AsyncMock()
        delete_result = MagicMock()
        delete_result.rowcount = 0
        mock_session.execute = AsyncMock(return_value=delete_result)
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgBlockedSourcesRepository(session_factory=MagicMock(return_value=mock_ctx))
        # Must not raise
        await repo.remove(uuid4(), uuid4())

    @pytest.mark.asyncio
    async def test_list_for_user_returns_blocked_sources(self):
        from src.infrastructure.persistence.pg_blocked_sources_repository import (
            PgBlockedSourcesRepository,
        )
        from src.infrastructure.persistence.models.rec_user_blocked_sources import (
            RecUserBlockedSourcesModel,
        )
        from src.domain.value_objects.blocked_source import BlockedSource

        s1 = uuid4()
        s2 = uuid4()
        t1 = datetime(2026, 4, 10, tzinfo=timezone.utc)
        t2 = datetime(2026, 4, 11, tzinfo=timezone.utc)

        row1 = MagicMock(spec=RecUserBlockedSourcesModel)
        row1.source_id = s1
        row1.blocked_at = t1
        row2 = MagicMock(spec=RecUserBlockedSourcesModel)
        row2.source_id = s2
        row2.blocked_at = t2

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [row1, row2]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        repo = PgBlockedSourcesRepository(session_factory=MagicMock(return_value=mock_ctx))
        result = await repo.list_for_user(uuid4())

        assert len(result) == 2
        assert all(isinstance(bs, BlockedSource) for bs in result)
        assert {bs.source_id for bs in result} == {s1, s2}


class TestRecUserBlockedSourcesModel:
    def test_table_name_and_schema(self):
        from src.infrastructure.persistence.models.rec_user_blocked_sources import (
            RecUserBlockedSourcesModel,
        )

        assert RecUserBlockedSourcesModel.__tablename__ == "rec_user_blocked_sources"
        assert RecUserBlockedSourcesModel.__table__.schema == "data_flow"

    def test_composite_primary_key(self):
        from sqlalchemy import inspect

        from src.infrastructure.persistence.models.rec_user_blocked_sources import (
            RecUserBlockedSourcesModel,
        )

        mapper = inspect(RecUserBlockedSourcesModel)
        pk_cols = {c.key for c in mapper.primary_key}
        assert pk_cols == {"user_id", "source_id"}
