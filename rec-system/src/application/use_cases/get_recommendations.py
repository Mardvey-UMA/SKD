"""GetRecommendationsUseCase application use case."""
from __future__ import annotations

import logging
import math
from datetime import datetime, timezone
from uuid import UUID

logger = logging.getLogger(__name__)

from src.application.dto.generate_feed import GenerateFeedRequest
from src.application.dto.recommendations import RecommendationsRequest, RecommendationsResponse
from src.application.use_cases.generate_feed import GenerateFeedUseCase
from src.domain.interfaces.recommendation_history_repository import RecommendationHistoryRepository
from src.domain.interfaces.user_profile_repository import UserProfileRepository


class UserProfileNotFoundError(Exception):
    """Raised when no recommendation profile exists for the given user_id."""

    def __init__(self, user_id: UUID) -> None:
        super().__init__(f"No recommendation profile exists for user {user_id}")
        self.user_id = user_id


class GetRecommendationsUseCase:
    """Returns personalized recommendations for a user.

    Wraps GenerateFeedUseCase, adds recommendation_history exclusion and write,
    and returns ordered UUIDs without scores.
    Implements POST /recommendations from design/08-api-contracts.md.
    """

    # Buffer factor to request extra candidates to account for history filtering.
    _BUFFER_FACTOR = 1.2

    def __init__(
        self,
        generate_feed_uc: GenerateFeedUseCase,
        history_repo: RecommendationHistoryRepository,
        profile_repo: UserProfileRepository,
    ) -> None:
        self._generate_feed_uc = generate_feed_uc
        self._history_repo = history_repo
        self._profile_repo = profile_repo

    async def execute(self, request: RecommendationsRequest) -> RecommendationsResponse:
        """Generate personalized recommendations with history exclusion.

        Args:
            request: RecommendationsRequest with user_id and count.

        Returns:
            RecommendationsResponse with ordered UUIDs, count, and generated_at.

        Raises:
            UserProfileNotFoundError: If no profile exists for user_id.
        """
        user_id: UUID = request.user_id
        count: int = request.count

        # Check profile exists -- raise 404 error if not
        profile = await self._profile_repo.get_by_user_id(user_id)
        if profile is None:
            raise UserProfileNotFoundError(user_id)

        # Load recommendation history for this user
        seen_ids = await self._history_repo.get_recommended_ids(user_id)

        # Request extra candidates to account for history filtering
        buffered_count = math.ceil(count * self._BUFFER_FACTOR) + len(seen_ids)

        # Call GenerateFeedUseCase — pass through the new Phase 1 source fields.
        feed_request = GenerateFeedRequest(
            user_id=user_id,
            feed_size=buffered_count,
            include_source_ids=request.include_source_ids,
            exclude_source_ids=request.exclude_source_ids,
            ranking_mode=request.ranking_mode,
            include_breakdown=request.include_breakdown,
        )
        feed_response = await self._generate_feed_uc.execute(feed_request)

        # Extract UUIDs from feed items, filter out history; track positional index for breakdown alignment
        all_items: list[UUID] = []
        all_items_detailed: list[dict] = []
        seen_in_response: set[UUID] = set()
        detailed_by_pub_id: dict[str, dict] = {}
        if feed_response.items_detailed:
            for detail in feed_response.items_detailed:
                detailed_by_pub_id[detail["content_id"]] = detail

        for item in feed_response.feed:
            try:
                pid = UUID(item["post_id"])
            except (KeyError, ValueError):
                continue
            if pid in seen_ids:
                continue
            if pid in seen_in_response:
                continue
            seen_in_response.add(pid)
            all_items.append(pid)
            if detailed_by_pub_id:
                detail = detailed_by_pub_id.get(item["post_id"])
                if detail is not None:
                    all_items_detailed.append(detail)

        # Trim to requested count
        result_items = all_items[:count]
        result_detailed = all_items_detailed[:count] if all_items_detailed else None

        # Graceful degradation: if history filter exhausted all candidates,
        # clear history and retry once so the user still sees content.
        if not result_items and seen_ids:
            logger.warning(
                "recommendation_history exhausted for user %s (%d seen), clearing history",
                user_id,
                len(seen_ids),
            )
            await self._history_repo.delete_for_user(user_id)

            # Retry without history filter
            feed_response = await self._generate_feed_uc.execute(feed_request)
            retry_items: list[UUID] = []
            retry_detailed: list[dict] = []
            retry_seen: set[UUID] = set()
            retry_detailed_by_id: dict[str, dict] = {}
            if feed_response.items_detailed:
                for detail in feed_response.items_detailed:
                    retry_detailed_by_id[detail["content_id"]] = detail
            for item in feed_response.feed:
                try:
                    pid = UUID(item["post_id"])
                except (KeyError, ValueError):
                    continue
                if pid in retry_seen:
                    continue
                retry_seen.add(pid)
                retry_items.append(pid)
                if retry_detailed_by_id:
                    detail = retry_detailed_by_id.get(item["post_id"])
                    if detail is not None:
                        retry_detailed.append(detail)
            result_items = retry_items[:count]
            result_detailed = retry_detailed[:count] if retry_detailed else None

        # NOTE: We do NOT save to recommendation_history here.
        # History is written when the user actually VIEWS posts
        # (via impression events in HandleInteractionsBatchUseCase).

        generated_at = datetime.now(timezone.utc)
        return RecommendationsResponse(
            user_id=user_id,
            items=result_items,
            count=len(result_items),
            generated_at=generated_at,
            items_detailed=result_detailed,
            latency_breakdown=feed_response.latency_breakdown,
            profile_snapshot=feed_response.profile_snapshot,
            feature_flags=feed_response.feature_flags,
        )
