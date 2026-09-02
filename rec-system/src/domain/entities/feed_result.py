from __future__ import annotations

from typing import List
from uuid import UUID

from src.domain.value_objects.post_score import PostScore
from src.domain.value_objects.feed_meta import FeedMeta


class FeedResult:
    """Entity wrapping a list of PostScore items and FeedMeta — output of feed generation."""

    def __init__(self, scores: List[PostScore], meta: FeedMeta) -> None:
        self._scores = list(scores)
        self._meta = meta

    @property
    def scores(self) -> List[PostScore]:
        return list(self._scores)

    @property
    def meta(self) -> FeedMeta:
        return self._meta

    @property
    def post_ids(self) -> List[UUID]:
        """Returns list of post_ids in order."""
        return [s.post_id for s in self._scores]

    @property
    def total(self) -> int:
        """Returns meta.total."""
        return self._meta.total
