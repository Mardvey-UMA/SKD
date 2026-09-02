"""Unit tests for text_window.py — H+M+T token-aware text extraction.

Mirror of dedup-system tests (T4).
RED phase: module does not exist yet.
"""
from __future__ import annotations

import pytest
from unittest.mock import MagicMock, call

# RED: this import will fail until text_window.py is created
from src.infrastructure.nlp.text_window import extract_hmt, prepend_title


# ---------------------------------------------------------------------------
# Minimal tokenizer mock satisfying TokenizerProtocol
# ---------------------------------------------------------------------------

def _make_tokenizer(n_tokens: int = 10, sep_tokens: int = 3) -> MagicMock:
    """Return a mock tokenizer that returns n_tokens for any non-separator text."""
    tokenizer = MagicMock()

    def encode_side_effect(text: str, add_special_tokens: bool = False) -> list[int]:
        if text == " [...] ":
            return list(range(sep_tokens))
        return list(range(n_tokens))

    tokenizer.encode.side_effect = encode_side_effect
    tokenizer.decode.side_effect = lambda tokens, skip_special_tokens=True: (
        f"tok_{len(tokens)}"
    )
    return tokenizer


# ---------------------------------------------------------------------------
# Tests for prepend_title()
# ---------------------------------------------------------------------------

@pytest.mark.unit
class TestPrependTitle:
    def test_title_and_body_joined_with_period_space(self):
        result = prepend_title("Заголовок", "Текст статьи")
        assert result == "Заголовок. Текст статьи"

    def test_title_ending_with_dot_uses_space_only(self):
        result = prepend_title("Заголовок.", "Текст статьи")
        assert result == "Заголовок. Текст статьи"

    def test_title_ending_with_exclamation_uses_space_only(self):
        result = prepend_title("Заголовок!", "Текст статьи")
        assert result == "Заголовок! Текст статьи"

    def test_title_ending_with_question_uses_space_only(self):
        result = prepend_title("Заголовок?", "Текст статьи")
        assert result == "Заголовок? Текст статьи"

    def test_title_ending_with_colon_uses_space_only(self):
        result = prepend_title("Заголовок:", "Текст статьи")
        assert result == "Заголовок: Текст статьи"

    def test_empty_title_returns_body(self):
        result = prepend_title("", "Текст статьи")
        assert result == "Текст статьи"

    def test_none_title_returns_body(self):
        result = prepend_title(None, "Текст статьи")
        assert result == "Текст статьи"

    def test_empty_body_returns_title(self):
        result = prepend_title("Заголовок", "")
        assert result == "Заголовок"

    def test_both_empty_returns_empty(self):
        result = prepend_title("", "")
        assert result == ""

    def test_none_title_empty_body_returns_empty(self):
        result = prepend_title(None, "")
        assert result == ""

    def test_title_with_whitespace_stripped(self):
        result = prepend_title("  Заголовок  ", "Текст")
        assert result == "Заголовок. Текст"

    def test_body_only_when_title_is_whitespace(self):
        result = prepend_title("   ", "Текст")
        assert result == "Текст"


# ---------------------------------------------------------------------------
# Tests for extract_hmt()
# ---------------------------------------------------------------------------

