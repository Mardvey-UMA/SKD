"""Tests for ProfileUpdater domain service."""
from __future__ import annotations

import math
import pytest
from datetime import datetime, timezone
from uuid import uuid4

from src.domain.services.profile_updater import ProfileUpdater
from src.domain.entities.user_profile import UserProfile
from src.domain.entities.content_features import ContentFeatures
from src.domain.value_objects.signal import Signal
from src.domain.value_objects.event_type import EventType
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


DEFAULT_CONFIG = {
    "learning_rate": 0.08,
    "entity_min_weight": 0.4,
    "entity_max_per_post": 5,
}


def make_profile(
    embedding=None,
    topic_weights=None,
    sentiment_weights=None,
    format_prefs=None,
    interaction_count=0,
) -> UserProfile:
    if topic_weights is None:
        topic_weights = {t: 1.0 / 18 for t in TopicVector.TOPICS}
    if sentiment_weights is None:
        sentiment_weights = {"POSITIVE": 0.33, "NEGATIVE": 0.33, "NEUTRAL": 0.34}
    if format_prefs is None:
        format_prefs = FormatPrefs.create_default()
    return UserProfile(
        user_id=uuid4(),
        topic_vector=TopicVector(topic_weights),
        sentiment_prefs=SentimentPrefs(sentiment_weights),
        format_prefs=format_prefs,
        interaction_count=interaction_count,
        created_at=datetime.now(timezone.utc),
        last_updated=datetime.now(timezone.utc),
        embedding=embedding,
    )


def make_signal(weight: float, post_id=None) -> Signal:
    return Signal(post_id=post_id if post_id is not None else uuid4(), user_id="user1", weight=weight, event_type=EventType.LIKE)


def make_content(
    post_id=None,
    topic_1: str = "технологии",
    topic_1_score: float = 0.8,
    sentiment: str = "POSITIVE",
    word_count: int = 300,
    complexity: float = 0.5,
    embedding=None,
    entities_persons=None,
    entities_organizations=None,
) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="test content",
        post_date=datetime.now(timezone.utc),
        topic_1=topic_1,
        topic_1_score=topic_1_score,
        sentiment=sentiment,
        word_count=word_count,
        complexity=complexity,
        embedding=embedding,
        entities_persons=entities_persons or [],
        entities_organizations=entities_organizations or [],
    )


