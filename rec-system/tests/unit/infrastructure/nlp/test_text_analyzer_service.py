import pytest
from src.infrastructure.nlp.text_analyzer_service import TextAnalyzerService
from src.domain.interfaces.text_analyzer import TextAnalyzer


class TestTextAnalyzerService:
    @pytest.fixture
    def sut(self) -> TextAnalyzerService:
        return TextAnalyzerService()

    def test_implements_text_analyzer_port(self, sut):
        assert isinstance(sut, TextAnalyzer)

    def test_word_count(self, sut):
        text = "hello world this is a test"
        result = sut.analyze(text)
        assert result["word_count"] == 6

    def test_text_length(self, sut):
        text = "hello world"
        result = sut.analyze(text)
        assert result["text_length"] == len("hello world")

    def test_reading_time(self, sut):
        words = " ".join(["word"] * 200)
        result = sut.analyze(words)
        assert result["reading_time_min"] == pytest.approx(1.0, abs=0.01)

    def test_sentence_count(self, sut):
        text = "First sentence. Second sentence. Third."
        result = sut.analyze(text)
        assert result["sentence_count"] == 3

    def test_avg_word_length(self, sut):
        text = "hi there world"
        result = sut.analyze(text)
        # (2 + 5 + 5) / 3 = 4.0
        assert result["avg_word_length"] == 4.0

    def test_unique_word_ratio(self, sut):
        text = "the the the cat"
        result = sut.analyze(text)
        assert result["unique_word_ratio"] == pytest.approx(0.5, abs=0.01)

    def test_caps_ratio(self, sut):
        text = "Hello World"
        result = sut.analyze(text)
        assert result["caps_ratio"] > 0

    def test_digit_count(self, sut):
        text = "test 123 data"
        result = sut.analyze(text)
        assert result["digit_count"] == 3

    def test_has_links(self, sut):
        text = "visit https://example.com for more"
        result = sut.analyze(text)
        assert result["has_links"] is True
        assert result["link_count"] == 1

    def test_no_links(self, sut):
        text = "no links here"
        result = sut.analyze(text)
        assert result["has_links"] is False
        assert result["link_count"] == 0

    def test_complexity_normal_text(self, sut):
        text = "Это длинный текст для проверки сложности. " * 10
        result = sut.analyze(text)
        assert 0.0 <= result["complexity"] <= 1.0

    def test_complexity_short_text_fallback(self, sut):
        text = "Кот."
        result = sut.analyze(text)
        assert result["complexity"] == 0.5

    def test_is_long_form(self, sut):
        text = " ".join(["word"] * 300)
        result = sut.analyze(text)
        assert result["is_long_form"] is True

    def test_is_short_form_under_50_words(self, sut):
        """Reference pipeline: is_short_form = word_count < 50."""
        text = " ".join(["word"] * 30)
        result = sut.analyze(text)
        assert result["is_short_form"] is True

    def test_not_short_form_above_50_words(self, sut):
        text = " ".join(["word"] * 60)
        result = sut.analyze(text)
        assert result["is_short_form"] is False

    def test_analyze_returns_all_metrics(self, sut):
        text = "This is a simple test text with several words."
        result = sut.analyze(text)
        expected_keys = {
            "word_count", "text_length", "sentence_count",
            "avg_word_length", "unique_word_ratio", "caps_ratio",
            "digit_count", "has_links", "link_count",
            "reading_time_min", "is_long_form", "is_short_form",
            "complexity",
        }
        assert expected_keys.issubset(result.keys())

    def test_empty_text(self, sut):
        result = sut.analyze("")
        assert result["word_count"] == 0
        assert result["text_length"] == 0
        assert result["reading_time_min"] == 0.0
        assert result["complexity"] == 0.5
        assert result["is_long_form"] is False
        assert result["is_short_form"] is True
