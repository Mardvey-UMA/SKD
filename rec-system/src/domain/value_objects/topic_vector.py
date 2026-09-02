from __future__ import annotations

from typing import Dict, List, Optional


class TopicVector:
    """Immutable value object representing a topic distribution (L1-normalized).

    By default uses the 18-topic hardcoded TOPICS list for backward compatibility.
    Accepts an optional ``topics`` parameter to use a dynamic list loaded from DB.
    """

    TOPICS: List[str] = [
        "политика",
        "экономика",
        "технологии",
        "наука",
        "спорт",
        "культура",
        "общество",
        "происшествия",
        "международные новости",
        "бизнес",
        "финансы",
        "образование",
        "здоровье",
        "развлечения",
        "криминал",
        "армия",
        "природа",
        "транспорт",
    ]

    def __init__(
        self,
        weights: Dict[str, float],
        topics: Optional[List[str]] = None,
    ) -> None:
        active_topics = topics if topics is not None else self.TOPICS
        # Clamp negatives, then L1-normalize
        clamped = {topic: max(0.0, weights.get(topic, 0.0)) for topic in active_topics}
        total = sum(clamped.values())
        if total == 0.0:
            # Uniform fallback if everything is zero
            normalized = {topic: 1.0 / len(active_topics) for topic in active_topics}
        else:
            normalized = {topic: v / total for topic, v in clamped.items()}
        # Immutable storage
        self._weights: Dict[str, float] = normalized

    @property
    def weights(self) -> Dict[str, float]:
        return dict(self._weights)

    def get(self, topic: str) -> float:
        return self._weights.get(topic, 0.0)

    @classmethod
    def create_uniform(cls, topics: Optional[List[str]] = None) -> "TopicVector":
        active_topics = topics if topics is not None else cls.TOPICS
        weights = {topic: 1.0 for topic in active_topics}
        return cls(weights, topics=active_topics)

    @classmethod
    def create_from_selected(
        cls,
        selected_topics: List[str],
        baseline_weight: float = 0.01,
        all_topics: Optional[List[str]] = None,
    ) -> "TopicVector":
        """Create vector from onboarding selection: selected topics equal share, rest get baseline.

        Args:
            selected_topics: Category IDs chosen by the user.
            baseline_weight: Weight assigned to non-selected topics.
            all_topics: Full list of topic keys. Defaults to TOPICS class constant.
        """
        active_topics = all_topics if all_topics is not None else cls.TOPICS
        weights = {topic: baseline_weight for topic in active_topics}
        # Equal share for selected topics (only valid ones)
        equal_share = 1.0  # will be normalized
        for topic in selected_topics:
            if topic in weights:
                weights[topic] = equal_share
        return cls(weights, topics=active_topics)

    def apply_update(
        self, lr: float, weight: float, topic_scores: Dict[str, float]
    ) -> "TopicVector":
        """Apply lr * weight * topic_score to each topic, return new normalized instance."""
        current_topics = list(self._weights.keys())
        new_weights = dict(self._weights)
        for topic, score in topic_scores.items():
            if topic in new_weights:
                new_weights[topic] += lr * weight * score
        return TopicVector(new_weights, topics=current_topics)

    def to_dict(self) -> Dict[str, float]:
        return dict(self._weights)
