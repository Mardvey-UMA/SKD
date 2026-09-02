"""Token-aware Head+Middle+Tail text window extraction.

Mirror of dedup-system/src/domain/services/text_window.py.
Both implementations MUST stay in sync — extract to shared library when stable.
"""
from __future__ import annotations

from typing import Protocol


class TokenizerProtocol(Protocol):
    def encode(self, text: str, add_special_tokens: bool = False) -> list[int]: ...
    def decode(self, tokens: list[int], skip_special_tokens: bool = True) -> str: ...


def extract_hmt(
    text: str,
    tokenizer: TokenizerProtocol,
    max_tokens: int,
    separator: str = " [...] ",
) -> tuple[str, bool]:
    """Extract a token-bounded window from text using Head+Middle+Tail strategy.

    If the text fits within max_tokens - 8 tokens, return it unchanged.
    Otherwise, split the budget 50% head / 25% middle / 25% tail and join
    with *separator*.

    Args:
        text: Input text (plain, no HTML).
        tokenizer: HuggingFace-compatible tokenizer with encode/decode.
        max_tokens: Token budget (typically 512 for rubert family).
        separator: Marker inserted between segments (default " [...] ").

    Returns:
        Tuple of (windowed_text, was_truncated).
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
        return tokenizer.decode(tokens[: max(1, max_tokens - 8)], skip_special_tokens=True), True

    head_budget = budget // 2
    tail_budget = budget // 4
    mid_budget = budget - head_budget - tail_budget

    head = tokens[:head_budget]
    mid_start = (n - mid_budget) // 2
    mid = tokens[mid_start : mid_start + mid_budget]
    tail = tokens[n - tail_budget :]

    return (
        tokenizer.decode(head, skip_special_tokens=True)
        + separator
        + tokenizer.decode(mid, skip_special_tokens=True)
        + separator
        + tokenizer.decode(tail, skip_special_tokens=True),
        True,
    )


def prepend_title(title: str | None, body: str) -> str:
    """Prepend *title* to *body* with an appropriate separator.

    - If title is None or blank, return body.
    - If body is empty, return title.
    - Otherwise join with ". " unless title already ends with sentence-terminal
      punctuation (., !, ?, :), in which case join with a single space.
    """
    if not title:
        return body
    title = title.strip()
    if not title:
        return body
    if not body:
        return title
    sep = ". " if not title.endswith((".", "!", "?", ":")) else " "
    return f"{title}{sep}{body}"
