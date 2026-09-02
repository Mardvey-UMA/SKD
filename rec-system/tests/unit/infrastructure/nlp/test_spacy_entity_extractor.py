"""Unit tests for SpacyEntityExtractor — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import MagicMock, patch

from src.domain.interfaces.entity_extractor import EntityExtractor


def _make_ent(text: str, label: str) -> MagicMock:
    ent = MagicMock()
    ent.text = text
    ent.label_ = label
    return ent


def _make_doc(ents) -> MagicMock:
    doc = MagicMock()
    doc.ents = ents
    return doc


class TestSpacyEntityExtractor:
    @pytest.fixture
    def mock_nlp(self):
        nlp = MagicMock()
        nlp.return_value = _make_doc([
            _make_ent("Путин", "PER"),
            _make_ent("Кремль", "ORG"),
            _make_ent("Москва", "LOC"),
        ])
        return nlp

    @pytest.fixture
    def sut(self, mock_nlp):
        with patch("src.infrastructure.nlp.spacy_entity_extractor.spacy") as mock_spacy:
            mock_spacy.load.return_value = mock_nlp
            from src.infrastructure.nlp.spacy_entity_extractor import SpacyEntityExtractor
            return SpacyEntityExtractor(model_name="ru_core_news_lg")

    def test_implements_interface(self, sut):
        assert isinstance(sut, EntityExtractor)

    @pytest.mark.asyncio
    async def test_extract_returns_three_categories(self, sut):
        result = await sut.extract("Путин встретился с Макроном в Москве.")
        assert "persons" in result
        assert "organizations" in result
        assert "locations" in result

    @pytest.mark.asyncio
    async def test_persons_extracted(self, sut):
        result = await sut.extract("Путин встретился.")
        assert "Путин" in result["persons"]

    @pytest.mark.asyncio
    async def test_organizations_extracted(self, sut):
        result = await sut.extract("Кремль объявил.")
        assert "Кремль" in result["organizations"]

    @pytest.mark.asyncio
    async def test_locations_extracted(self, sut):
        result = await sut.extract("В Москве прошёл митинг.")
        assert "Москва" in result["locations"]

    @pytest.mark.asyncio
    async def test_empty_text(self, sut, mock_nlp):
        mock_nlp.return_value = _make_doc([])
        result = await sut.extract("")
        assert result["persons"] == []
        assert result["organizations"] == []
        assert result["locations"] == []

    @pytest.mark.asyncio
    async def test_no_dedup_matches_reference(self, sut, mock_nlp):
        """Reference pipeline does NOT deduplicate entities."""
        mock_nlp.return_value = _make_doc([
            _make_ent("Путин", "PER"),
            _make_ent("Путин", "PER"),
            _make_ent("Путин", "PER"),
        ])
        result = await sut.extract("Путин, Путин и снова Путин.")
        assert result["persons"].count("Путин") == 3

    @pytest.mark.asyncio
    async def test_truncates_text_to_4096(self, sut, mock_nlp):
        """Reference pipeline truncates to MAX_TEXT_SPACY = 4096."""
        long_text = "a" * 10000
        await sut.extract(long_text)
        # Verify the nlp was called with truncated text
        call_args = mock_nlp.call_args[0][0]
        assert len(call_args) <= 4096

    @pytest.mark.asyncio
    async def test_disables_unused_components(self):
        """Reference pipeline disables lemmatizer and attribute_ruler."""
        with patch("src.infrastructure.nlp.spacy_entity_extractor.spacy") as mock_spacy:
            mock_nlp = MagicMock()
            mock_spacy.load.return_value = mock_nlp
            from src.infrastructure.nlp.spacy_entity_extractor import SpacyEntityExtractor
            SpacyEntityExtractor(model_name="ru_core_news_lg")
            call_kwargs = mock_spacy.load.call_args[1]
            assert "lemmatizer" in call_kwargs.get("disable", [])
            assert "attribute_ruler" in call_kwargs.get("disable", [])
