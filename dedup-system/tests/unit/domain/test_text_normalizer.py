import unicodedata

import pytest


class TestTextNormalizer:
    @pytest.mark.unit
    def test_normalize_collapses_whitespace(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        assert norm.normalize("hello   world") == "hello world"

    @pytest.mark.unit
    def test_normalize_strips_edges(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        assert norm.normalize("  hello  ") == "hello"

    @pytest.mark.unit
    def test_normalize_removes_control_chars(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        result = norm.normalize("hello\u200bworld")
        assert result == "helloworld"

    @pytest.mark.unit
    def test_normalize_keeps_newline_tab(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        result = norm.normalize("hello\n\tworld")
        assert result == "hello world"

    @pytest.mark.unit
    def test_normalize_nfc(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        decomposed = "e\u0301"
        result = norm.normalize(decomposed)
        assert result == unicodedata.normalize("NFC", decomposed)

    @pytest.mark.unit
    def test_normalize_does_not_lowercase(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        assert norm.normalize("Hello World") == "Hello World"

    @pytest.mark.unit
    def test_compute_hash_returns_content_hash(self):
        from src.domain.services.text_normalizer import TextNormalizer
        from src.domain.value_objects.content_hash import ContentHash

        norm = TextNormalizer()
        h = norm.compute_hash("Hello World")
        assert isinstance(h, ContentHash)

    @pytest.mark.unit
    def test_compute_hash_lowercases_for_hashing(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        h1 = norm.compute_hash("Hello World")
        h2 = norm.compute_hash("hello world")
        assert h1 == h2

    @pytest.mark.unit
    def test_compute_hash_different_texts_different_hashes(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        h1 = norm.compute_hash("text one")
        h2 = norm.compute_hash("text two")
        assert h1 != h2

    @pytest.mark.unit
    def test_normalize_russian_text(self):
        from src.domain.services.text_normalizer import TextNormalizer

        norm = TextNormalizer()
        result = norm.normalize("  Центробанк   повысил   ставку  ")
        assert result == "Центробанк повысил ставку"
