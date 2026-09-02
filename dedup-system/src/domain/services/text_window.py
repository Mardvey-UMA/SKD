"""Token-aware Head+Middle+Tail text window extraction."""
from __future__ import annotations

from typing import Protocol


class TokenizerProtocol(Protocol):
    """Minimal tokenizer interface required by extract_hmt."""

    def encode(self, text: str, add_special_tokens: bool = False) -> list[int]: ...
    def decode(self, tokens: list[int], skip_special_tokens: bool = True) -> str: ...


def extract_hmt(
    text: str,
    tokenizer: TokenizerProtocol,
    max_tokens: int,
    separator: str = " [...] ",
) -> tuple[str, bool]:
    """
    Extract head + middle + tail of text within max_tokens budget.
    Returns (extracted_text, was_truncated).

    Budget: max_tokens - 8 (special tokens reserve) - 2 * len(separator_tokens)
    Split: 50% head, 25% middle, 25% tail
    """
    if not text:
        return "", False

    tokens = tokenizer.encode(text, add_special_tokens=False)
    n = len(tokens)
    if n <= max_tokens - 8:
        return text, False

    sep_tokens = tokenizer.encode(separator, add_special_tokens=False)
    sep_len = len(sep_tokens)
    budget = max_tokens - 8 - 2 * sep_len
    if budget <= 0:
        # max_tokens too small — return only head
        return tokenizer.decode(tokens[: max(1, max_tokens - 8)], skip_special_tokens=True), True

    head_budget = budget // 2
    tail_budget = budget // 4
    mid_budget = budget - head_budget - tail_budget

    head = tokens[:head_budget]
    mid_start = (n - mid_budget) // 2
    mid = tokens[mid_start : mid_start + mid_budget]
    tail = tokens[n - tail_budget :]

    result = (
        tokenizer.decode(head, skip_special_tokens=True)
        + separator
        + tokenizer.decode(mid, skip_special_tokens=True)
        + separator
        + tokenizer.decode(tail, skip_special_tokens=True)
    )
    return result, True


def prepend_title(title: str | None, body: str) -> str:
    """Combine title with body for embedding input. Title is precious context."""
    if not title:
        return body
    title = title.strip()
    if not body:
        return title
    sep = ". " if not title.endswith((".", "!", "?", ":")) else " "
    return f"{title}{sep}{body}"
