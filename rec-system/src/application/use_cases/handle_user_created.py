"""HandleUserCreatedUseCase application use case."""
from __future__ import annotations

from src.application.dto.kafka_events import UserCreatedEvent
from src.domain.factories.user_profile_factory import create_cold_start_profile
from src.domain.interfaces.user_profile_repository import UserProfileRepository


class HandleUserCreatedUseCase:
    """Processes user.created Kafka events by creating an empty recommendation profile.

    Idempotent: skips if a profile already exists for the user.
    Implements the user.created consumer from design.md Sequence 3.
    """

    def __init__(self, profile_repo: UserProfileRepository) -> None:
        self._profile_repo = profile_repo

    async def execute(self, event: UserCreatedEvent) -> None:
        """Create an empty profile for the user, or skip if profile already exists.

        Args:
            event: UserCreatedEvent with user_id, email, and timestamp.
        """
        existing = await self._profile_repo.get_by_user_id(event.user_id)
        if existing is not None:
            # Idempotent: profile already exists, nothing to do
            return

        profile = create_cold_start_profile(event.user_id)
        await self._profile_repo.save(profile)
