"""HandleInteractionsBatchUseCase application use case."""
from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID, uuid4

from src.application.dto.kafka_events import InteractionsBatchEvent, InteractionItem
from src.domain.entities.user_interaction import UserInteraction
from src.domain.entities.user_profile import UserProfile
from src.domain.interfaces.cache_client import CacheClient
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.domain.interfaces.event_publisher import EventPublisher
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.interfaces.recommendation_history_repository import RecommendationHistoryRepository
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.signal_classifier import SignalClassifier

import logging

logger = logging.getLogger(__name__)
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector

DEBOUNCE_KEY_PREFIX = "rec:invalidation:last:"
DEBOUNCE_TTL = 900  # 15 minutes

# Canonical action types that trigger recommendations.updated publish (per design).
# Backend emits UPPERCASE; matching is performed against the mapped event_type.
STRONG_ACTION_TYPES = frozenset({"LIKE", "DISLIKE", "BOOKMARK"})

# Map from Kafka action_type -> UserInteraction event_type for SignalClassifier.
# Keys are normalized via .upper() at lookup time. Aligns with backend
# ActionType.fromString(): canonical (identity) + legacy aliases.
_ACTION_TYPE_MAP = {
    # canonical (identity)
    "IMPRESSION": "IMPRESSION",
    "OPEN": "OPEN",
    "CLOSE": "CLOSE",
    "LIKE": "LIKE",
    "DISLIKE": "DISLIKE",
    "BOOKMARK": "BOOKMARK",
    # legacy aliases — preserved for backward compat with older messages
    "VIEW": "IMPRESSION",
    "CLICK": "OPEN",
    "SCROLL_PAST": "CLOSE",
    "SAVE": "BOOKMARK",
    "HIDE": "DISLIKE",
    "SHARE": "BOOKMARK",
}


def _map_event_type(action_type: str) -> str | None:
    """Normalize action_type and map to canonical event_type. None = unknown (silent drop)."""
    return _ACTION_TYPE_MAP.get(action_type.upper())


def _make_fake_interaction(user_id: UUID, item: InteractionItem) -> UserInteraction | None:
    """Convert an InteractionItem (Kafka DTO) to UserInteraction domain entity.

    Returns None when action_type is unknown (caller must filter).
    """
    event_type = _map_event_type(item.action_type)
    if event_type is None:
        return None
    duration_ms = int(item.duration_sec * 1000) if item.duration_sec is not None else None
    return UserInteraction(
        id=0,
        event_id=uuid4(),
        user_id=str(user_id),
        post_id=item.content_id,
        event_type=event_type,
        duration_ms=duration_ms,
        scroll_pct=None,
        max_scroll_pct=None,
        created_at=item.timestamp,
        processed=False,
    )


