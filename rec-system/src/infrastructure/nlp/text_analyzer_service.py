"""TextAnalyzerService — computes text metrics matching the reference pipeline.

Extracts 12 features: text_length, word_count, sentence_count, avg_word_length,
unique_word_ratio, caps_ratio, digit_count, has_links, link_count,
reading_time_min, is_long_form, is_short_form, plus complexity_score via textstat.
"""
from __future__ import annotations

import os
import re

import textstat

from src.domain.interfaces.text_analyzer import TextAnalyzer


class TextAnalyzerService(TextAnalyzer):
    """Computes text metrics matching the reference processing pipeline.

    Uses textstat for Flesch-Oborneva complexity (Russian locale).
    """

    _READING_SPEED_WPM: int = 200
    _SHORT_FORM_THRESHOLD: int = 50   # words — matches reference pipeline
    _MIN_CHARS_FOR_COMPLEXITY: int = 10

    def __init__(self) -> None:
        textstat.set_lang("ru")
        self._LONG_FORM_THRESHOLD: int = int(
            os.environ.get("REC_LONG_FORM_THRESHOLD", "200")
        )

    def analyze(self, text: str) -> dict:
        """Return a dict with all text metrics matching the reference pipeline."""
        if not text:
            return {
                "text_length": 0,
                "word_count": 0,
                "sentence_count": 0,
                "avg_word_length": 0.0,
                "unique_word_ratio": 0.0,
                "caps_ratio": 0.0,
                "digit_count": 0,
                "has_links": False,
                "link_count": 0,
                "reading_time_min": 0.0,
                "is_long_form": False,
                "is_short_form": True,
                "complexity": 0.5,
            }

        words = text.split()
        wc = len(words)
        sentences = [s for s in text.split(".") if s.strip()]
        letters = [c for c in text if c.isalpha()]
        links = re.findall(r"https?://\S+", text)
        caps = sum(1 for c in letters if c.isupper())

        # Complexity via textstat (Flesch-Oborneva for Russian)
        complexity = self._compute_complexity(text)

        return {
            "text_length": len(text),
            "word_count": wc,
            "sentence_count": len(sentences),
            "avg_word_length": round(sum(len(w) for w in words) / wc, 2) if wc else 0.0,
            "unique_word_ratio": round(len(set(words)) / wc, 3) if wc else 0.0,
            "caps_ratio": round(caps / len(letters), 3) if letters else 0.0,
            "digit_count": sum(c.isdigit() for c in text),
            "has_links": len(links) > 0,
            "link_count": len(links),
            "reading_time_min": round(wc / self._READING_SPEED_WPM, 2),
            "is_long_form": wc >= self._LONG_FORM_THRESHOLD,
            "is_short_form": wc < self._SHORT_FORM_THRESHOLD,
            "complexity": complexity,
        }

    def _compute_complexity(self, text: str) -> float:
        """Flesch-Oborneva via textstat, normalized to [0, 1]. Fallback 0.5."""
        text = text.strip()
        if len(text) < self._MIN_CHARS_FOR_COMPLEXITY:
            return 0.5
        try:
            fre = textstat.flesch_reading_ease(text)
            fre = max(0.0, min(100.0, fre))
            return round((100.0 - fre) / 100.0, 4)
        except Exception:
            return 0.5
