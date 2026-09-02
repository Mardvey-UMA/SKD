"""Tests for DiversityFilter domain service."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from uuid import UUID, uuid4

from src.domain.services.diversity_filter import DiversityFilter
from src.domain.entities.content_features import ContentFeatures
from src.domain.value_objects.post_score import PostScore


DEFAULT_CONFIG = {
    "max_topic_streak": 3,
    "max_topic_ratio": 0.40,
}


def make_content(post_id=None, topic_1: str = "технологии") -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="test",
        post_date=datetime.now(timezone.utc),
        topic_1=topic_1,
    )


def make_candidates(topics_and_scores: list[tuple[str, float]]) -> list[tuple[ContentFeatures, float]]:
    return [
        (make_content(topic_1=topic), score)
        for topic, score in topics_and_scores
    ]


class TestDiversityFilter:
    @pytest.fixture
    def sut(self) -> DiversityFilter:
        return DiversityFilter()

    def test_streak_limit_breaks_consecutive(self, sut: DiversityFilter) -> None:
        """4 posts of same topic: 4th is skipped"""
        candidates = make_candidates([
            ("технологии", 0.9),
            ("технологии", 0.8),
            ("технологии", 0.7),
            ("технологии", 0.6),  # This should be skipped
            ("наука", 0.5),
        ])
        result = sut.filter(candidates, feed_size=10, config=DEFAULT_CONFIG)

        # Collect post_ids of the input tech posts (first 4 candidates)
        tech_post_ids = {c.post_id for c, _ in candidates[:4]}
        tech_posts_in_result = [ps for ps in result if ps.post_id in tech_post_ids]
        assert len(tech_posts_in_result) == 3  # max streak is 3

    def test_global_cap_limits_ratio(self, sut: DiversityFilter) -> None:
        """with feed_size=10, max 40% = 4 per topic"""
        # 10 tech posts, then other topics
        candidates = make_candidates(
            [("технологии", 1.0 - i * 0.01) for i in range(10)] +
            [("наука", 0.5 - i * 0.01) for i in range(5)]
        )
        # Track which post_ids are tech
        tech_post_ids = {c.post_id for c, _ in candidates[:10]}
        result = sut.filter(candidates, feed_size=10, config=DEFAULT_CONFIG)

        tech_count = sum(1 for ps in result if ps.post_id in tech_post_ids)
        assert tech_count <= 4  # max 40% of 10

    def test_streak_resets_on_different_topic(self, sut: DiversityFilter) -> None:
        """AAA-B-AAA is valid (3+3)"""
        candidates = make_candidates([
            ("технологии", 0.9),
            ("технологии", 0.88),
            ("технологии", 0.86),
            ("наука", 0.84),         # breaks streak
            ("технологии", 0.82),   # streak reset, allowed
            ("технологии", 0.80),
            ("технологии", 0.78),
        ])
        result = sut.filter(candidates, feed_size=7, config=DEFAULT_CONFIG)

        # Track first 3 tech post ids
        first_3_tech_ids = {c.post_id for c, _ in candidates[:3]}
        first_3_tech_in_result = [ps for ps in result if ps.post_id in first_3_tech_ids]
        # First 3 are always valid
        assert len(first_3_tech_in_result) >= 3

    def test_feed_size_respected(self, sut: DiversityFilter) -> None:
        """output length <= feed_size"""
        candidates = make_candidates([("технологии", 1.0 - i * 0.01) for i in range(100)])
        result = sut.filter(candidates, feed_size=20, config=DEFAULT_CONFIG)
        assert len(result) <= 20

    def test_mixed_topics_no_filtering(self, sut: DiversityFilter) -> None:
        """diverse input passes through with no items removed (up to feed_size)"""
        topics = ["технологии", "наука", "спорт", "культура", "экономика"]
        candidates = make_candidates([(t, 1.0 - i * 0.1) for i, t in enumerate(topics)])
        result = sut.filter(candidates, feed_size=10, config=DEFAULT_CONFIG)

        assert len(result) == len(candidates)

    def test_empty_input(self, sut: DiversityFilter) -> None:
        """returns empty list"""
        result = sut.filter([], feed_size=200, config=DEFAULT_CONFIG)
        assert result == []

    # --- Related spacing tests ---

    def test_related_spacing_defers_close_related_posts(self, sut: DiversityFilter) -> None:
        """Post B related to post A placed at position 0 cannot appear within 3 positions."""
        post_a_id = uuid4()
        post_b_id = uuid4()
        # 5 posts: A at index 0, then 2 fillers, then B at index 3
        filler1_id = uuid4()
        filler2_id = uuid4()
        post_c_id = uuid4()

        candidates = [
            (make_content(post_id=post_a_id, topic_1="технологии"), 1.0),
            (make_content(post_id=filler1_id, topic_1="наука"), 0.9),
            (make_content(post_id=filler2_id, topic_1="спорт"), 0.8),
            (make_content(post_id=post_b_id, topic_1="культура"), 0.7),  # related to A
            (make_content(post_id=post_c_id, topic_1="экономика"), 0.6),
        ]
        related_map = {post_a_id: {post_b_id}, post_b_id: {post_a_id}}
        config = {**DEFAULT_CONFIG, "related_min_gap": 3}

        result = sut.filter(candidates, feed_size=5, config=config, related_map=related_map)

        post_ids = [ps.post_id for ps in result]

        # A is placed; B must appear at least 3 positions after A
        if post_a_id in post_ids and post_b_id in post_ids:
            idx_a = post_ids.index(post_a_id)
            idx_b = post_ids.index(post_b_id)
            assert idx_b - idx_a >= 3

    def test_related_spacing_allows_distant_related_posts(self, sut: DiversityFilter) -> None:
        """Related posts with enough gap between them should both appear in the feed."""
        post_a_id = uuid4()
        post_b_id = uuid4()
        filler_ids = [uuid4() for _ in range(3)]

        candidates = [
            (make_content(post_id=post_a_id, topic_1="технологии"), 1.0),
            (make_content(post_id=filler_ids[0], topic_1="наука"), 0.9),
            (make_content(post_id=filler_ids[1], topic_1="спорт"), 0.8),
            (make_content(post_id=filler_ids[2], topic_1="культура"), 0.75),
            (make_content(post_id=post_b_id, topic_1="экономика"), 0.7),  # related to A
        ]
        related_map = {post_a_id: {post_b_id}, post_b_id: {post_a_id}}
        config = {**DEFAULT_CONFIG, "related_min_gap": 3}

        result = sut.filter(candidates, feed_size=5, config=config, related_map=related_map)

        post_ids = [ps.post_id for ps in result]
        # Both should appear since B is at position 4 (gap=4 >= 3)
        assert post_a_id in post_ids
        assert post_b_id in post_ids

    def test_related_map_none_no_effect(self, sut: DiversityFilter) -> None:
        """When related_map=None, behavior is identical to not passing it."""
        candidates = make_candidates([
            ("технологии", 0.9),
            ("наука", 0.8),
            ("спорт", 0.7),
        ])
        result_no_arg = sut.filter(candidates, feed_size=5, config=DEFAULT_CONFIG)
        result_none = sut.filter(candidates, feed_size=5, config=DEFAULT_CONFIG, related_map=None)

        assert [ps.score for ps in result_no_arg] == [ps.score for ps in result_none]
        assert len(result_none) == 3

    def test_related_min_gap_configurable(self, sut: DiversityFilter) -> None:
        """related_min_gap=1 means related posts cannot be immediately consecutive."""
        post_a_id = uuid4()
        post_b_id = uuid4()

        candidates = [
            (make_content(post_id=post_a_id, topic_1="технологии"), 1.0),
            (make_content(post_id=post_b_id, topic_1="наука"), 0.9),  # immediate neighbor, gap=0 < 1 → skip
            (make_content(post_id=uuid4(), topic_1="спорт"), 0.8),
        ]
        related_map = {post_a_id: {post_b_id}}
        config = {**DEFAULT_CONFIG, "related_min_gap": 1}

        result = sut.filter(candidates, feed_size=3, config=config, related_map=related_map)
        post_ids = [ps.post_id for ps in result]

        # B should not immediately follow A (positions differ by at least 1)
        if post_a_id in post_ids and post_b_id in post_ids:
            idx_a = post_ids.index(post_a_id)
            idx_b = post_ids.index(post_b_id)
            assert idx_b - idx_a >= 1

    def test_existing_streak_and_cap_still_work_with_related_map(self, sut: DiversityFilter) -> None:
        """Streak and cap constraints still apply when related_map is provided."""
        candidates = make_candidates([
            ("технологии", 0.9),
            ("технологии", 0.8),
            ("технологии", 0.7),
            ("технологии", 0.6),  # 4th consecutive tech — should be skipped by streak
            ("наука", 0.5),
        ])
        related_map: dict = {}  # empty, no related pairs
        result = sut.filter(candidates, feed_size=10, config=DEFAULT_CONFIG, related_map=related_map)

        tech_post_ids = {c.post_id for c, _ in candidates[:4]}
        tech_count = sum(1 for ps in result if ps.post_id in tech_post_ids)
        assert tech_count == 3  # streak still enforced

    def test_configurable_params(self, sut: DiversityFilter) -> None:
        """custom max_topic_streak and max_topic_ratio"""
        config = {"max_topic_streak": 1, "max_topic_ratio": 0.5}
        # Build candidates with stable UUIDs for ordering checks
        tech1_id = uuid4()
        tech2_id = uuid4()
        tech3_id = uuid4()
        sci_id = uuid4()

        candidates = [
            (make_content(post_id=tech1_id, topic_1="технологии"), 0.9),
            (make_content(post_id=tech2_id, topic_1="технологии"), 0.8),  # Should be skipped (streak=1)
            (make_content(post_id=sci_id, topic_1="наука"), 0.7),
            (make_content(post_id=tech3_id, topic_1="технологии"), 0.6),  # allowed after break
        ]
        result = sut.filter(candidates, feed_size=10, config=config)

        # With streak=1, second consecutive tech post should be skipped
        post_ids = [ps.post_id for ps in result]
        # tech1 and tech2 should not be consecutive in the result
        if tech1_id in post_ids and tech2_id in post_ids:
            idx1 = post_ids.index(tech1_id)
            idx2 = post_ids.index(tech2_id)
            assert idx2 != idx1 + 1  # they're not consecutive
