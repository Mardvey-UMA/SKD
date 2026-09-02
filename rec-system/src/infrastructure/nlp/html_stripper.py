"""HTML stripping utility using stdlib only (re + html).

DEPRECATED: The platform now uses pre-cleaned ``clean_text`` from
``data_flow.raw_content.clean_text``, populated by content-parser-service
CleanContentJob (T1). rec-system reads ``clean_text`` directly via
``pg_content_repository._map_raw_to_entity()`` (T5).

This module is kept as a fallback for legacy data only. Do not use in new code.
"""
from __future__ import annotations

import html
import re
from typing import Optional

# Block-level tags that should become a space separator
_BLOCK_TAGS = re.compile(
    r"</?(?:p|div|br|h[1-6]|ul|ol|li|blockquote|pre|tr|td|th)\b[^>]*>",
    re.IGNORECASE,
)
# Any remaining HTML tag
_ANY_TAG = re.compile(r"<[^>]+>", re.DOTALL)
# Collapse whitespace
_WHITESPACE = re.compile(r"\s+")


def strip_html(text: Optional[str]) -> str:
    """Remove HTML tags from *text* and return plain text.

    - Block elements (p, div, br, h1-h6, li …) are replaced with a space.
    - HTML entities are decoded (``&amp;`` → ``&``, ``&lt;`` → ``<``, etc.).
    - Multiple whitespace / newlines are collapsed to a single space.
    - ``None`` and empty string both return ``""``.
    """
    if not text:
        return ""

    # Replace block tags with a space so words from adjacent blocks don't merge
    result = _BLOCK_TAGS.sub(" ", text)
    # Remove all remaining tags
    result = _ANY_TAG.sub("", result)
    # Decode HTML entities
    result = html.unescape(result)
    # Collapse whitespace
    result = _WHITESPACE.sub(" ", result).strip()
    return result
