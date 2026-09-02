"""Unit tests for PgRecommendationHistoryRepository."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.domain.interfaces.recommendation_history_repository import RecommendationHistoryRepository


class TestPgRecommendationHistoryRepository:
    """Tests for PgRecommendationHistoryRepository."""

    def test_implements_interface(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        session_factory = MagicMock()
        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        assert isinstance(repo, RecommendationHistoryRepository)

    @pytest.mark.asyncio
    async def test_save_recommendations_inserts_rows(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        user_id = uuid4()
        content_ids = [uuid4(), uuid4(), uuid4()]

        await repo.save_recommendations(user_id=user_id, content_ids=content_ids)

        mock_session.execute.assert_called_once()
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_save_recommendations_uses_on_conflict_do_nothing(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        user_id = uuid4()
        content_ids = [uuid4()]

        await repo.save_recommendations(user_id=user_id, content_ids=content_ids)

        # Verify idempotent query (ON CONFLICT DO NOTHING)
        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "recommendation_history" in compiled
        assert "on conflict" in compiled.lower() and "do nothing" in compiled.lower()

    @pytest.mark.asyncio
    async def test_get_recommended_ids_returns_set(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )
        from src.infrastructure.persistence.models.recommendation_history import (
            RecommendationHistoryModel,
        )

        user_id = uuid4()
        content_id1 = uuid4()
        content_id2 = uuid4()

        model1 = RecommendationHistoryModel()
        model1.user_id = user_id
        model1.content_id = content_id1

        model2 = RecommendationHistoryModel()
        model2.user_id = user_id
        model2.content_id = content_id2

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [model1, model2]
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        result = await repo.get_recommended_ids(user_id=user_id)

        assert isinstance(result, set)
        assert content_id1 in result
        assert content_id2 in result
        assert len(result) == 2

    @pytest.mark.asyncio
    async def test_get_recommended_ids_empty_for_unknown_user(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []
        mock_session.execute = AsyncMock(return_value=mock_result)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        result = await repo.get_recommended_ids(user_id=uuid4())

        assert result == set()

    @pytest.mark.asyncio
    async def test_save_recommendations_empty_list_is_no_op(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        await repo.save_recommendations(user_id=uuid4(), content_ids=[])

        # Should not call execute for empty list
        mock_session.execute.assert_not_called()

    @pytest.mark.asyncio
    async def test_delete_older_than_returns_count(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.rowcount = 5
        mock_session.execute = AsyncMock(return_value=mock_result)
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        count = await repo.delete_older_than(days=30)

        assert count == 5
        mock_session.execute.assert_called_once()
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_delete_older_than_queries_recommended_at(self):
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )

        mock_session = AsyncMock()
        mock_result = MagicMock()
        mock_result.rowcount = 0
        mock_session.execute = AsyncMock(return_value=mock_result)
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)
        await repo.delete_older_than(days=30)

        call_args = mock_session.execute.call_args[0][0]
        compiled = str(call_args.compile(compile_kwargs={"literal_binds": True}))
        assert "recommendation_history" in compiled
        assert "recommended_at" in compiled

    @pytest.mark.asyncio
    async def test_different_users_have_isolated_histories(self):
        """Each get_recommended_ids call returns only that user's content_ids."""
        from src.infrastructure.persistence.pg_recommendation_history_repository import (
            PgRecommendationHistoryRepository,
        )
        from src.infrastructure.persistence.models.recommendation_history import (
            RecommendationHistoryModel,
        )

        user1 = uuid4()
        user2 = uuid4()
        content_id_u1 = uuid4()
        content_id_u2 = uuid4()

        def _make_model(uid, cid):
            m = RecommendationHistoryModel()
            m.user_id = uid
            m.content_id = cid
            return m

        # First call returns user1's content, second call returns user2's content
        call_index = 0

        async def side_effect(stmt):
            nonlocal call_index
            mock_result = MagicMock()
            if call_index == 0:
                mock_result.scalars.return_value.all.return_value = [
                    _make_model(user1, content_id_u1)
                ]
            else:
                mock_result.scalars.return_value.all.return_value = [
                    _make_model(user2, content_id_u2)
                ]
            call_index += 1
            return mock_result

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock(side_effect=side_effect)

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgRecommendationHistoryRepository(session_factory=session_factory)

        result1 = await repo.get_recommended_ids(user_id=user1)
        result2 = await repo.get_recommended_ids(user_id=user2)

        assert content_id_u1 in result1
        assert content_id_u2 not in result1
        assert content_id_u2 in result2
        assert content_id_u1 not in result2
