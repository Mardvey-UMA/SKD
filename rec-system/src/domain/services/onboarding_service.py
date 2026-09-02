"""OnboardingService domain service."""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List
from uuid import UUID

from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


class OnboardingService:
    """Initializes a UserProfile from a list of chosen topics.

    Validates topic count (3-5) and creates the profile with correct defaults.
    Implements design/04-user-profile.md section 2.
    """

    def create_profile(
        self,
        user_id: UUID,
        chosen_topics: List[str],
        config: Dict[str, Any],
    ) -> UserProfile:
        """Create a new UserProfile from onboarding topic selection.

        Args:
            user_id: UUID of the user
            chosen_topics: List of selected topic names (3-5 items)
            config: Dict with baseline_weight, min_topics, max_topics

        Returns:
            A new UserProfile with initialized defaults

        Raises:
            ValueError: If topic count is outside allowed range or topic names are invalid
        """
        min_topics = config.get("min_topics", 3)
        max_topics = config.get("max_topics", 5)
        baseline_weight = config.get("baseline_weight", 0.01)

        # Validate topic count
        if len(chosen_topics) < min_topics:
            raise ValueError(
                f"At least {min_topics} topics must be selected, got {len(chosen_topics)}"
            )
        if len(chosen_topics) > max_topics:
            raise ValueError(
                f"At most {max_topics} topics can be selected, got {len(chosen_topics)}"
            )

        # Validate topic names
        valid_topics = set(TopicVector.TOPICS)
        for topic in chosen_topics:
            if topic not in valid_topics:
                raise ValueError(f"Invalid topic: '{topic}'. Must be one of {TopicVector.TOPICS}")

        # Build topic vector: selected topics equal share, rest get baseline_weight
        topic_vector = TopicVector.create_from_selected(
            selected_topics=chosen_topics,
            baseline_weight=baseline_weight,
        )

        # Uniform sentiment prefs
        sentiment_prefs = SentimentPrefs.create_uniform()

        # Default format prefs
        format_prefs = FormatPrefs.create_default()

        now = datetime.now(timezone.utc)
        return UserProfile(
            user_id=user_id,
            topic_vector=topic_vector,
            sentiment_prefs=sentiment_prefs,
            format_prefs=format_prefs,
            interaction_count=0,
            created_at=now,
            last_updated=now,
            embedding=None,
        )
