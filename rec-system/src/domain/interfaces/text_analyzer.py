from __future__ import annotations

from abc import ABC, abstractmethod


class TextAnalyzer(ABC):
    """Abstract port for computing text metrics synchronously (no I/O)."""

    @abstractmethod
    def analyze(self, text: str) -> dict:
        """Return a dict with word_count, text_length, reading_time, complexity,
        is_long_form, and is_short_form for the given text."""
        ...
