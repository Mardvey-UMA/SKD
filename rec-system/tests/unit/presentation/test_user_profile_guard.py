"""Unit tests for ensure_user_profile_exists dependency — RED phase.

Tests cover:
- profile already exists → returns user_id, no save
- profile missing → creates cold_start profile, returns user_id
- concurrent creation (IntegrityError) → catches error, reads existing, returns user_id
- WARN log emitted on fallback auto-creation
"""
from __future__ import annotations

import logging
import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import UUID, uuid4

from sqlalchemy.exc import IntegrityError

from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector


def _make_existing_profile(user_id: UUID) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_uniform(),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=5,
        created_at=now,
        last_updated=now,
        embedding=None,
        cold_start=False,
    )


@pytest.fixture
def user_id() -> UUID:
    return uuid4()


@pytest.fixture
def profile_repo_with_existing(user_id: UUID) -> AsyncMock:
    repo = AsyncMock()
    repo.get_by_user_id.return_value = _make_existing_profile(user_id)
    repo.save.return_value = None
    return repo


@pytest.fixture
def profile_repo_empty() -> AsyncMock:
    repo = AsyncMock()
    repo.get_by_user_id.return_value = None
    repo.save.return_value = None
    return repo


@pytest.mark.unit
class TestEnsureUserProfileExists:
    """Tests for src.presentation.dependencies.user_profile_guard.ensure_user_profile_exists."""

    @pytest.mark.asyncio
    async def test_returns_user_id_when_profile_exists(
        self, user_id: UUID, profile_repo_with_existing: AsyncMock
    ) -> None:
        """When profile already exists, returns user_id without calling save."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        result = await ensure_user_profile_exists(
            user_id=user_id, profile_repo=profile_repo_with_existing
        )
        assert result == user_id

    @pytest.mark.asyncio
    async def test_no_save_when_profile_exists(
        self, user_id: UUID, profile_repo_with_existing: AsyncMock
    ) -> None:
        """When profile already exists, save is NOT called."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        await ensure_user_profile_exists(
            user_id=user_id, profile_repo=profile_repo_with_existing
        )
        profile_repo_with_existing.save.assert_not_called()

    @pytest.mark.asyncio
    async def test_creates_cold_start_profile_when_missing(
        self, user_id: UUID, profile_repo_empty: AsyncMock
    ) -> None:
        """When profile missing, save is called once with a cold_start=True profile."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        await ensure_user_profile_exists(
            user_id=user_id, profile_repo=profile_repo_empty
        )
        profile_repo_empty.save.assert_called_once()
        saved: UserProfile = profile_repo_empty.save.call_args[0][0]
        assert saved.cold_start is True
        assert saved.user_id == user_id

    @pytest.mark.asyncio
    async def test_returns_user_id_when_profile_missing(
        self, user_id: UUID, profile_repo_empty: AsyncMock
    ) -> None:
        """When profile missing and created, still returns the original user_id."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        result = await ensure_user_profile_exists(
            user_id=user_id, profile_repo=profile_repo_empty
        )
        assert result == user_id

    @pytest.mark.asyncio
    async def test_warn_log_emitted_on_fallback_creation(
        self, user_id: UUID, profile_repo_empty: AsyncMock, caplog: pytest.LogCaptureFixture
    ) -> None:
        """WARN is logged when auto-creating a profile (Kafka consumer lag suspected)."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        with caplog.at_level(logging.WARNING, logger="src.presentation.dependencies.user_profile_guard"):
            await ensure_user_profile_exists(
                user_id=user_id, profile_repo=profile_repo_empty
            )
        assert any("auto-creating" in record.message or "cold_start" in record.message
                   for record in caplog.records
                   if record.levelno >= logging.WARNING)

    @pytest.mark.asyncio
    async def test_integrity_error_falls_back_to_existing(
        self, user_id: UUID
    ) -> None:
        """Concurrent creation: IntegrityError on save → reads existing row and returns user_id."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        existing = _make_existing_profile(user_id)
        repo = AsyncMock()
        # First call: no profile found (race window)
        # Then save raises IntegrityError (concurrent insert)
        # Then get_by_user_id returns the existing profile
        repo.get_by_user_id.side_effect = [None, existing]
        repo.save.side_effect = IntegrityError("duplicate", {}, Exception())

        result = await ensure_user_profile_exists(user_id=user_id, profile_repo=repo)
        assert result == user_id

    @pytest.mark.asyncio
    async def test_integrity_error_does_not_propagate(
        self, user_id: UUID
    ) -> None:
        """IntegrityError during concurrent save is swallowed — no exception raised to caller."""
        from src.presentation.dependencies.user_profile_guard import ensure_user_profile_exists

        existing = _make_existing_profile(user_id)
        repo = AsyncMock()
        repo.get_by_user_id.side_effect = [None, existing]
        repo.save.side_effect = IntegrityError("duplicate", {}, Exception())

        # Must not raise
        await ensure_user_profile_exists(user_id=user_id, profile_repo=repo)
