from __future__ import annotations

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


class UserProfile:
    """Entity representing a user's recommendation profile."""

    def __init__(
        self,
        user_id: UUID,
        topic_vector: TopicVector,
        sentiment_prefs: SentimentPrefs,
        format_prefs: FormatPrefs,
        interaction_count: int,
        created_at: datetime,
        last_updated: datetime,
        embedding: Optional[List[float]] = None,
        cold_start: bool = True,
    ) -> None:
        self._user_id = user_id
        self._topic_vector = topic_vector
        self._embedding = embedding
        self._sentiment_prefs = sentiment_prefs
        self._format_prefs = format_prefs
        self._interaction_count = interaction_count
        self._created_at = created_at
        self._last_updated = last_updated
        self._cold_start = cold_start

    @property
    def user_id(self) -> UUID:
        return self._user_id

    @property
    def topic_vector(self) -> TopicVector:
        return self._topic_vector

    @property
    def embedding(self) -> Optional[List[float]]:
        return self._embedding

    @property
    def sentiment_prefs(self) -> SentimentPrefs:
        return self._sentiment_prefs

    @property
    def format_prefs(self) -> FormatPrefs:
        return self._format_prefs

    @property
    def interaction_count(self) -> int:
        return self._interaction_count

    @property
    def created_at(self) -> datetime:
        return self._created_at

    @property
    def last_updated(self) -> datetime:
        return self._last_updated

    @property
    def has_embedding(self) -> bool:
        return self._embedding is not None

    @property
    def is_cold_start(self) -> bool:
        """Returns True when embedding is None (new user without interaction history)."""
        return self._embedding is None

    @property
    def cold_start(self) -> bool:
        """Returns the explicit cold_start flag (set to False after onboarding)."""
        return self._cold_start

    def increment_interaction_count(self) -> "UserProfile":
        """Returns a new instance with interaction_count incremented by 1."""
        return UserProfile(
            user_id=self._user_id,
            topic_vector=self._topic_vector,
            embedding=self._embedding,
            sentiment_prefs=self._sentiment_prefs,
            format_prefs=self._format_prefs,
            interaction_count=self._interaction_count + 1,
            created_at=self._created_at,
            last_updated=self._last_updated,
            cold_start=self._cold_start,
        )

    def with_updated_topic_vector(self, topic_vector: TopicVector) -> "UserProfile":
        """Returns a new instance with the given TopicVector."""
        return UserProfile(
            user_id=self._user_id,
            topic_vector=topic_vector,
            embedding=self._embedding,
            sentiment_prefs=self._sentiment_prefs,
            format_prefs=self._format_prefs,
            interaction_count=self._interaction_count,
            created_at=self._created_at,
            last_updated=self._last_updated,
            cold_start=self._cold_start,
        )

    def with_updated_embedding(self, embedding: Optional[List[float]]) -> "UserProfile":
        """Returns a new instance with the given embedding."""
        return UserProfile(
            user_id=self._user_id,
            topic_vector=self._topic_vector,
            embedding=embedding,
            sentiment_prefs=self._sentiment_prefs,
            format_prefs=self._format_prefs,
            interaction_count=self._interaction_count,
            created_at=self._created_at,
            last_updated=self._last_updated,
            cold_start=self._cold_start,
        )

    def with_updated_sentiment_prefs(self, sentiment_prefs: SentimentPrefs) -> "UserProfile":
        """Returns a new instance with the given SentimentPrefs."""
        return UserProfile(
            user_id=self._user_id,
            topic_vector=self._topic_vector,
            embedding=self._embedding,
            sentiment_prefs=sentiment_prefs,
            format_prefs=self._format_prefs,
            interaction_count=self._interaction_count,
            created_at=self._created_at,
            last_updated=self._last_updated,
            cold_start=self._cold_start,
        )

    def with_updated_format_prefs(self, format_prefs: FormatPrefs) -> "UserProfile":
        """Returns a new instance with the given FormatPrefs."""
        return UserProfile(
            user_id=self._user_id,
            topic_vector=self._topic_vector,
            embedding=self._embedding,
            sentiment_prefs=self._sentiment_prefs,
            format_prefs=format_prefs,
            interaction_count=self._interaction_count,
            created_at=self._created_at,
            last_updated=self._last_updated,
            cold_start=self._cold_start,
        )
