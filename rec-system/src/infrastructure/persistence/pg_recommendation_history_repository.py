"""PostgreSQL implementation of RecommendationHistoryRepository."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Callable, List, Set
from uuid import UUID

from sqlalchemy import select, delete
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.interfaces.recommendation_history_repository import RecommendationHistoryRepository
from src.infrastructure.persistence.models.recommendation_history import RecommendationHistoryModel


class PgRecommendationHistoryRepository(RecommendationHistoryRepository):
    """PostgreSQL adapter for recommendation history."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    async def get_recommended_ids(self, user_id: UUID) -> Set[UUID]:
        """Return set of content_ids already recommended to user_id."""
        stmt = (
            select(RecommendationHistoryModel)
            .where(RecommendationHistoryModel.user_id == user_id)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return {row.content_id for row in rows}

    async def save_recommendations(self, user_id: UUID, content_ids: List[UUID]) -> None:
        """Persist content_ids as recommended to user_id (idempotent, INSERT OR IGNORE)."""
        if not content_ids:
            return

        now = datetime.now(timezone.utc)
        values = [
            {"user_id": user_id, "content_id": cid, "recommended_at": now}
            for cid in content_ids
        ]

        stmt = (
            insert(RecommendationHistoryModel)
            .values(values)
            .on_conflict_do_nothing(index_elements=["user_id", "content_id"])
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()

    async def delete_for_user(self, user_id: UUID) -> int:
        """Delete ALL history entries for user_id. Returns number of rows deleted."""
        stmt = (
            delete(RecommendationHistoryModel)
            .where(RecommendationHistoryModel.user_id == user_id)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            await session.commit()
            return result.rowcount

    async def delete_older_than(self, days: int) -> int:
        """Delete history entries older than days days. Returns number of rows deleted."""
        cutoff = datetime.now(timezone.utc) - timedelta(days=days)
        stmt = (
            delete(RecommendationHistoryModel)
            .where(RecommendationHistoryModel.recommended_at < cutoff)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            await session.commit()
            return result.rowcount
