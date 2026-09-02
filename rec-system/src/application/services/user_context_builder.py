"""UserContextBuilder — builds a natural-language user context string for reranking.

The context string is passed to TorchCrossEncoder as the "query" for
cross-encoder scoring against candidate documents.

Context is built from:
1. Top-3 topics from profile.topic_vector (sorted by weight descending)
2. Top-5 entities from entity_interests (sorted by weight descending)

Parts are joined with ". ". If no meaningful data is available, returns
the fallback string "Пользователь без профиля".
"""
from __future__ import annotations

from typing import Optional

from src.domain.entities.entity_interest import EntityInterest
from src.domain.entities.user_profile import UserProfile


class UserContextBuilder:
    """Builds a natural-language context string for cross-encoder reranking.

    Stateless — no dependencies, safe to instantiate per request.
    """

    _FALLBACK = "Пользователь без профиля"
    _TOP_TOPICS = 3
    _TOP_ENTITIES = 5

    async def build(
        self,
        profile: Optional[UserProfile],
        entity_interests: Optional[list[EntityInterest]] = None,
    ) -> str:
        """Build a context string representing the user's interests.

        Args:
            profile: User profile with topic vector. May be None for new users.
            entity_interests: List of entity interests. Optional.

        Returns:
            A string joining topic labels and entity names, or a fallback string.
        """
        if profile is None:
            return self._FALLBACK

        parts: list[str] = []

        # 1. Top-3 topics
        topic_weights = profile.topic_vector.weights
        top_topics = sorted(topic_weights.items(), key=lambda kv: kv[1], reverse=True)
        # Filter topics with non-trivial weight (avoid uniform fallback noise)
        meaningful_topics = [
            name for name, weight in top_topics[: self._TOP_TOPICS]
            if weight > 0.0
        ]
        if meaningful_topics:
            parts.append(". ".join(meaningful_topics))

        # 2. Top-5 entities
        interests = entity_interests or []
        top_entities = sorted(interests, key=lambda ei: ei.weight, reverse=True)
        entity_names = [ei.entity_name for ei in top_entities[: self._TOP_ENTITIES]]
        if entity_names:
            parts.append(". ".join(entity_names))

        if not parts:
            return self._FALLBACK

        return ". ".join(parts)
