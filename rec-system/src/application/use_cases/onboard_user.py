"""OnboardUserUseCase application use case."""
from __future__ import annotations

import statistics
from typing import List, Optional
from uuid import UUID

from src.application.dto.onboard import OnboardRequest, OnboardResponse
from src.domain.interfaces.cache_client import CacheClient
from src.domain.interfaces.category_repository import CategoryRepository
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.event_publisher import EventPublisher
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.interfaces.config_repository import ConfigRepository
from src.domain.services.onboarding_service import OnboardingService


class OnboardUserUseCase:
    """Initializes a recommendation profile for a new user.

    Creates the profile via OnboardingService, handles optional embedding warmup
    from source_content_ids, sets cold_start=False, and publishes a Kafka event.
    Implements POST /onboarding from design/08-api-contracts.md.
    """

    def __init__(
        self,
        profile_repo: UserProfileRepository,
        config_repo: ConfigRepository,
        onboarding_service: OnboardingService,
        category_repo: Optional[CategoryRepository] = None,
        content_repo: Optional[ContentRepository] = None,
        event_publisher: Optional[EventPublisher] = None,
    ) -> None:
        self._profile_repo = profile_repo
        self._config_repo = config_repo
        self._onboarding_service = onboarding_service
        self._category_repo = category_repo
        self._content_repo = content_repo
        self._event_publisher = event_publisher

    async def execute(self, request: OnboardRequest) -> OnboardResponse:
        """Create and persist a new user profile from onboarding category selection.

        Args:
            request: OnboardRequest with user_id, categories, and optional source_content_ids.

        Returns:
            OnboardResponse with user_id and profile_initialized=True.

        Raises:
            ValueError: If profile not found, categories invalid, or too many source_content_ids.
        """
        # Get categories to use as topics (prefer request.categories, fall back to chosen_topics)
        selected_categories = request.categories or request.chosen_topics or []

        # Validate min categories
        config = await self._config_repo.get_config("onboarding_params") or {}
        min_topics = config.get("min_topics", 3)
        max_topics = config.get("max_topics", 10)

        if len(selected_categories) < min_topics:
            raise ValueError(
                f"At least {min_topics} categories must be selected, got {len(selected_categories)}"
            )

        # Validate source_content_ids length
        if request.source_content_ids is not None and len(request.source_content_ids) > 10:
            raise ValueError(
                f"At most 10 source_content_ids allowed, got {len(request.source_content_ids)}"
            )

        # Profile is guaranteed to exist by ensure_user_profile_exists dependency.
        # Validate categories against active category IDs
        if self._category_repo is not None:
            active_ids = await self._category_repo.get_category_ids()
            active_set = set(active_ids)
            for cat in selected_categories:
                if cat not in active_set:
                    raise ValueError(
                        f"Invalid category: '{cat}'. Must be one of the active categories."
                    )

        # Create warm profile using OnboardingService
        # Categories ARE topic keys -- no mapping needed
        profile = self._onboarding_service.create_profile(
            request.user_id,
            selected_categories,
            config,
        )

        # Handle optional embedding warmup from source_content_ids
        if request.source_content_ids and self._content_repo is not None:
            features_list = await self._content_repo.get_by_ids(request.source_content_ids)
            embeddings = [
                cf.embedding for cf in features_list
                if cf.embedding is not None
            ]
            if embeddings:
                # Average the embeddings to compute initial embedding
                dim = len(embeddings[0])
                avg_embedding = [
                    sum(emb[i] for emb in embeddings) / len(embeddings)
                    for i in range(dim)
                ]
                # L2-normalize
                norm = sum(v * v for v in avg_embedding) ** 0.5
                if norm > 0:
                    avg_embedding = [v / norm for v in avg_embedding]
                profile = profile.with_updated_embedding(avg_embedding)

        # Set cold_start=False after successful onboarding
        # Rebuild profile with cold_start=False
        from src.domain.entities.user_profile import UserProfile
        profile_no_cold_start = UserProfile(
            user_id=profile.user_id,
            topic_vector=profile.topic_vector,
            embedding=profile.embedding,
            sentiment_prefs=profile.sentiment_prefs,
            format_prefs=profile.format_prefs,
            interaction_count=profile.interaction_count,
            created_at=profile.created_at,
            last_updated=profile.last_updated,
            cold_start=False,
        )

        await self._profile_repo.save(profile_no_cold_start)

        # Publish recommendations.updated event (no debounce for onboarding)
        if self._event_publisher is not None:
            await self._event_publisher.publish_recommendations_updated(
                user_id=str(request.user_id),
                reason="onboarding_complete",
            )

        return OnboardResponse(
            user_id=request.user_id,
            profile_initialized=True,
        )
