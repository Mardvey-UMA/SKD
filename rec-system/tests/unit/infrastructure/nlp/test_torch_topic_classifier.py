"""Unit tests for TorchTopicClassifier — pipeline API version."""
from __future__ import annotations

import pytest
from unittest.mock import MagicMock, patch

from src.domain.interfaces.topic_classifier import TopicClassifier


VALID_TOPICS = {
    "политика", "экономика", "технологии", "наука", "спорт", "культура",
    "общество", "происшествия", "международные новости", "бизнес",
    "финансы", "образование", "здоровье", "развлечения", "криминал",
    "армия", "природа", "транспорт",
}


class TestTorchTopicClassifier:
    @pytest.fixture
    def pipe_instance(self):
        inst = MagicMock()
        inst.return_value = {
            "labels": ["технологии", "наука", "экономика"],
            "scores": [0.85, 0.10, 0.05],
        }
        return inst

    @pytest.fixture
    def mock_pipeline(self, pipe_instance):
        factory = MagicMock()
        factory.return_value = pipe_instance
        return factory

    @pytest.fixture
    def sut(self, mock_pipeline):
        with patch("src.infrastructure.nlp.torch_topic_classifier.pipeline", mock_pipeline):
            from src.infrastructure.nlp.torch_topic_classifier import TorchTopicClassifier
            return TorchTopicClassifier(model_name="test-model", batch_size=32)

    def test_implements_interface(self, sut):
        assert isinstance(sut, TopicClassifier)

    @pytest.mark.asyncio
    async def test_classify_returns_top3(self, sut):
        result = await sut.classify("Новый процессор от Intel.")
        assert isinstance(result, list)
        assert len(result) == 3
        for item in result:
            assert isinstance(item, tuple)
            assert len(item) == 2

    @pytest.mark.asyncio
    async def test_topics_are_valid(self, sut):
        result = await sut.classify("Правительство приняло закон.")
        topics = {topic for topic, _ in result}
        assert topics.issubset(VALID_TOPICS)

    @pytest.mark.asyncio
    async def test_scores_are_floats_in_range(self, sut):
        result = await sut.classify("Матч прошёл успешно.")
        for _, score in result:
            assert isinstance(score, float)
            assert 0.0 <= score <= 1.0

    @pytest.mark.asyncio
    async def test_sorted_by_score_desc(self, sut):
        result = await sut.classify("Текст.")
        scores = [s for _, s in result]
        assert scores == sorted(scores, reverse=True)

    @pytest.mark.asyncio
    async def test_pipeline_called_with_hypothesis(self, sut, pipe_instance):
        await sut.classify("Тестовый текст.")
        call_kwargs = pipe_instance.call_args[1]
        assert "hypothesis_template" in call_kwargs
        assert "Этот текст про {}." == call_kwargs["hypothesis_template"]
        assert len(call_kwargs["candidate_labels"]) == 18

    def test_uses_correct_default_model(self):
        from src.infrastructure.nlp.torch_topic_classifier import TorchTopicClassifier
        import inspect
        sig = inspect.signature(TorchTopicClassifier.__init__)
        assert sig.parameters["model_name"].default == "cointegrated/rubert-base-cased-nli-threeway"

    @pytest.mark.asyncio
    async def test_classify_empty_string_returns_empty_list(self, sut, pipe_instance):
        result = await sut.classify("")
        assert result == []
        pipe_instance.assert_not_called()

    @pytest.mark.asyncio
    async def test_classify_whitespace_only_returns_empty_list(self, sut, pipe_instance):
        result = await sut.classify("   \n\t  ")
        assert result == []
        pipe_instance.assert_not_called()

    @pytest.mark.asyncio
    async def test_classify_none_returns_empty_list(self, sut, pipe_instance):
        result = await sut.classify(None)
        assert result == []
        pipe_instance.assert_not_called()
