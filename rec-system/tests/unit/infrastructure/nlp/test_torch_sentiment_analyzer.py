"""Unit tests for TorchSentimentAnalyzer — pipeline API version."""
from __future__ import annotations

import pytest
from unittest.mock import MagicMock, patch

from src.domain.interfaces.sentiment_analyzer import SentimentAnalyzer

VALID_LABELS = {"POSITIVE", "NEGATIVE", "NEUTRAL"}


class TestTorchSentimentAnalyzer:
    @pytest.fixture
    def pipe_instance(self):
        inst = MagicMock()
        inst.return_value = [{"label": "POSITIVE", "score": 0.92}]
        return inst

    @pytest.fixture
    def mock_pipeline(self, pipe_instance):
        factory = MagicMock()
        factory.return_value = pipe_instance
        return factory

    @pytest.fixture
    def sut(self, mock_pipeline):
        with patch("src.infrastructure.nlp.torch_sentiment_analyzer.pipeline", mock_pipeline):
            from src.infrastructure.nlp.torch_sentiment_analyzer import TorchSentimentAnalyzer
            return TorchSentimentAnalyzer(model_name="test-model", batch_size=128)

    def test_implements_interface(self, sut):
        assert isinstance(sut, SentimentAnalyzer)

    @pytest.mark.asyncio
    async def test_analyze_returns_tuple(self, sut):
        result = await sut.analyze("Отличный день!")
        assert isinstance(result, tuple)
        assert len(result) == 2

    @pytest.mark.asyncio
    async def test_label_is_valid(self, sut):
        label, _ = await sut.analyze("Текст.")
        assert label in VALID_LABELS

    @pytest.mark.asyncio
    async def test_score_is_float_in_range(self, sut):
        _, score = await sut.analyze("Текст.")
        assert isinstance(score, float)
        assert 0.0 <= score <= 1.0

    @pytest.mark.asyncio
    async def test_positive_label_returned(self, sut):
        label, score = await sut.analyze("Хороший день.")
        assert label == "POSITIVE"
        assert score == 0.92

    @pytest.mark.asyncio
    async def test_negative_label(self, sut, pipe_instance):
        pipe_instance.return_value = [{"label": "NEGATIVE", "score": 0.87}]
        label, score = await sut.analyze("Всё плохо.")
        assert label == "NEGATIVE"
        assert score == 0.87

    @pytest.mark.asyncio
    async def test_normalizes_label_to_uppercase(self, sut, pipe_instance):
        pipe_instance.return_value = [{"label": "neutral", "score": 0.6}]
        label, _ = await sut.analyze("Обычный день.")
        assert label == "NEUTRAL"

    def test_uses_correct_default_model(self):
        from src.infrastructure.nlp.torch_sentiment_analyzer import TorchSentimentAnalyzer
        import inspect
        sig = inspect.signature(TorchSentimentAnalyzer.__init__)
        assert sig.parameters["model_name"].default == "blanchefort/rubert-base-cased-sentiment"
