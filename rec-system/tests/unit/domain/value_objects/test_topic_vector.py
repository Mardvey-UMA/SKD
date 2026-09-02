import pytest
from src.domain.value_objects.topic_vector import TopicVector


class TestTopicVector:
    def test_topics_list(self):
        topics = TopicVector.TOPICS
        assert len(topics) == 18
        assert "политика" in topics
        assert "экономика" in topics
        assert "технологии" in topics
        assert "наука" in topics
        assert "спорт" in topics
        assert "культура" in topics
        assert "общество" in topics
        assert "происшествия" in topics
        assert "международные новости" in topics
        assert "бизнес" in topics
        assert "финансы" in topics
        assert "образование" in topics
        assert "здоровье" in topics
        assert "развлечения" in topics
        assert "криминал" in topics
        assert "армия" in topics
        assert "природа" in topics
        assert "транспорт" in topics

    def test_create_from_dict_with_all_topics(self):
        weights = {topic: 1.0 / 18 for topic in TopicVector.TOPICS}
        tv = TopicVector(weights)
        assert len(tv.weights) == 18

    def test_normalization_sums_to_one(self):
        weights = {topic: 1.0 for topic in TopicVector.TOPICS}
        tv = TopicVector(weights)
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6

    def test_clamp_negative_values_to_zero(self):
        weights = {topic: 1.0 for topic in TopicVector.TOPICS}
        weights["политика"] = -5.0
        tv = TopicVector(weights)
        assert tv.weights["политика"] == 0.0
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6

    def test_get_topic_weight(self):
        weights = {topic: 0.0 for topic in TopicVector.TOPICS}
        weights["технологии"] = 1.0
        tv = TopicVector(weights)
        assert tv.get("технологии") > 0.0
        assert tv.get("политика") == 0.0

    def test_create_uniform(self):
        tv = TopicVector.create_uniform()
        for topic in TopicVector.TOPICS:
            assert abs(tv.weights[topic] - 1.0 / 18) < 1e-6
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6

    def test_create_from_selected_topics(self):
        selected = ["технологии", "наука", "экономика"]
        baseline_weight = 0.01
        tv = TopicVector.create_from_selected(selected, baseline_weight=baseline_weight)
        # selected topics get equal share, rest get baseline_weight
        assert tv.weights["технологии"] > tv.weights["политика"]
        assert abs(sum(tv.weights.values()) - 1.0) < 1e-6
        # non-selected topics should have uniform small values
        for topic in TopicVector.TOPICS:
            if topic not in selected:
                assert abs(tv.weights[topic] - tv.weights["политика"]) < 1e-9

    def test_apply_update(self):
        tv = TopicVector.create_uniform()
        lr = 0.08
        weight = 0.6
        topic_scores = {"технологии": 0.9, "наука": 0.7, "экономика": 0.5}
        updated = tv.apply_update(lr=lr, weight=weight, topic_scores=topic_scores)
        # Returns a new instance
        assert updated is not tv
        # технологии should have increased more than before
        assert updated.weights["технологии"] > tv.weights["технологии"]
        # Still normalized
        assert abs(sum(updated.weights.values()) - 1.0) < 1e-6

    def test_to_dict(self):
        tv = TopicVector.create_uniform()
        d = tv.to_dict()
        assert isinstance(d, dict)
        assert len(d) == 18
        assert "технологии" in d
        assert isinstance(d["технологии"], float)
