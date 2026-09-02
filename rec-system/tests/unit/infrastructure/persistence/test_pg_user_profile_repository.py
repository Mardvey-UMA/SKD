"""Unit tests for PgUserProfileRepository."""
from __future__ import annotations

import pytest
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID, uuid4

from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs
from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel


class TestPgUserProfileRepository:
    """Tests for PgUserProfileRepository."""

    def _make_profile(self, user_id: UUID, embedding=None) -> UserProfile:
        return UserProfile(
            user_id=user_id,
            topic_vector=TopicVector.create_uniform(),
            sentiment_prefs=SentimentPrefs.create_uniform(),
            format_prefs=FormatPrefs.create_default(),
            interaction_count=5,
            created_at=datetime(2024, 1, 1),
            last_updated=datetime(2024, 6, 1),
            embedding=embedding,
        )

    def test_implements_interface(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        session_factory = MagicMock()
        repo = PgUserProfileRepository(session_factory=session_factory)
        assert isinstance(repo, UserProfileRepository)

    def test_get_by_user_id_maps_to_entity(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        session_factory = MagicMock()
        repo = PgUserProfileRepository(session_factory=session_factory)

        user_id = uuid4()
        model = RecProfilesModel()
        model.user_id = user_id
        model.topic_vector = {t: 1.0 / 18 for t in TopicVector.TOPICS}
        model.embedding = [0.1, 0.2, 0.3]
        model.sentiment_prefs = {"POSITIVE": 0.5, "NEGATIVE": 0.25, "NEUTRAL": 0.25}
        model.format_prefs = {"avg_length": 200.0, "avg_complexity": 0.5}
        model.interaction_count = 10
        model.created_at = datetime(2024, 1, 1)
        model.last_updated = datetime(2024, 6, 1)

        entity = repo._map_to_entity(model)

        assert isinstance(entity, UserProfile)
        assert entity.user_id == user_id
        assert entity.interaction_count == 10
        assert entity.embedding == [0.1, 0.2, 0.3]
        assert isinstance(entity.topic_vector, TopicVector)
        assert isinstance(entity.sentiment_prefs, SentimentPrefs)
        assert isinstance(entity.format_prefs, FormatPrefs)

    def test_handles_null_embedding(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        session_factory = MagicMock()
        repo = PgUserProfileRepository(session_factory=session_factory)

        user_id = uuid4()
        model = RecProfilesModel()
        model.user_id = user_id
        model.topic_vector = {t: 1.0 / 18 for t in TopicVector.TOPICS}
        model.embedding = None
        model.sentiment_prefs = {"POSITIVE": 0.5, "NEGATIVE": 0.25, "NEUTRAL": 0.25}
        model.format_prefs = {"avg_length": 200.0, "avg_complexity": 0.5}
        model.interaction_count = 0
        model.created_at = datetime(2024, 1, 1)
        model.last_updated = datetime(2024, 6, 1)

        entity = repo._map_to_entity(model)

        assert entity.embedding is None
        assert entity.is_cold_start is True

    @pytest.mark.asyncio
    async def test_save_inserts_new_profile(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgUserProfileRepository(session_factory=session_factory)
        profile = self._make_profile(uuid4())

        await repo.save(profile)

        mock_session.execute.assert_called_once()
        mock_session.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_save_updates_existing_profile(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)
        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgUserProfileRepository(session_factory=session_factory)
        profile = self._make_profile(uuid4(), embedding=[0.1, 0.2])

        # Call save twice — upsert should handle both insert and update
        await repo.save(profile)
        await repo.save(profile)

        assert mock_session.execute.call_count == 2