class HandleInteractionsBatchUseCase:
    """Processes user.interactions.batch Kafka events.

    Updates user profiles via EMA, then optionally publishes recommendations.updated
    with Redis-based debounce (max once per 15 minutes per user).
    Implements design.md Sequence 4.
    """

    # Minimum impression duration (ms) to count as "viewed" for history
    _IMPRESSION_THRESHOLD_MS = 2000

    def __init__(
        self,
        profile_repo: UserProfileRepository,
        content_repo: ContentRepository,
        signal_classifier: SignalClassifier,
        profile_updater: ProfileUpdater,
        event_publisher: EventPublisher,
        cache_client: CacheClient,
        entity_interest_repo: EntityInterestRepository,
        interaction_repo: InteractionRepository,
        history_repo: RecommendationHistoryRepository | None = None,
    ) -> None:
        self._profile_repo = profile_repo
        self._content_repo = content_repo
        self._signal_classifier = signal_classifier
        self._profile_updater = profile_updater
        self._event_publisher = event_publisher
        self._cache_client = cache_client
        self._entity_interest_repo = entity_interest_repo
        self._interaction_repo = interaction_repo
        self._history_repo = history_repo

    async def execute(self, event: InteractionsBatchEvent) -> None:
        """Process all interactions in the batch and update user profile.

        Args:
            event: InteractionsBatchEvent with user_id and interactions list.
        """
        user_id: UUID = event.user_id

        # Load profile -- create empty one defensively if not found
        profile = await self._profile_repo.get_by_user_id(user_id)
        if profile is None:
            profile = self._create_empty_profile(user_id)
            await self._profile_repo.save(profile)

        # Adapt Kafka items to domain UserInteraction entities.
        # Pair each mapped event_type with its source item for downstream logic.
        # Unknown action_types are silently dropped per canonical contract.
        mapped_pairs: list[tuple[InteractionItem, str, UserInteraction]] = []
        for item in event.interactions:
            event_type = _map_event_type(item.action_type)
            if event_type is None:
                logger.debug("Skipping unknown action_type: %s", item.action_type)
                continue
            ui = _make_fake_interaction(user_id, item)
            if ui is None:
                continue
            mapped_pairs.append((item, event_type, ui))

        interactions = [ui for _, _, ui in mapped_pairs]

        # Classify signals
        signals = self._signal_classifier.classify_batch(interactions)

        # Load content features for all posts referenced in interactions
        post_ids = list({item.content_id for item, _, _ in mapped_pairs})
        features_list = await self._content_repo.get_by_ids(post_ids)
        content_features_map = {cf.post_id: cf for cf in features_list}

        # Apply profile update via EMA
        updated_profile, entity_changes = self._profile_updater.apply_batch(
            profile=profile,
            signals=signals,
            content_features_map=content_features_map,
            config={},
        )

        # Persist updated profile
        await self._profile_repo.save(updated_profile)

        # Upsert entity interests
        for entity_interest in entity_changes:
            await self._entity_interest_repo.upsert(entity_interest)

        # Apply entity decay and cleanup
        await self._entity_interest_repo.decay_all_for_user(user_id, 0.9)
        await self._entity_interest_repo.cleanup_expired(user_id, 30)

        # Save interactions to user_interactions for audit/history.
        # Mark as processed=True because EMA was already applied above.
        # This prevents the background UpdateProfileUseCase job from
        # processing them again (double EMA update bug).
        for inter in interactions:
            inter.processed = True
        await self._interaction_repo.save_batch(interactions)

        # Write VIEWED posts to recommendation_history.
        # A post is "viewed" if:
        # - mapped event_type is a strong signal (LIKE/DISLIKE/BOOKMARK), OR
        # - it's an IMPRESSION with duration >= 2000ms (user actually saw it)
        if self._history_repo is not None:
            viewed_content_ids: list[UUID] = []
            for item, event_type, _ in mapped_pairs:
                if event_type in STRONG_ACTION_TYPES:
                    viewed_content_ids.append(item.content_id)
                elif event_type == "IMPRESSION":
                    duration_ms = int(item.duration_sec * 1000) if item.duration_sec is not None else 0
                    if duration_ms >= self._IMPRESSION_THRESHOLD_MS:
                        viewed_content_ids.append(item.content_id)
            if viewed_content_ids:
                await self._history_repo.save_recommendations(
                    user_id=user_id,
                    content_ids=viewed_content_ids,
                )
                logger.info(
                    "Saved %d viewed posts to recommendation_history for user %s",
                    len(viewed_content_ids),
                    user_id,
                )

        # Check for strong signals (compare mapped event_types, not raw action_types)
        mapped_event_types = {event_type for _, event_type, _ in mapped_pairs}
        has_strong_signals = bool(mapped_event_types & STRONG_ACTION_TYPES)

        if has_strong_signals:
            await self._maybe_publish_recommendations_updated(user_id)

    async def _maybe_publish_recommendations_updated(self, user_id: UUID) -> None:
        """Publish recommendations.updated with Redis-based debounce.

        At most once per 15 minutes per user.
        """
        debounce_key = f"{DEBOUNCE_KEY_PREFIX}{user_id}"
        existing = await self._cache_client.get(debounce_key)
        if existing is not None:
            # Within debounce window -- skip publish
            return

        # Publish the event
        await self._event_publisher.publish_recommendations_updated(
            user_id=str(user_id),
            reason="interactions_processed",
        )

        # Set debounce key with TTL
        await self._cache_client.set(debounce_key, "1", ttl=DEBOUNCE_TTL)

    def _create_empty_profile(self, user_id: UUID) -> UserProfile:
        """Create an empty profile with zero vector (defensive creation)."""
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
