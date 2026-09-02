"""PostgreSQL implementation of InteractionRepository."""
from __future__ import annotations

from typing import Callable, List
from uuid import UUID

from sqlalchemy import select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.entities.user_interaction import UserInteraction
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.infrastructure.persistence.models.user_interactions import UserInteractionsModel


class PgInteractionRepository(InteractionRepository):
    """PostgreSQL adapter for user interactions."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    def _map_to_entity(self, model: UserInteractionsModel) -> UserInteraction:
        """Map UserInteractionsModel to UserInteraction domain entity."""
        return UserInteraction(
            id=model.id,
            event_id=model.event_id,
            user_id=str(model.user_id),
            post_id=model.post_id,
            event_type=model.event_type or "",
            duration_ms=model.duration_ms,
            scroll_pct=model.scroll_pct,
            max_scroll_pct=model.max_scroll_pct,
            created_at=model.created_at,
            processed=model.processed or False,
        )

    async def get_unprocessed_for_user(self, user_id: UUID) -> List[UserInteraction]:
        """Return all unprocessed interactions for a specific user."""
        stmt = (
            select(UserInteractionsModel)
            .where(UserInteractionsModel.processed == False)  # noqa: E712
            .where(UserInteractionsModel.user_id == user_id)
            .with_for_update(skip_locked=True)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [self._map_to_entity(row) for row in rows]

    async def get_unprocessed(self, limit: int) -> List[UserInteraction]:
        """Return up to limit unprocessed interactions across all users."""
        stmt = (
            select(UserInteractionsModel)
            .where(UserInteractionsModel.processed == False)  # noqa: E712
            .limit(limit)
            .with_for_update(skip_locked=True)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [self._map_to_entity(row) for row in rows]

    async def mark_processed(self, interaction_ids: List[int]) -> None:
        """Mark the given interaction IDs as processed."""
        stmt = (
            update(UserInteractionsModel)
            .where(UserInteractionsModel.id.in_(interaction_ids))
            .values(processed=True)
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()

    async def get_recent(self, user_id: UUID, limit: int) -> List[UserInteraction]:
        """Return up to limit most-recent interactions for user, ordered by created_at DESC."""
        stmt = (
            select(UserInteractionsModel)
            .where(UserInteractionsModel.user_id == user_id)
            .order_by(UserInteractionsModel.created_at.desc())
            .limit(limit)
        )
        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [self._map_to_entity(row) for row in rows]

    async def save_batch(self, interactions: List[UserInteraction]) -> None:
        """Insert interactions with ON CONFLICT (event_id) DO NOTHING."""
        if not interactions:
            return

        rows = [
            {
                "event_id": interaction.event_id,
                "user_id": UUID(interaction.user_id) if isinstance(interaction.user_id, str) else interaction.user_id,
                "post_id": interaction.post_id,
                "event_type": interaction.event_type,
                "duration_ms": interaction.duration_ms,
                "scroll_pct": interaction.scroll_pct,
                "max_scroll_pct": interaction.max_scroll_pct,
                "created_at": interaction.created_at,
                "processed": interaction.processed,
            }
            for interaction in interactions
        ]

        stmt = insert(UserInteractionsModel).values(rows).on_conflict_do_nothing(
            index_elements=["event_id"]
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()
