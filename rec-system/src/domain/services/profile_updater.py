"""ProfileUpdater domain service."""
from __future__ import annotations

import math
from datetime import datetime, timezone
from typing import Dict, Any, List, Tuple
from uuid import UUID

from src.domain.entities.user_profile import UserProfile
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.entity_interest import EntityInterest
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


class ProfileUpdater:
    """Applies a batch of signals to a UserProfile.

    Implements EMA update formulas from design/04-user-profile.md section 3.
    All updates are immutable — returns a new UserProfile instance.
    """

    def apply_batch(
        self,
        profile: UserProfile,
        signals: List[Signal],
        content_features_map: Dict[UUID, ContentFeatures],
        config: Dict[str, Any],
    ) -> Tuple[UserProfile, List[EntityInterest]]:
        """Apply all signals to the profile. Returns (updated_profile, entity_changes)."""
        lr = config.get("learning_rate", 0.08)
        entity_min_weight = config.get("entity_min_weight", 0.4)
        entity_max_per_post = config.get("entity_max_per_post", 5)

        current = profile
        all_entity_changes: List[EntityInterest] = []

        for signal in signals:
            content = content_features_map.get(signal.post_id)
            if content is None:
                continue

            # Update topic vector
            topic_scores = content.topic_dict()
            new_topic_vector = current.topic_vector.apply_update(lr, signal.weight, topic_scores)
            current = current.with_updated_topic_vector(new_topic_vector)

            # Update embedding (positive signals only)
            if signal.is_positive and content.embedding is not None:
                new_embedding = self._update_embedding(current.embedding, content.embedding, lr, signal.weight)
                current = current.with_updated_embedding(new_embedding)

            # Update sentiment prefs
            if content.sentiment is not None:
                new_sentiment = current.sentiment_prefs.apply_update(content.sentiment, lr, signal.weight)
                current = current.with_updated_sentiment_prefs(new_sentiment)

            # Update format prefs (positive signals only)
            if signal.is_positive and content.word_count is not None and content.complexity is not None:
                new_format = current.format_prefs.apply_ema_update(
                    lr, float(content.word_count), content.complexity
                )
                current = current.with_updated_format_prefs(new_format)

            # Update entity interests (strong signals only)
            if signal.weight >= entity_min_weight:
                entities = content.all_entities()[:entity_max_per_post]
                for entity_name in entities:
                    entity_change = EntityInterest(
                        user_id=signal.user_id,
                        entity_type="person",
                        entity_name=entity_name,
                        weight=1.0,
                        last_seen=datetime.utcnow(),
                    )
                    all_entity_changes.append(entity_change)

        # Increment interaction count by batch size
        updated = UserProfile(
            user_id=current.user_id,
            topic_vector=current.topic_vector,
            embedding=current.embedding,
            sentiment_prefs=current.sentiment_prefs,
            format_prefs=current.format_prefs,
            interaction_count=current.interaction_count + len(signals),
            created_at=current.created_at,
            last_updated=current.last_updated,
        )

        return updated, all_entity_changes

    def _update_embedding(
        self,
        profile_embedding: list | None,
        post_embedding: list,
        lr: float,
        weight: float,
    ) -> list:
        """Update embedding: first positive = copy, subsequent = EMA blend + L2-normalize."""
        if profile_embedding is None:
            # First positive interaction: copy post embedding
            return list(post_embedding)

        # EMA: alpha = lr * abs(weight)
        alpha = lr * abs(weight)
        blended = [
            (1 - alpha) * p + alpha * e
            for p, e in zip(profile_embedding, post_embedding)
        ]
        return self._l2_normalize(blended)

    @staticmethod
    def _l2_normalize(vec: list) -> list:
        """L2-normalize a vector using stdlib math."""
        norm = math.sqrt(sum(x * x for x in vec))
        if norm == 0.0:
            return vec
        return [x / norm for x in vec]