class TestProfileUpdater:
    @pytest.fixture
    def sut(self) -> ProfileUpdater:
        return ProfileUpdater()

    def test_update_topic_vector_positive_signal(self, sut: ProfileUpdater) -> None:
        """topic weight increases on positive signal"""
        pid = uuid4()
        profile = make_profile()
        signal = make_signal(weight=0.6, post_id=pid)
        content = make_content(post_id=pid, topic_1="технологии", topic_1_score=0.8)
        initial_weight = profile.topic_vector.get("технологии")

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.topic_vector.get("технологии") > initial_weight

    def test_update_topic_vector_negative_signal(self, sut: ProfileUpdater) -> None:
        """topic weight decreases on dislike"""
        pid = uuid4()
        weights = {t: 0.1 for t in TopicVector.TOPICS}
        weights["технологии"] = 0.5
        profile = make_profile(topic_weights=weights)
        signal = Signal(post_id=pid, user_id="user1", weight=-0.7, event_type=EventType.DISLIKE)
        content = make_content(post_id=pid, topic_1="технологии", topic_1_score=0.8)
        initial_weight = profile.topic_vector.get("технологии")

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.topic_vector.get("технологии") < initial_weight

    def test_update_topic_vector_normalization(self, sut: ProfileUpdater) -> None:
        """topic vector sums to ~1.0 after update"""
        pid = uuid4()
        profile = make_profile()
        signal = make_signal(weight=0.6, post_id=pid)
        content = make_content(post_id=pid)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        total = sum(updated.topic_vector.weights.values())
        assert total == pytest.approx(1.0, abs=1e-9)

    def test_update_embedding_first_positive(self, sut: ProfileUpdater) -> None:
        """null embedding -> copy post embedding"""
        pid = uuid4()
        profile = make_profile(embedding=None)
        signal = make_signal(weight=0.6, post_id=pid)
        post_embedding = [0.1, 0.2, 0.3, 0.4]
        content = make_content(post_id=pid, embedding=post_embedding)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.embedding is not None
        assert len(updated.embedding) == len(post_embedding)

    def test_update_embedding_ema(self, sut: ProfileUpdater) -> None:
        """existing embedding: alpha = lr * abs(weight), EMA blend"""
        pid = uuid4()
        initial_embedding = [1.0, 0.0, 0.0]
        norm = math.sqrt(sum(x**2 for x in initial_embedding))
        initial_embedding = [x / norm for x in initial_embedding]
        profile = make_profile(embedding=initial_embedding)
        signal = make_signal(weight=0.5, post_id=pid)
        post_embedding = [0.0, 1.0, 0.0]
        content = make_content(post_id=pid, embedding=post_embedding)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.embedding is not None
        # After EMA blend, the embedding should have moved toward post embedding
        assert updated.embedding[1] > 0.0  # y component should be > 0

    def test_embedding_not_updated_on_negative(self, sut: ProfileUpdater) -> None:
        """dislike does NOT update embedding"""
        pid = uuid4()
        initial_embedding = [1.0, 0.0, 0.0]
        profile = make_profile(embedding=initial_embedding)
        signal = Signal(post_id=pid, user_id="user1", weight=-0.7, event_type=EventType.DISLIKE)
        post_embedding = [0.0, 1.0, 0.0]
        content = make_content(post_id=pid, embedding=post_embedding)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.embedding == initial_embedding

    def test_embedding_l2_normalized(self, sut: ProfileUpdater) -> None:
        """result embedding is L2-normalized"""
        pid = uuid4()
        initial_embedding = [1.0, 0.0, 0.0]
        profile = make_profile(embedding=initial_embedding)
        signal = make_signal(weight=0.5, post_id=pid)
        post_embedding = [0.5, 0.5, 0.7071]
        content = make_content(post_id=pid, embedding=post_embedding)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.embedding is not None
        norm = math.sqrt(sum(x**2 for x in updated.embedding))
        assert norm == pytest.approx(1.0, abs=1e-6)

    def test_update_sentiment_prefs(self, sut: ProfileUpdater) -> None:
        """sentiment += lr * weight * 0.5 (then normalized)"""
        pid = uuid4()
        profile = make_profile()
        signal = make_signal(weight=0.6, post_id=pid)
        content = make_content(post_id=pid, sentiment="POSITIVE")
        initial_pos = profile.sentiment_prefs.get("POSITIVE")

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.sentiment_prefs.get("POSITIVE") > initial_pos

    def test_update_format_prefs_positive_only(self, sut: ProfileUpdater) -> None:
        """format prefs updated on positive signals, skipped on negative"""
        pid = uuid4()
        profile = make_profile()
        initial_avg_length = profile.format_prefs.avg_length
        signal = Signal(post_id=pid, user_id="user1", weight=-0.7, event_type=EventType.DISLIKE)
        content = make_content(post_id=pid, word_count=1000, complexity=0.9)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert updated.format_prefs.avg_length == pytest.approx(initial_avg_length)

    def test_update_format_prefs_ema(self, sut: ProfileUpdater) -> None:
        """format_prefs: new = (1-lr)*old + lr*post_value"""
        pid = uuid4()
        lr = 0.08
        profile = make_profile(format_prefs=FormatPrefs(avg_length=200.0, avg_complexity=0.5))
        signal = make_signal(weight=0.6, post_id=pid)
        post_word_count = 400
        post_complexity = 0.8
        content = make_content(post_id=pid, word_count=post_word_count, complexity=post_complexity)

        updated, _ = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        expected_length = (1 - lr) * 200.0 + lr * post_word_count
        expected_complexity = (1 - lr) * 0.5 + lr * post_complexity
        assert updated.format_prefs.avg_length == pytest.approx(expected_length)
        assert updated.format_prefs.avg_complexity == pytest.approx(expected_complexity)

    def test_entity_interests_updated_on_strong_signal(self, sut: ProfileUpdater) -> None:
        """weight >= 0.4 triggers entity upsert"""
        pid = uuid4()
        profile = make_profile()
        signal = make_signal(weight=0.6, post_id=pid)
        content = make_content(post_id=pid, entities_persons=["Илон Маск", "Билл Гейтс"])

        _, entity_changes = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert len(entity_changes) > 0
        entity_names = [e.entity_name for e in entity_changes]
        assert "Илон Маск" in entity_names

    def test_entity_interests_not_updated_on_weak_signal(self, sut: ProfileUpdater) -> None:
        """weight < 0.4 skipped for entities"""
        pid = uuid4()
        profile = make_profile()
        signal = Signal(post_id=pid, user_id="user1", weight=0.15, event_type=EventType.IMPRESSION)
        content = make_content(post_id=pid, entities_persons=["Илон Маск"])

        _, entity_changes = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert len(entity_changes) == 0

    def test_entity_interests_max_per_post(self, sut: ProfileUpdater) -> None:
        """only first 5 entities per post are processed"""
        pid = uuid4()
        profile = make_profile()
        signal = make_signal(weight=0.8, post_id=pid)
        entities = ["Entity1", "Entity2", "Entity3", "Entity4", "Entity5", "Entity6", "Entity7"]
        content = make_content(post_id=pid, entities_persons=entities)

        _, entity_changes = sut.apply_batch(profile, [signal], {pid: content}, DEFAULT_CONFIG)

        assert len(entity_changes) <= 5

    def test_apply_batch(self, sut: ProfileUpdater) -> None:
        """apply multiple signals, returns updated profile + entity changes"""
        pid1 = uuid4()
        pid2 = uuid4()
        profile = make_profile()
        signals = [
            Signal(post_id=pid1, user_id="user1", weight=0.6, event_type=EventType.LIKE),
            Signal(post_id=pid2, user_id="user1", weight=0.8, event_type=EventType.BOOKMARK),
        ]
        content_map = {
            pid1: make_content(post_id=pid1, topic_1="технологии"),
            pid2: make_content(post_id=pid2, topic_1="наука"),
        }

        updated_profile, entity_changes = sut.apply_batch(profile, signals, content_map, DEFAULT_CONFIG)

        assert isinstance(updated_profile, UserProfile)
        assert isinstance(entity_changes, list)

    def test_interaction_count_incremented(self, sut: ProfileUpdater) -> None:
        """interaction_count += batch size"""
        pids = [uuid4() for _ in range(3)]
        profile = make_profile(interaction_count=10)
        signals = [
            make_signal(weight=0.6, post_id=pids[0]),
            make_signal(weight=0.8, post_id=pids[1]),
            make_signal(weight=0.15, post_id=pids[2]),
        ]
        content_map = {pid: make_content(post_id=pid) for pid in pids}

        updated, _ = sut.apply_batch(profile, signals, content_map, DEFAULT_CONFIG)

        assert updated.interaction_count == 13
