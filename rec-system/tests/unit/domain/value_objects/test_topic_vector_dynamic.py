"""Tests for dynamic TopicVector topic list."""
from __future__ import annotations

import pytest


CUSTOM_TOPICS = ["технологии", "наука", "спорт", "культура"]
ALL_18_TOPICS = [
    "политика", "экономика", "технологии", "наука", "спорт", "культура",
    "общество", "происшествия", "международные новости", "бизнес", "финансы",
    "образование", "здоровье", "развлечения", "криминал", "армия", "природа",
    "транспорт",
]


class TestTopicVectorDynamic:
    def test_create_with_custom_topic_list(self):
        """TopicVector can be created with a custom topic list."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector(
            weights={"технологии": 0.5, "наука": 0.3, "спорт": 0.2},
            topics=CUSTOM_TOPICS,
        )
        assert len(tv.weights) == len(CUSTOM_TOPICS)
        assert "технологии" in tv.weights

    def test_custom_topics_l1_normalized(self):
        """L1 normalization works for custom topic lists."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector(
            weights={"технологии": 1.0, "наука": 1.0, "спорт": 1.0, "культура": 1.0},
            topics=CUSTOM_TOPICS,
        )
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6

    def test_create_uniform_with_custom_topics(self):
        """create_uniform can take an explicit topic list."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector.create_uniform(topics=CUSTOM_TOPICS)
        assert len(tv.weights) == len(CUSTOM_TOPICS)
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6
        for topic in CUSTOM_TOPICS:
            assert abs(tv.weights[topic] - 1.0 / len(CUSTOM_TOPICS)) < 1e-6

    def test_create_from_selected_with_all_topics(self):
        """create_from_selected accepts all_topics list explicitly."""
        from src.domain.value_objects.topic_vector import TopicVector

        selected = ["технологии", "наука"]
        tv = TopicVector.create_from_selected(
            selected_topics=selected,
            all_topics=CUSTOM_TOPICS,
            baseline_weight=0.01,
        )
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6
        assert tv.weights["технологии"] > tv.weights["спорт"]
        assert tv.weights["наука"] > tv.weights["спорт"]

    def test_create_from_selected_unknown_topic_ignored(self):
        """Unknown topics in selected_topics are ignored gracefully."""
        from src.domain.value_objects.topic_vector import TopicVector

        selected = ["технологии", "несуществующая_тема"]
        tv = TopicVector.create_from_selected(
            selected_topics=selected,
            all_topics=CUSTOM_TOPICS,
            baseline_weight=0.01,
        )
        # Should not raise, should normalize
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6
        assert "несуществующая_тема" not in tv.weights

    def test_apply_update_with_dynamic_topics(self):
        """apply_update works with dynamically-loaded topics."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector.create_uniform(topics=CUSTOM_TOPICS)
        updated = tv.apply_update(lr=0.08, weight=0.6, topic_scores={"технологии": 0.9})
        assert updated.weights["технологии"] > tv.weights["технологии"]
        assert abs(sum(updated.weights.values()) - 1.0) < 1e-6

    def test_backward_compat_no_topics_param_uses_class_topics(self):
        """Old-style construction without topics param still works using TOPICS class attr."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector(weights={t: 1.0 for t in TopicVector.TOPICS})
        assert len(tv.weights) == 18
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6

    def test_topics_constant_matches_18_categories(self):
        """TOPICS class constant must still contain all 18 original categories."""
        from src.domain.value_objects.topic_vector import TopicVector

        assert len(TopicVector.TOPICS) == 18
        for topic in ALL_18_TOPICS:
            assert topic in TopicVector.TOPICS

    def test_create_uniform_no_topics_uses_class_topics(self):
        """create_uniform without topics param uses TOPICS."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector.create_uniform()
        assert len(tv.weights) == 18

    def test_create_from_selected_no_all_topics_uses_class_topics(self):
        """create_from_selected without all_topics uses TOPICS."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector.create_from_selected(["технологии", "наука", "спорт"])
        assert len(tv.weights) == 18
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6

    def test_compute_topic_match_with_dynamic_topics(self):
        """Scoring works with dynamically-loaded topics (no hardcoded topic list)."""
        from src.domain.value_objects.topic_vector import TopicVector

        tv = TopicVector(
            weights={"технологии": 0.8, "наука": 0.1, "спорт": 0.05, "культура": 0.05},
            topics=CUSTOM_TOPICS,
        )
        # High weight on технологии, post is about технологии
        topic_scores = {"технологии": 0.9, "наука": 0.05}
        score = sum(tv.weights.get(t, 0.0) * s for t, s in topic_scores.items())
        assert score > 0.5
