"""Factory for creating cold-start UserProfile instances.

Shared between HandleUserCreatedUseCase (Kafka path) and
ensure_user_profile_exists (HTTP safety-net path) to avoid duplication.
"""
from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID

from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector


def create_cold_start_profile(user_id: UUID) -> UserProfile:
    """Create an empty cold-start recommendation profile for the given user.

    Args:
        user_id: UUID of the user to create a profile for.

    Returns:
        A new UserProfile with cold_start=True, zero interaction count,
        uniform topic/sentiment vectors, and no embedding.
    """
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=user_id,
        topic_vector=TopicVector.create_uniform(),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=0,
        created_at=now,
        last_updated=now,
        embedding=None,
        cold_start=True,
    )
