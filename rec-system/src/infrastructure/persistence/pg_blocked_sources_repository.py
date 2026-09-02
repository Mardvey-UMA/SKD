"""PostgreSQL implementation of BlockedSourcesRepository (Phase 1 user-sources)."""
from __future__ import annotations

from typing import Callable, List, Set
from uuid import UUID

from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository
from src.domain.value_objects.blocked_source import BlockedSource
from src.infrastructure.persistence.models.rec_user_blocked_sources import (
    RecUserBlockedSourcesModel,
)


class PgBlockedSourcesRepository(BlockedSourcesRepository):
    """PostgreSQL adapter for the per-user source blacklist."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    async def get_source_ids_for_user(self, user_id: UUID) -> Set[UUID]:
        stmt = select(RecUserBlockedSourcesModel).where(
            RecUserBlockedSourcesModel.user_id == user_id
        )
        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return {row.source_id for row in rows}

    async def add(self, user_id: UUID, source_id: UUID) -> None:
        """INSERT ... ON CONFLICT DO NOTHING — preserves original blocked_at on retry."""
        stmt = (
            insert(RecUserBlockedSourcesModel)
            .values(user_id=user_id, source_id=source_id)
            .on_conflict_do_nothing(index_elements=["user_id", "source_id"])
        )
        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()

    async def remove(self, user_id: UUID, source_id: UUID) -> None:
        """DELETE — row-count zero is fine (idempotent)."""
        stmt = (
            delete(RecUserBlockedSourcesModel)
            .where(RecUserBlockedSourcesModel.user_id == user_id)
            .where(RecUserBlockedSourcesModel.source_id == source_id)
        )
        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()

    async def list_for_user(self, user_id: UUID) -> List[BlockedSource]:
        stmt = (
            select(RecUserBlockedSourcesModel)
            .where(RecUserBlockedSourcesModel.user_id == user_id)
            .order_by(RecUserBlockedSourcesModel.blocked_at.desc())
        )
        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.scalars().all()
            return [
                BlockedSource(source_id=row.source_id, blocked_at=row.blocked_at)
                for row in rows
            ]
