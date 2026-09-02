"""Tests for UserProfile cold_start field."""
from __future__ import annotations

import pytest
from datetime import datetime
from unittest.mock import MagicMock
from uuid import uuid4

from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


def _make_profile(user_id=None, embedding=None, cold_start=True):
    from src.domain.entities.user_profile import UserProfile

    return UserProfile(
        user_id=user_id or uuid4(),
        topic_vector=TopicVector.create_uniform(),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=0,
        created_at=datetime(2024, 1, 1),
        last_updated=datetime(2024, 6, 1),
        embedding=embedding,
        cold_start=cold_start,
    )


class TestUserProfileColdStart:
    def test_cold_start_defaults_to_true(self):
        from src.domain.entities.user_profile import UserProfile

        profile = UserProfile(
            user_id=uuid4(),
            topic_vector=TopicVector.create_uniform(),
            sentiment_prefs=SentimentPrefs.create_uniform(),
            format_prefs=FormatPrefs.create_default(),
            interaction_count=0,
            created_at=datetime(2024, 1, 1),
            last_updated=datetime(2024, 6, 1),
        )
        assert profile.cold_start is True

    def test_cold_start_can_be_set_to_false(self):
        profile = _make_profile(cold_start=False)
        assert profile.cold_start is False

    def test_cold_start_true_is_preserved(self):
        profile = _make_profile(cold_start=True)
        assert profile.cold_start is True

    def test_cold_start_is_independent_of_embedding(self):
        """cold_start is an explicit field, not just derived from embedding."""
        # Profile with embedding but cold_start=True
        profile = _make_profile(embedding=[0.1] * 312, cold_start=True)
        assert profile.cold_start is True

        # Profile without embedding but cold_start=False
        profile2 = _make_profile(embedding=None, cold_start=False)
        assert profile2.cold_start is False

    def test_existing_profile_construction_still_works(self):
        """Backward compat: existing code that does not pass cold_start still works."""
        from src.domain.entities.user_profile import UserProfile

        profile = UserProfile(
            user_id=uuid4(),
            topic_vector=TopicVector.create_uniform(),
            sentiment_prefs=SentimentPrefs.create_uniform(),
            format_prefs=FormatPrefs.create_default(),
            interaction_count=5,
            created_at=datetime(2024, 1, 1),
            last_updated=datetime(2024, 6, 1),
            embedding=[0.1] * 312,
        )
        # Default is True for backward compat
        assert profile.cold_start is True


class TestRecProfilesModelColdStart:
    def test_model_has_cold_start_column(self):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel

        assert hasattr(RecProfilesModel, "cold_start")

    def test_model_cold_start_column_is_boolean(self):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
        from sqlalchemy import Boolean

        col = RecProfilesModel.__table__.columns.get("cold_start")
        assert col is not None
        assert isinstance(col.type, Boolean)


class TestPgUserProfileRepositoryColdStart:
    def _make_model(self, user_id=None, embedding=None, cold_start=True):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel

        model = RecProfilesModel()
        model.user_id = user_id or uuid4()
        model.topic_vector = {t: 1.0 / 18 for t in TopicVector.TOPICS}
        model.embedding = embedding
        model.sentiment_prefs = {"POSITIVE": 0.5, "NEGATIVE": 0.25, "NEUTRAL": 0.25}
        model.format_prefs = {"avg_length": 200.0, "avg_complexity": 0.5}
        model.interaction_count = 0
        model.created_at = datetime(2024, 1, 1)
        model.last_updated = datetime(2024, 6, 1)
        model.cold_start = cold_start
        return model

    def test_map_to_entity_reads_cold_start_true(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        repo = PgUserProfileRepository(session_factory=MagicMock())
        model = self._make_model(cold_start=True)
        entity = repo._map_to_entity(model)

        assert entity.cold_start is True

    def test_map_to_entity_reads_cold_start_false(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository

        repo = PgUserProfileRepository(session_factory=MagicMock())
        model = self._make_model(cold_start=False)
        entity = repo._map_to_entity(model)

        assert entity.cold_start is False

    @pytest.mark.asyncio
    async def test_save_includes_cold_start(self):
        from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository
        from unittest.mock import AsyncMock

        mock_session = AsyncMock()
        mock_session.execute = AsyncMock()
        mock_session.commit = AsyncMock()

        mock_ctx = AsyncMock()
        mock_ctx.__aenter__ = AsyncMock(return_value=mock_session)
        mock_ctx.__aexit__ = AsyncMock(return_value=None)

        session_factory = MagicMock(return_value=mock_ctx)

        repo = PgUserProfileRepository(session_factory=session_factory)
        profile = _make_profile(cold_start=False)
        await repo.save(profile)

        mock_session.execute.assert_called_once()
        # Verify the cold_start column is referenced in the statement string
        call_args = mock_session.execute.call_args[0][0]
        # Use default compilation (no literal_binds to avoid JSONB rendering issues)
        compiled = str(call_args.compile())
        assert "cold_start" in compiled
