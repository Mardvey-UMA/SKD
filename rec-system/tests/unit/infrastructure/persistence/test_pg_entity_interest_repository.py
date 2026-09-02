"""Unit tests for PgEntityInterestRepository."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4

from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.domain.entities.entity_interest import EntityInterest


class TestPgEntityInterestRepository:
    """Tests for PgEntityInterestRepository."""

    def _make_interest(self) -> EntityInterest:
        return EntityInterest(
            user_id=str(uuid4()),
            entity_type="person",
            entity_name="Alice",
            weight=3.0,
            last_seen=datetime(2024, 6, 1, tzinfo=timezone.utc),
        )

    def test_implements_interface(self):
        from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository

        session_factory = MagicMock()
        repo = PgEntityInterestRepository(session_factory=session_factory)
        assert isinstance(repo, EntityInterestRepository)

    @pytest.mark.asyncio
    async def test_get_top_for_user_query(self):
        from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgEntityInterestRepository(session_factory=session_factory)
        user_id = uuid4()
        result = await repo.get_top_for_user(user_id=user_id, limit=10)

        assert isinstance(result, list)
        mock_session.execute.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "rec_entity_interests" in compiled
        assert "weight" in compiled

    @pytest.mark.asyncio
    async def test_upsert_increments_weight(self):
        from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgEntityInterestRepository(session_factory=session_factory)
        interest = self._make_interest()

        await repo.upsert(interest)

        mock_session.execute.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        # Should be an INSERT ... ON CONFLICT that references weight
        assert "rec_entity_interests" in compiled

    @pytest.mark.asyncio
    async def test_decay_multiplies_weight(self):
        from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgEntityInterestRepository(session_factory=session_factory)
        user_id = uuid4()

        await repo.decay_all_for_user(user_id=user_id, decay_factor=0.9)

        mock_session.execute.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "rec_entity_interests" in compiled
        assert "weight" in compiled

    @pytest.mark.asyncio
    async def test_cleanup_deletes_old(self):
        from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgEntityInterestRepository(session_factory=session_factory)
        user_id = uuid4()

        await repo.cleanup_expired(user_id=user_id, cleanup_days=30)

        mock_session.execute.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "rec_entity_interests" in compiled
        assert "last_seen" in compiled
