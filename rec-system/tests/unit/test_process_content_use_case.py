"""Unit tests for ProcessContentUseCase.

Tests verify:
- NLP input text is built from title + content (title+content combined)
- channel_id is NOT passed to ContentFeatures constructor
- title and source_type ARE passed to ContentFeatures constructor
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import pytest

from src.application.dto.process_content import ProcessContentCommand
from src.application.use_cases.process_content import ProcessContentUseCase
from src.domain.entities.content_features import ContentFeatures


def _make_post(
    title: str | None = "Test Title",
    content: str = "Test content body",
    source_type: str = "telegram",
) -> ContentFeatures:
    return ContentFeatures(
        post_id=uuid.uuid4(),
        content=content,
        post_date=datetime.now(timezone.utc),
        title=title,
        source_type=source_type,
    )


def _make_use_case(captured_texts: list[str] | None = None) -> ProcessContentUseCase:
    """Create ProcessContentUseCase with mocked NLP adapters.

    If captured_texts is provided, classify/analyze/extract/encode calls
    record the text they received into it.
    """
    async def _classify(text: str):
        if captured_texts is not None:
            captured_texts.append(text)
        return [("технологии", 0.9), ("наука", 0.7), ("экономика", 0.5)]

    async def _analyze_sentiment(text: str):
        return ("POSITIVE", 0.85)

    async def _extract(text: str):
        return {"persons": [], "organizations": [], "locations": []}

    async def _encode(text: str):
        return [0.1] * 312

    def _analyze_text(text: str):
        return {
            "text_length": len(text),
            "word_count": 3,
            "reading_time": 0.01,
            "complexity": 0.5,
            "is_short_form": True,
            "is_long_form": False,
        }

    topic_classifier = MagicMock()
    topic_classifier.classify = AsyncMock(side_effect=_classify)

    sentiment_analyzer = MagicMock()
    sentiment_analyzer.analyze = AsyncMock(side_effect=_analyze_sentiment)

    entity_extractor = MagicMock()
    entity_extractor.extract = AsyncMock(side_effect=_extract)

    content_encoder = MagicMock()
    content_encoder.encode = AsyncMock(side_effect=_encode)
    # T5: expose mock tokenizer for H+M+T (short text, no truncation)
    mock_tokenizer = MagicMock()
    mock_tokenizer.encode.return_value = list(range(10))
    mock_tokenizer.decode.side_effect = lambda tokens, **kw: f"decoded_{len(tokens)}"
    content_encoder.tokenizer = mock_tokenizer

    text_analyzer = MagicMock()
    text_analyzer.analyze = MagicMock(side_effect=_analyze_text)

    content_repo = MagicMock()
    content_repo.get_unprocessed = AsyncMock(return_value=[])
    content_repo.save_features = AsyncMock()

    return ProcessContentUseCase(
        content_repo=content_repo,
        topic_classifier=topic_classifier,
        sentiment_analyzer=sentiment_analyzer,
        entity_extractor=entity_extractor,
        content_encoder=content_encoder,
        text_analyzer=text_analyzer,
    )


@pytest.mark.unit
@pytest.mark.asyncio
async def test_process_single_uses_title_and_content_as_nlp_text():
    """_process_single must pass prepend_title(title, content) as the NLP input text.

    T5: prepend_title uses '. ' separator when title has no terminal punctuation.
    """
    captured: list[str] = []
    uc = _make_use_case(captured_texts=captured)

    post = _make_post(title="Важный заголовок", content="Тело статьи про технологии")
    await uc._process_single(post)

    assert len(captured) == 1
    # T5: prepend_title adds '. ' (period + space) when title has no terminal punctuation
    expected_text = "Важный заголовок. Тело статьи про технологии"
    assert captured[0] == expected_text, (
        f"Expected NLP text '{expected_text}', got '{captured[0]}'"
    )


@pytest.mark.unit
@pytest.mark.asyncio
async def test_process_single_uses_content_only_when_title_is_none():
    """When title is None, only content should be used as NLP text (no leading space)."""
    captured: list[str] = []
    uc = _make_use_case(captured_texts=captured)

    post = _make_post(title=None, content="Тело без заголовка")
    await uc._process_single(post)

    assert len(captured) == 1
    assert captured[0] == "Тело без заголовка", (
        f"Expected content-only text, got '{captured[0]}'"
    )


@pytest.mark.unit
@pytest.mark.asyncio
async def test_process_single_result_includes_title():
    """_process_single must return ContentFeatures with title from post."""
    uc = _make_use_case()
    post = _make_post(title="My Title", content="Content here")
    result = await uc._process_single(post)
    assert result.title == "My Title"


@pytest.mark.unit
@pytest.mark.asyncio
async def test_process_single_result_includes_source_type():
    """_process_single must return ContentFeatures with source_type from post."""
    uc = _make_use_case()
    post = _make_post(source_type="rss")
    result = await uc._process_single(post)
    assert result.source_type == "rss"


@pytest.mark.unit
@pytest.mark.asyncio
async def test_process_single_result_has_no_channel_id():
    """ContentFeatures must not have a channel_id attribute (field was removed in Task 4)."""
    uc = _make_use_case()
    post = _make_post()
    result = await uc._process_single(post)
    assert not hasattr(result, "channel_id"), (
        "ContentFeatures must not have channel_id (removed in Task 4)"
    )
