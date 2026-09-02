"""DiversityFilter domain service."""
from __future__ import annotations

from typing import Dict, Any, List, Tuple, Set
from uuid import UUID

from src.domain.entities.content_features import ContentFeatures
from src.domain.value_objects.post_score import PostScore


class DiversityFilter:
    """Applies streak cap, global cap, and related spacing constraints to a sorted list of scored candidates.

    Prevents topic monotony and content repetition in the feed.
    Implements design/05-scoring-ranking.md section 4.
    """

    def filter(
        self,
        candidates: List[Tuple[ContentFeatures, float]],
        feed_size: int,
        config: Dict[str, Any],
        related_map: Dict[UUID, Set[UUID]] | None = None,
    ) -> List[PostScore]:
        """Filter candidates respecting streak and global cap constraints.

        Args:
            candidates: Pre-sorted list of (ContentFeatures, score) pairs (DESC by score)
            feed_size: Maximum number of posts in the result
            config: Dict with max_topic_streak, max_topic_ratio, and related_min_gap
            related_map: Optional mapping of post_id -> set of related post_ids.
                         Related posts will be spaced at least related_min_gap positions apart.

        Returns:
            List of PostScore objects respecting all constraints
        """
        max_streak = config.get("max_topic_streak", 3)
        max_ratio = config.get("max_topic_ratio", 0.40)
        related_min_gap = config.get("related_min_gap", 3)
        max_per_topic = feed_size * max_ratio

        feed: List[PostScore] = []
        topic_streak: Dict[str, int] = {}
        topic_count: Dict[str, int] = {}
        last_topic: str | None = None
        position_of: Dict[UUID, int] = {}

        for content, score in candidates:
            topic = content.topic_1 or ""

            # Initialize counts
            if topic not in topic_streak:
                topic_streak[topic] = 0
            if topic not in topic_count:
                topic_count[topic] = 0

            # Check streak constraint
            if topic_streak[topic] >= max_streak:
                continue

            # Check global cap constraint
            if topic_count[topic] >= max_per_topic:
                continue

            # Check related spacing constraint
            if related_map:
                related_to = related_map.get(content.post_id, set())
                if any(
                    (len(feed) - pos) < related_min_gap
                    for rel_id in related_to
                    for pos in [position_of.get(rel_id)]
                    if pos is not None
                ):
                    continue

            # Accept this post
            position_of[content.post_id] = len(feed)
            feed.append(PostScore(post_id=content.post_id, score=score))
            topic_count[topic] = topic_count.get(topic, 0) + 1

            # Update streak: increment for current topic, reset all others
            if topic == last_topic:
                topic_streak[topic] += 1
            else:
                topic_streak[topic] = 1
                for other in topic_streak:
                    if other != topic:
                        topic_streak[other] = 0

            last_topic = topic

            if len(feed) >= feed_size:
                break

        return feed
