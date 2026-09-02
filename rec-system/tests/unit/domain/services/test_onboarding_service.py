"""Tests for OnboardingService domain service."""
from __future__ import annotations

import pytest
from uuid import uuid4

from src.domain.services.onboarding_service import OnboardingService
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.topic_vector import TopicVector


DEFAULT_CONFIG = {
    "baseline_weight": 0.01,
    "min_topics": 3,
    "max_topics": 5,
}


class TestOnboardingService:
    @pytest.fixture
    def sut(self) -> OnboardingService:
        return OnboardingService()

    def test_create_profile_with_3_topics(self, sut: OnboardingService) -> None:
        """3 topics get equal share, rest get baseline"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        assert isinstance(profile, UserProfile)
        # Selected topics should have higher weight than unselected
        for t in topics:
            assert profile.topic_vector.get(t) > DEFAULT_CONFIG["baseline_weight"]
        # Unselected topics should have low weight
        unselected = [t for t in TopicVector.TOPICS if t not in topics]
        for t in unselected:
            assert profile.topic_vector.get(t) < profile.topic_vector.get("технологии")

    def test_create_profile_with_5_topics(self, sut: OnboardingService) -> None:
        """5 topics all get equal share"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика", "спорт", "культура"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        # All selected topics should have roughly equal weight
        weights = [profile.topic_vector.get(t) for t in topics]
        assert max(weights) == pytest.approx(min(weights), rel=0.01)

    def test_topic_vector_sums_to_one(self, sut: OnboardingService) -> None:
        """topic vector sums to 1.0 after normalization"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        total = sum(profile.topic_vector.weights.values())
        assert total == pytest.approx(1.0)

    def test_default_sentiment_prefs(self, sut: OnboardingService) -> None:
        """uniform sentiment prefs (1/3 each)"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        # All sentiments should be roughly equal (uniform)
        pos = profile.sentiment_prefs.get("POSITIVE")
        neg = profile.sentiment_prefs.get("NEGATIVE")
        neu = profile.sentiment_prefs.get("NEUTRAL")
        assert pos == pytest.approx(1.0 / 3, rel=0.01)
        assert neg == pytest.approx(1.0 / 3, rel=0.01)
        assert neu == pytest.approx(1.0 / 3, rel=0.01)

    def test_default_format_prefs(self, sut: OnboardingService) -> None:
        """format_prefs: avg_length=200, avg_complexity=0.5"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        assert profile.format_prefs.avg_length == pytest.approx(200.0)
        assert profile.format_prefs.avg_complexity == pytest.approx(0.5)

    def test_embedding_is_none(self, sut: OnboardingService) -> None:
        """new profile has no embedding"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        assert profile.embedding is None

    def test_interaction_count_zero(self, sut: OnboardingService) -> None:
        """new profile starts at interaction_count=0"""
        user_id = uuid4()
        topics = ["технологии", "наука", "экономика"]

        profile = sut.create_profile(user_id, topics, DEFAULT_CONFIG)

        assert profile.interaction_count == 0

    def test_validates_min_topics(self, sut: OnboardingService) -> None:
        """less than 3 topics raises ValueError"""
        user_id = uuid4()
        with pytest.raises(ValueError, match="3"):
            sut.create_profile(user_id, ["технологии", "наука"], DEFAULT_CONFIG)

    def test_validates_max_topics(self, sut: OnboardingService) -> None:
        """more than 5 topics raises ValueError"""
        user_id = uuid4()
        too_many = ["технологии", "наука", "экономика", "спорт", "культура", "политика"]
        with pytest.raises(ValueError, match="5"):
            sut.create_profile(user_id, too_many, DEFAULT_CONFIG)

    def test_validates_topic_names(self, sut: OnboardingService) -> None:
        """invalid topic name raises ValueError"""
        user_id = uuid4()
        with pytest.raises(ValueError):
            sut.create_profile(user_id, ["InvalidTopic", "наука", "экономика"], DEFAULT_CONFIG)
