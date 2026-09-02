"""Failing tests for domain NLP port interfaces (Task 18)."""

import inspect
import pytest

from src.domain.interfaces.content_encoder import ContentEncoder
from src.domain.interfaces.topic_classifier import TopicClassifier
from src.domain.interfaces.sentiment_analyzer import SentimentAnalyzer
from src.domain.interfaces.entity_extractor import EntityExtractor
from src.domain.interfaces.text_analyzer import TextAnalyzer


class TestContentEncoder:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            ContentEncoder()  # type: ignore[abstract]

    def test_has_encode_method(self):
        assert hasattr(ContentEncoder, "encode")
        method = getattr(ContentEncoder, "encode")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "text" in params
        hints = method.__annotations__ if hasattr(method, "__annotations__") else {}
        assert "return" in hints

    def test_has_encode_batch_method(self):
        assert hasattr(ContentEncoder, "encode_batch")
        method = getattr(ContentEncoder, "encode_batch")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "texts" in params


class TestTopicClassifier:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            TopicClassifier()  # type: ignore[abstract]

    def test_has_classify_method(self):
        assert hasattr(TopicClassifier, "classify")
        method = getattr(TopicClassifier, "classify")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "text" in params


class TestSentimentAnalyzer:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            SentimentAnalyzer()  # type: ignore[abstract]

    def test_has_analyze_method(self):
        assert hasattr(SentimentAnalyzer, "analyze")
        method = getattr(SentimentAnalyzer, "analyze")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "text" in params


class TestEntityExtractor:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            EntityExtractor()  # type: ignore[abstract]

    def test_has_extract_method(self):
        assert hasattr(EntityExtractor, "extract")
        method = getattr(EntityExtractor, "extract")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "text" in params


class TestTextAnalyzer:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            TextAnalyzer()  # type: ignore[abstract]

    def test_has_analyze_method(self):
        assert hasattr(TextAnalyzer, "analyze")
        method = getattr(TextAnalyzer, "analyze")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "text" in params

    def test_analyze_is_sync(self):
        """TextAnalyzer.analyze must be synchronous (no I/O, pure computation)."""
        method = getattr(TextAnalyzer, "analyze")
        assert not inspect.iscoroutinefunction(method)
