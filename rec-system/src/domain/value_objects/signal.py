from __future__ import annotations

from uuid import UUID

from src.domain.value_objects.event_type import EventType


class Signal:
    """Immutable value object representing a classified interaction signal."""

    def __init__(
        self,
        post_id: UUID,
        user_id: str,
        weight: float,
        event_type: EventType,
    ) -> None:
        self._post_id = post_id
        self._user_id = user_id
        self._weight = weight
        self._event_type = event_type

    @property
    def post_id(self) -> UUID:
        return self._post_id

    @property
    def user_id(self) -> str:
        return self._user_id

    @property
    def weight(self) -> float:
        return self._weight

    @property
    def event_type(self) -> EventType:
        return self._event_type

    @property
    def is_positive(self) -> bool:
        return self._weight > 0.0

    def is_strong_enough_for_entities(self, threshold: float = 0.4) -> bool:
        """Returns True if weight meets the threshold for updating entity interests."""
        return self._weight >= threshold
