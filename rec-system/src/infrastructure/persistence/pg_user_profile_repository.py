"""PostgreSQL implementation of UserProfileRepository."""
from __future__ import annotations

from typing import Callable, List, Optional
from uuid import UUID

from sqlalchemy.dialects.postgresql import insert
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.domain.entities.user_profile import UserProfile
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector
from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel


class PgUserProfileRepository(UserProfileRepository):
    """PostgreSQL adapter for user profiles."""

    def __init__(self, session_factory: Callable[[], AsyncSession]) -> None:
        self._session_factory = session_factory

    def _map_to_entity(self, model: RecProfilesModel) -> UserProfile:
        """Map RecProfilesModel to UserProfile domain entity."""
        embedding: Optional[List[float]] = None
        if model.embedding is not None:
            embedding = list(model.embedding)

        topic_vector = TopicVector(model.topic_vector or {})
        sentiment_prefs = SentimentPrefs(model.sentiment_prefs or {})
        format_prefs = FormatPrefs.from_dict(model.format_prefs or {"avg_length": 200.0, "avg_complexity": 0.5})

        cold_start = model.cold_start if model.cold_start is not None else True

        return UserProfile(
            user_id=model.user_id,
            topic_vector=topic_vector,
            sentiment_prefs=sentiment_prefs,
            format_prefs=format_prefs,
            interaction_count=model.interaction_count or 0,
            created_at=model.created_at,
            last_updated=model.last_updated,
            embedding=embedding,
            cold_start=cold_start,
        )

    async def get_by_user_id(self, user_id: UUID) -> Optional[UserProfile]:
        """Return the user profile for user_id, or None if not found."""
        stmt = select(RecProfilesModel).where(RecProfilesModel.user_id == user_id)

        async with self._session_factory() as session:
            result = await session.execute(stmt)
            model = result.scalar_one_or_none()
            if model is None:
                return None
            return self._map_to_entity(model)

    async def save(self, profile: UserProfile) -> None:
        """Persist (insert or update) a user profile."""
        embedding_value = profile.embedding

        # DB columns are TIMESTAMP WITHOUT TIME ZONE — strip tz info if present
        created_at = profile.created_at
        last_updated = profile.last_updated
        if created_at is not None and getattr(created_at, "tzinfo", None) is not None:
            created_at = created_at.replace(tzinfo=None)
        if last_updated is not None and getattr(last_updated, "tzinfo", None) is not None:
            last_updated = last_updated.replace(tzinfo=None)

        stmt = (
            insert(RecProfilesModel)
            .values(
                user_id=profile.user_id,
                topic_vector=profile.topic_vector.to_dict(),
                embedding=embedding_value,
                sentiment_prefs=profile.sentiment_prefs.to_dict(),
                format_prefs=profile.format_prefs.to_dict(),
                interaction_count=profile.interaction_count,
                created_at=created_at,
                last_updated=last_updated,
                cold_start=profile.cold_start,
            )
            .on_conflict_do_update(
                index_elements=["user_id"],
                set_={
                    "topic_vector": profile.topic_vector.to_dict(),
                    "embedding": embedding_value,
                    "sentiment_prefs": profile.sentiment_prefs.to_dict(),
                    "format_prefs": profile.format_prefs.to_dict(),
                    "interaction_count": profile.interaction_count,
                    "last_updated": last_updated,
                    "cold_start": profile.cold_start,
                },
            )
        )

        async with self._session_factory() as session:
            await session.execute(stmt)
            await session.commit()
