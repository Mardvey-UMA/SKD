from __future__ import annotations

import uuid
from datetime import datetime
from typing import Dict, List, Optional


class ContentFeatures:
    """Entity representing a post with all extracted NLP features."""

    def __init__(
        self,
        post_id: uuid.UUID,
        content: str,
        post_date: datetime,
        title: Optional[str] = None,
        source_id: Optional[uuid.UUID] = None,
        source_type: Optional[str] = None,
        text_length: Optional[int] = None,
        word_count: Optional[int] = None,
        reading_time: Optional[float] = None,
        complexity: Optional[float] = None,
        is_short_form: Optional[bool] = None,
        is_long_form: Optional[bool] = None,
        topic_1: Optional[str] = None,
        topic_1_score: Optional[float] = None,
        topic_2: Optional[str] = None,
        topic_2_score: Optional[float] = None,
        topic_3: Optional[str] = None,
        topic_3_score: Optional[float] = None,
        sentiment: Optional[str] = None,
        sentiment_score: Optional[float] = None,
        entities_persons: Optional[List[str]] = None,
        entities_organizations: Optional[List[str]] = None,
        entities_locations: Optional[List[str]] = None,
        embedding: Optional[List[float]] = None,
        processed_at: Optional[datetime] = None,
    ) -> None:
        self._post_id = post_id
        self._content = content
        self._post_date = post_date
        self._title = title
        self._source_id = source_id
        self._source_type = source_type
        self._text_length = text_length
        self._word_count = word_count
        self._reading_time = reading_time
        self._complexity = complexity
        self._is_short_form = is_short_form
        self._is_long_form = is_long_form
        self._topic_1 = topic_1
        self._topic_1_score = topic_1_score
        self._topic_2 = topic_2
        self._topic_2_score = topic_2_score
        self._topic_3 = topic_3
        self._topic_3_score = topic_3_score
        self._sentiment = sentiment
        self._sentiment_score = sentiment_score
        self._entities_persons = entities_persons or []
        self._entities_organizations = entities_organizations or []
        self._entities_locations = entities_locations or []
        self._embedding = embedding
        self._processed_at = processed_at

    @property
    def post_id(self) -> uuid.UUID:
        return self._post_id

    @property
    def title(self) -> Optional[str]:
        return self._title

    @property
    def source_id(self) -> Optional[uuid.UUID]:
        return self._source_id

    @property
    def source_type(self) -> Optional[str]:
        return self._source_type

    @property
    def content(self) -> str:
        return self._content

    @property
    def post_date(self) -> datetime:
        return self._post_date

    @property
    def text_length(self) -> Optional[int]:
        return self._text_length

    @property
    def word_count(self) -> Optional[int]:
        return self._word_count

    @property
    def reading_time(self) -> Optional[float]:
        return self._reading_time

    @property
    def complexity(self) -> Optional[float]:
        return self._complexity

    @property
    def is_short_form(self) -> Optional[bool]:
        return self._is_short_form

    @property
    def is_long_form(self) -> Optional[bool]:
        return self._is_long_form

    @property
    def topic_1(self) -> Optional[str]:
        return self._topic_1

    @property
    def topic_1_score(self) -> Optional[float]:
        return self._topic_1_score

    @property
    def topic_2(self) -> Optional[str]:
        return self._topic_2

    @property
    def topic_2_score(self) -> Optional[float]:
        return self._topic_2_score

    @property
    def topic_3(self) -> Optional[str]:
        return self._topic_3

    @property
    def topic_3_score(self) -> Optional[float]:
        return self._topic_3_score

    @property
    def sentiment(self) -> Optional[str]:
        return self._sentiment

    @property
    def sentiment_score(self) -> Optional[float]:
        return self._sentiment_score

    @property
    def entities_persons(self) -> List[str]:
        return list(self._entities_persons)

    @property
    def entities_organizations(self) -> List[str]:
        return list(self._entities_organizations)

    @property
    def entities_locations(self) -> List[str]:
        return list(self._entities_locations)

    @property
    def embedding(self) -> Optional[List[float]]:
        return self._embedding

    @property
    def processed_at(self) -> Optional[datetime]:
        return self._processed_at

    def topic_dict(self) -> Dict[str, float]:
        """Returns mapping of topic name -> score for all present topics."""
        result: Dict[str, float] = {}
        if self._topic_1 is not None and self._topic_1_score is not None:
            result[self._topic_1] = self._topic_1_score
        if self._topic_2 is not None and self._topic_2_score is not None:
            result[self._topic_2] = self._topic_2_score
        if self._topic_3 is not None and self._topic_3_score is not None:
            result[self._topic_3] = self._topic_3_score
        return result

    def all_entities(self) -> List[str]:
        """Returns combined list of persons + organizations + locations."""
        return (
            list(self._entities_persons)
            + list(self._entities_organizations)
            + list(self._entities_locations)
        )

    @property
    def is_processed(self) -> bool:
        """Returns True when NLP features have been extracted."""
        return self._processed_at is not None

    @property
    def has_embedding(self) -> bool:
        """Returns True when embedding vector is present."""
        return self._embedding is not None
