"""Unit tests for is_long_form threshold behaviour — RED phase.

Verifies that the threshold is 200 words (down from 300) and is
tunable via REC_LONG_FORM_THRESHOLD env var.
"""
from __future__ import annotations

import os
import pytest


class TestLongFormThreshold:
    def _make_sut(self):
        from src.infrastructure.nlp.text_analyzer_service import TextAnalyzerService
        return TextAnalyzerService()

    # --- Boundary checks at default threshold 200 ---

    def test_199_words_is_not_long_form(self):
        sut = self._make_sut()
        text = " ".join(["word"] * 199)
        result = sut.analyze(text)
        assert result["is_long_form"] is False

    def test_200_words_is_long_form(self):
        sut = self._make_sut()
        text = " ".join(["word"] * 200)
        result = sut.analyze(text)
        assert result["is_long_form"] is True

    def test_299_words_is_long_form(self):
        """299 words would have been False under the old 300-word threshold."""
        sut = self._make_sut()
        text = " ".join(["word"] * 299)
        result = sut.analyze(text)
        assert result["is_long_form"] is True

    def test_300_words_is_still_long_form(self):
        """Regression: 300 words must still be long form."""
        sut = self._make_sut()
        text = " ".join(["word"] * 300)
        result = sut.analyze(text)
        assert result["is_long_form"] is True

    # --- Env var override ---

    def test_env_var_override_lower_threshold(self):
        os.environ["REC_LONG_FORM_THRESHOLD"] = "150"
        try:
            sut = self._make_sut()
            # 149 words → False
            assert sut.analyze(" ".join(["word"] * 149))["is_long_form"] is False
            # 150 words → True
            assert sut.analyze(" ".join(["word"] * 150))["is_long_form"] is True
        finally:
            del os.environ["REC_LONG_FORM_THRESHOLD"]

    def test_env_var_override_higher_threshold(self):
        os.environ["REC_LONG_FORM_THRESHOLD"] = "250"
        try:
            sut = self._make_sut()
            # 200 words would be long form at default 200 but NOT at 250
            assert sut.analyze(" ".join(["word"] * 200))["is_long_form"] is False
            assert sut.analyze(" ".join(["word"] * 250))["is_long_form"] is True
        finally:
            del os.environ["REC_LONG_FORM_THRESHOLD"]

    def test_default_threshold_is_200_without_env_var(self):
        os.environ.pop("REC_LONG_FORM_THRESHOLD", None)
        sut = self._make_sut()
        # exactly at boundary
        assert sut.analyze(" ".join(["word"] * 199))["is_long_form"] is False
        assert sut.analyze(" ".join(["word"] * 200))["is_long_form"] is True
