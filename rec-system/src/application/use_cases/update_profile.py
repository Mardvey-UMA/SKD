"""UpdateProfileUseCase application use case."""
from __future__ import annotations

from collections import defaultdict
from typing import Optional
from uuid import UUID

from src.application.dto.update_profile import UpdateProfileCommand, UpdateProfileResult
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.domain.interfaces.config_repository import ConfigRepository
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.services.signal_classifier import SignalClassifier
from src.domain.services.profile_updater import ProfileUpdater


class UpdateProfileUseCase:
    """Background job use case for updating user profiles from interactions.

    Fetches unprocessed interactions, groups by user_id, classifies signals,
    applies profile updates (EMA), manages entity interests, and marks processed.
    Implements design/04-user-profile.md section 3 and design/03-user-signals.md section 5.
    """

    def __init__(
        self,
        interaction_repo: InteractionRepository,
        profile_repo: UserProfileRepository,
        entity_interest_repo: EntityInterestRepository,
        config_repo: ConfigRepository,
        signal_classifier: SignalClassifier,
        profile_updater: ProfileUpdater,
        content_repo: ContentRepository,
    ) -> None:
        self._interaction_repo = interaction_repo
        self._profile_repo = profile_repo
        self._entity_interest_repo = entity_interest_repo
        self._config_repo = config_repo
        self._signal_classifier = signal_classifier
        self._profile_updater = profile_updater
        self._content_repo = content_repo

    async def execute(self, command: UpdateProfileCommand) -> UpdateProfileResult:
        """Fetch and process unprocessed interactions, update profiles.

        Args:
            command: UpdateProfileCommand with optional user_id for single-user mode

        Returns:
            UpdateProfileResult with users_updated and events_processed counts
        """
        config = await self._config_repo.get_config("profile") or {}

        # Fetch interactions: single user or all
        if command.user_id is not None:
            interactions = await self._interaction_repo.get_unprocessed_for_user(command.user_id)
        else:
            interactions = await self._interaction_repo.get_unprocessed(limit=10000)

        if not interactions:
            return UpdateProfileResult(users_updated=0, events_processed=0)

        # Group by user_id
        by_user: dict[str, list] = defaultdict(list)
        for interaction in interactions:
            by_user[interaction.user_id].append(interaction)

        users_updated = 0
        events_processed = 0

        for user_id_str, user_interactions in by_user.items():
            try:
                user_uuid = UUID(user_id_str)
            except (ValueError, AttributeError):
                continue

            # Load profile
            profile = await self._profile_repo.get_by_user_id(user_uuid)
            if profile is None:
                continue

            # Classify signals
            signals = self._signal_classifier.classify_batch(user_interactions)

            # Fetch content features for relevant posts
            post_ids = list({inter.post_id for inter in user_interactions})
            content_features_list = await self._content_repo.get_by_ids(post_ids)
            content_features_map = {cf.post_id: cf for cf in content_features_list}

            # Apply profile update
            updated_profile, entity_changes = self._profile_updater.apply_batch(
                profile=profile,
                signals=signals,
                content_features_map=content_features_map,
                config=config,
            )

            # Persist updated profile
            await self._profile_repo.save(updated_profile)

            # Upsert entity interests
            for entity_interest in entity_changes:
                await self._entity_interest_repo.upsert(entity_interest)

            # Apply entity decay
            decay_factor = config.get("entity_decay_factor", 0.9)
            await self._entity_interest_repo.decay_all_for_user(user_uuid, decay_factor)

            # Clean up expired entities
            cleanup_days = config.get("entity_cleanup_days", 30)
            await self._entity_interest_repo.cleanup_expired(user_uuid, cleanup_days)

            # Mark interactions as processed
            interaction_ids = [inter.id for inter in user_interactions]
            await self._interaction_repo.mark_processed(interaction_ids)

            users_updated += 1
            events_processed += len(user_interactions)

        return UpdateProfileResult(users_updated=users_updated, events_processed=events_processed)