@pytest.mark.unit
class TestExtractHmt:
    def test_empty_string_returns_empty_no_truncation(self):
        tokenizer = MagicMock()
        result, was_truncated = extract_hmt("", tokenizer, max_tokens=512)
        assert result == ""
        assert was_truncated is False
        tokenizer.encode.assert_not_called()

    def test_short_text_returned_unchanged(self):
        """10 tokens << 512-8=504, no truncation."""
        tokenizer = _make_tokenizer(n_tokens=10)
        text = "Короткий текст"
        result, was_truncated = extract_hmt(text, tokenizer, max_tokens=512)
        assert result == text
        assert was_truncated is False

    def test_exactly_at_limit_not_truncated(self):
        """Exactly max_tokens-8 tokens: boundary case — no truncation."""
        tokenizer = _make_tokenizer(n_tokens=504)  # 512 - 8 = 504
        text = "boundary text"
        result, was_truncated = extract_hmt(text, tokenizer, max_tokens=512)
        assert result == text
        assert was_truncated is False

    def test_one_over_limit_is_truncated(self):
        """505 tokens > 504: truncation kicks in."""
        tokenizer = _make_tokenizer(n_tokens=505)
        text = "slightly over limit text"
        result, was_truncated = extract_hmt(text, tokenizer, max_tokens=512)
        assert was_truncated is True

    def test_long_text_contains_two_separators(self):
        """H+M+T result has exactly 2 default separators."""
        tokenizer = _make_tokenizer(n_tokens=600)
        result, was_truncated = extract_hmt("long " * 100, tokenizer, max_tokens=512)
        assert was_truncated is True
        assert result.count(" [...] ") == 2

    def test_long_text_separator_present(self):
        tokenizer = _make_tokenizer(n_tokens=600)
        result, _ = extract_hmt("long " * 100, tokenizer, max_tokens=512)
        assert " [...] " in result

    def test_custom_separator_used(self):
        tokenizer = _make_tokenizer(n_tokens=600, sep_tokens=2)

        def encode_side(text, **kwargs):
            if text == "===":
                return [1, 2]
            return list(range(600))

        tokenizer.encode.side_effect = encode_side
        tokenizer.decode.side_effect = lambda tokens, **kwargs: f"part_{len(tokens)}"

        result, was_truncated = extract_hmt("word " * 200, tokenizer, max_tokens=512, separator="===")
        assert was_truncated is True
        assert "===" in result

    def test_decode_called_for_head_mid_tail(self):
        """decode() must be called exactly 3 times for H, M, T segments."""
        tokenizer = _make_tokenizer(n_tokens=600)
        extract_hmt("long " * 100, tokenizer, max_tokens=512)
        # decode called 3 times (head, mid, tail)
        assert tokenizer.decode.call_count == 3

    def test_budget_distribution_50_25_25(self):
        """Head gets 50% of budget, tail 25%, mid 25%."""
        sep_len = 3
        budget = 512 - 8 - 2 * sep_len  # = 498
        expected_head = budget // 2       # = 249
        expected_tail = budget // 4       # = 124
        expected_mid = budget - expected_head - expected_tail  # = 125

        decode_calls = []
        tokenizer = MagicMock()

        def encode_side(text, **kwargs):
            if text == " [...] ":
                return list(range(sep_len))
            return list(range(600))

        def decode_side(tokens, **kwargs):
            decode_calls.append(len(tokens))
            return f"seg_{len(tokens)}"

        tokenizer.encode.side_effect = encode_side
        tokenizer.decode.side_effect = decode_side

        extract_hmt("a " * 300, tokenizer, max_tokens=512)

        assert decode_calls[0] == expected_head  # head
        assert decode_calls[1] == expected_mid   # mid
        assert decode_calls[2] == expected_tail  # tail

    def test_zero_budget_fallback(self):
        """When budget ≤ 0 (very small max_tokens), decode first max(1, n-8) tokens."""
        tokenizer = MagicMock()
        # separator costs 5 tokens; 2 separators = 10; budget = 8 - 8 - 10 = -10 ≤ 0
        tokenizer.encode.side_effect = lambda text, **kwargs: (
            list(range(5)) if text == " [...] " else list(range(100))
        )
        tokenizer.decode.return_value = "fallback"

        result, was_truncated = extract_hmt("word " * 50, tokenizer, max_tokens=8)

        assert was_truncated is True
        # decode called once with max(1, 8-8)=max(1,0)=1 token
        tokenizer.decode.assert_called_once()
        decoded_tokens = tokenizer.decode.call_args[0][0]
        assert len(decoded_tokens) == max(1, 8 - 8)

    def test_mid_segment_centered(self):
        """Middle segment is taken from the center of token stream."""
        n_tokens = 600
        sep_len = 3
        budget = 512 - 8 - 2 * sep_len
        head_budget = budget // 2
        tail_budget = budget // 4
        mid_budget = budget - head_budget - tail_budget
        expected_mid_start = (n_tokens - mid_budget) // 2

        tokenizer = MagicMock()
        captured = []

        def encode_side(text, **kwargs):
            if text == " [...] ":
                return list(range(sep_len))
            return list(range(n_tokens))

        def decode_side(tokens, **kwargs):
            captured.append(list(tokens))
            return f"seg"

        tokenizer.encode.side_effect = encode_side
        tokenizer.decode.side_effect = decode_side

        extract_hmt("word " * n_tokens, tokenizer, max_tokens=512)

        # captured[0]=head, captured[1]=mid, captured[2]=tail
        mid_tokens = captured[1]
        assert len(mid_tokens) == mid_budget
        assert mid_tokens[0] == expected_mid_start
