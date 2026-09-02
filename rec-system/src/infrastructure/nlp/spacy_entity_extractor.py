"""SpacyEntityExtractor — named entity recognition with spaCy.

Matches the reference pipeline: disable unused components, truncate text,
extract PER/ORG/LOC entities. Filters garbage entities.
"""
from __future__ import annotations

import asyncio
import re

import spacy

from src.domain.interfaces.entity_extractor import EntityExtractor

# spaCy entity label -> output category mapping
_LABEL_MAP: dict[str, str] = {
    "PER": "persons",
    "ORG": "organizations",
    "LOC": "locations",
}

MAX_TEXT_LENGTH = 4096

# Minimum length for a valid entity (skip single chars, quotes, etc.)
_MIN_ENTITY_LENGTH = 2

# Regex: entity must contain at least one letter
_HAS_LETTER = re.compile(r"[a-zA-Zа-яА-ЯёЁ]")


def _is_valid_entity(text: str) -> bool:
    """Filter garbage entities: single chars, quotes, special symbols, whitespace-only."""
    stripped = text.strip()
    if len(stripped) < _MIN_ENTITY_LENGTH:
        return False
    if not _HAS_LETTER.search(stripped):
        return False
    return True


class SpacyEntityExtractor(EntityExtractor):
    """Named entity extractor using spaCy ru_core_news_lg model.

    Matches the reference pipeline:
    - Disables lemmatizer and attribute_ruler for speed
    - Truncates text to 4096 chars
    - Extracts PER, ORG, LOC entities
    - Filters garbage: single chars, quotes, special symbols
    - Deduplicates entities per category
    """

    def __init__(self, model_name: str = "ru_core_news_lg") -> None:
        self._nlp = spacy.load(model_name, disable=["lemmatizer", "attribute_ruler"])
        self._nlp.max_length = 5_000_000

    async def extract(self, text: str) -> dict[str, list[str]]:
        """Extract named entities from the given text.

        Returns:
            Dict with keys 'persons', 'organizations', 'locations'.
        """
        result: dict[str, list[str]] = {
            "persons": [],
            "organizations": [],
            "locations": [],
        }

        if not text:
            return result

        # Truncate to match reference pipeline.
        # Run spaCy in thread pool to avoid blocking the asyncio event loop.
        doc = await asyncio.to_thread(self._nlp, text[:MAX_TEXT_LENGTH])

        seen: dict[str, set[str]] = {k: set() for k in result}

        for ent in doc.ents:
            category = _LABEL_MAP.get(ent.label_)
            if not category:
                continue
            entity_text = ent.text.strip()
            if not _is_valid_entity(entity_text):
                continue
            if entity_text not in seen[category]:
                seen[category].add(entity_text)
                result[category].append(entity_text)

        return result
