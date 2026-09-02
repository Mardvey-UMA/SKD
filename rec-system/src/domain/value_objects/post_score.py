from __future__ import annotations

from functools import total_ordering
from uuid import UUID


@total_ordering
class PostScore:
    """Immutable value object pairing a post_id with its computed score."""

    def __init__(self, post_id: UUID, score: float) -> None:
        self._post_id = post_id
        self._score = score

    @property
    def post_id(self) -> UUID:
        return self._post_id

    @property
    def score(self) -> float:
        return self._score

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, PostScore):
            return NotImplemented
        return self._score == other._score

    def __lt__(self, other: "PostScore") -> bool:
        if not isinstance(other, PostScore):
            return NotImplemented
        return self._score < other._score
