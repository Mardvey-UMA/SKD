"""PostgreSQL implementation of EntityInterestRepository."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Callable, List
from uuid import UUID

from sqlalchemy import select, update, delete, text
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.entities.entity_interest import EntityInterest
from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.infrastructure.persistence.models.rec_entity_interests import RecEntityInterestsModel


class PgEntityInterestRepository(EntityInterestRepository):
    """PostgreSQL adapter for entity interests."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    def _map_to_entity(self, model: RecEntityInterestsModel) -> EntityInterest:
        """Map RecEntityInterestsModel to EntityInterest domain entity."""
        return EntityInterest(
            user_id=str(model.user_id),
            entity_type=model.entity_type,
            entity_name=model.entity_name,
            weight=model.weight or 1.0,
            last_seen=model.last_seen or datetime.utcnow(),
        )

    async def get_top_for_user(self, user_id: UUID, limit: int) -> List[EntityInterest]:
        """Return the top-weighted entity interests for user_id, up to limit."""
        stmt = (
            select(RecEntityInterestsModel)
            .where(RecEntityInterestsModel.user_id == user_id)
            .order_by(RecEntityInterestsModel.weight.desc())
            .limit(limit)
        )

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            rows = result.all()
            return [self._map_to_entity(row[0]) for row in rows]

    async def upsert(self, interest: EntityInterest) -> None:
        """Insert or update an entity interest record (weight += 1 on conflict)."""
        stmt = (
            insert(RecEntityInterestsModel)
            .values(
                user_id=interest.user_id,
                entity_type=interest.entity_type,
                entity_name=interest.entity_name,
                weight=interest.weight,
                last_seen=interest.last_seen,
            )
            .on_conflict_do_update(
                index_elements=["user_id", "entity_type", "entity_name"],
                set_={
                    "weight": RecEntityInterestsModel.weight + 1,
                    "last_seen": datetime.utcnow(),
                },
            )
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()

    async def decay_all_for_user(self, user_id: UUID, decay_factor: float) -> None:
        """Multiply all entity interest weights for user_id by decay_factor."""
        stmt = (
            update(RecEntityInterestsModel)
            .where(RecEntityInterestsModel.user_id == user_id)
            .values(weight=RecEntityInterestsModel.weight * decay_factor)
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()

    async def cleanup_expired(self, user_id: UUID, cleanup_days: int) -> None:
        """Remove entity interest records older than cleanup_days for user_id."""
        # Use naive UTC datetime — the DB column is TIMESTAMP WITHOUT TIME ZONE
        cutoff = datetime.utcnow() - timedelta(days=cleanup_days)
        stmt = (
            delete(RecEntityInterestsModel)
            .where(RecEntityInterestsModel.user_id == user_id)
            .where(RecEntityInterestsModel.last_seen < cutoff)
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()
