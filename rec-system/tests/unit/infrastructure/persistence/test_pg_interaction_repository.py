"""Unit tests for PgInteractionRepository."""
from __future__ import annotations

import pytest
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4, UUID

from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.entities.user_interaction import UserInteraction
from src.infrastructure.persistence.models.user_interactions import UserInteractionsModel


class TestPgInteractionRepository:
    """Tests for PgInteractionRepository."""

    def test_implements_interface(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        session_factory = MagicMock()
        repo = PgInteractionRepository(session_factory=session_factory)
        assert isinstance(repo, InteractionRepository)

    def test_maps_to_domain_entity(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        session_factory = MagicMock()
        repo = PgInteractionRepository(session_factory=session_factory)

        event_id = uuid4()
        user_id = uuid4()
        model = UserInteractionsModel()
        model.id = 1
        model.event_id = event_id
        model.user_id = user_id
        model.post_id = 42
        model.event_type = "LIKE"
        model.duration_ms = 3000
        model.scroll_pct = 0.75
        model.max_scroll_pct = 0.95
        model.created_at = datetime(2024, 1, 15)
        model.processed = False

        entity = repo._map_to_entity(model)

        assert isinstance(entity, UserInteraction)
        assert entity.id == 1
        assert entity.event_id == event_id
        assert entity.post_id == 42
        assert entity.event_type == "LIKE"
        assert entity.processed is False

    @pytest.mark.asyncio
    async def test_get_unprocessed_for_user_query(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars = MagicMock(return_value=MagicMock(all=MagicMock(return_value=[])))
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgInteractionRepository(session_factory=session_factory)
        user_id = uuid4()
        result = await repo.get_unprocessed_for_user(user_id=user_id)

        assert isinstance(result, list)
        mock_session.execute.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "user_interactions" in compiled
        assert "processed" in compiled

    @pytest.mark.asyncio
    async def test_get_all_unprocessed_query(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars = MagicMock(return_value=MagicMock(all=MagicMock(return_value=[])))
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgInteractionRepository(session_factory=session_factory)
        result = await repo.get_unprocessed(limit=100)

        assert isinstance(result, list)
        mock_session.execute.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "user_interactions" in compiled
        assert "processed" in compiled

    @pytest.mark.asyncio
    async def test_mark_processed(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgInteractionRepository(session_factory=session_factory)
        await repo.mark_processed(interaction_ids=[1, 2, 3])

        mock_session.execute.assert_called_once()
        mock_session.commit.assert_called_once()
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "user_interactions" in compiled
        assert "processed" in compiled

    def test_has_save_batch_method(self):
        """InteractionRepository ABC must declare save_batch."""
        assert hasattr(InteractionRepository, "save_batch"), (
            "InteractionRepository must have save_batch abstract method"
        )

    @pytest.mark.asyncio
    async def test_save_batch_inserts_interactions(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgInteractionRepository(session_factory=session_factory)
        interaction = UserInteraction(
            id=0,
            event_id=uuid4(),
            user_id=str(uuid4()),
            post_id=uuid4(),
            event_type="LIKE",
            duration_ms=3000,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=datetime.now(),
            processed=False,
        )

        await repo.save_batch([interaction])

        mock_session.execute.assert_called_once()
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_save_batch_empty_list_does_nothing(self):
        from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository

        session_factory = MagicMock()
        repo = PgInteractionRepository(session_factory=session_factory)

        await repo.save_batch([])

        session_factory.assert_not_called()
