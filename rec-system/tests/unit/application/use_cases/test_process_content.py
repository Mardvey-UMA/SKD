"""Tests for ProcessContentUseCase."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, call
from uuid import uuid4

from src.application.dto.process_content import ProcessContentCommand, ProcessContentResult
from src.application.use_cases.process_content import ProcessContentUseCase
from src.domain.entities.content_features import ContentFeatures


def make_empty_content(post_id=None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="",
        title=None,
        post_date=datetime.now(timezone.utc),
        processed_at=None,
    )


def make_whitespace_content(post_id=None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="   \n  ",
        title=None,
        post_date=datetime.now(timezone.utc),
        processed_at=None,
    )


def make_unprocessed_content(post_id=None) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="Some text about technology and science.",
        post_date=datetime.now(timezone.utc),
        processed_at=None,
    )


@pytest.fixture
def mocks():
    content_items = [make_unprocessed_content() for _ in range(3)]

    content_repo = AsyncMock()
    content_repo.get_unprocessed.return_value = content_items
    content_repo.save_features.return_value = None

    topic_classifier = AsyncMock()
    topic_classifier.classify.return_value = [("technology", 0.9), ("science", 0.7), ("health", 0.5)]

    sentiment_analyzer = AsyncMock()
    sentiment_analyzer.analyze.return_value = ("positive", 0.85)

    entity_extractor = AsyncMock()
    entity_extractor.extract.return_value = {
        "persons": ["Alice"],
        "organizations": ["OpenAI"],
        "locations": ["San Francisco"],
    }

    content_encoder = AsyncMock()
    content_encoder.encode.return_value = [0.1, 0.2, 0.3, 0.4]

    text_analyzer = MagicMock()
    text_analyzer.analyze.return_value = {
        "word_count": 10,
        "text_length": 50.0,
        "reading_time": 0.5,
        "complexity": 0.3,
        "is_long_form": False,
        "is_short_form": True,
    }

    return {
        "content_repo": content_repo,
        "topic_classifier": topic_classifier,
        "sentiment_analyzer": sentiment_analyzer,
        "entity_extractor": entity_extractor,
        "content_encoder": content_encoder,
        "text_analyzer": text_analyzer,
        "content_items": content_items,
    }


@pytest.fixture
def sut(mocks):
    # Attach a mock tokenizer to content_encoder so extract_hmt can use it.
    # Returns short token lists (< 504) so normal tests don't trigger H+M+T.
    mock_tokenizer = MagicMock()
    mock_tokenizer.encode.return_value = list(range(10))
    mock_tokenizer.decode.side_effect = lambda tokens, **kw: f"decoded_{len(tokens)}"
    mocks["content_encoder"].tokenizer = mock_tokenizer
    mocks["mock_tokenizer"] = mock_tokenizer

    return ProcessContentUseCase(
        content_repo=mocks["content_repo"],
        topic_classifier=mocks["topic_classifier"],
        sentiment_analyzer=mocks["sentiment_analyzer"],
        entity_extractor=mocks["entity_extractor"],
        content_encoder=mocks["content_encoder"],
        text_analyzer=mocks["text_analyzer"],
    )


@pytest.mark.unit
class TestProcessContentUseCase:
    @pytest.mark.asyncio
    async def test_execute_fetches_unprocessed(self, sut, mocks):
        """content_repo.get_unprocessed is called with the batch_size."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        mocks["content_repo"].get_unprocessed.assert_called_once_with(50)

    @pytest.mark.asyncio
    async def test_runs_topic_classification(self, sut, mocks):
        """TopicClassifier.classify is called for each post."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        assert mocks["topic_classifier"].classify.call_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_runs_sentiment_analysis(self, sut, mocks):
        """SentimentAnalyzer.analyze is called for each post."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        assert mocks["sentiment_analyzer"].analyze.call_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_runs_entity_extraction(self, sut, mocks):
        """EntityExtractor.extract is called for each post."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        assert mocks["entity_extractor"].extract.call_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_runs_embedding_generation(self, sut, mocks):
        """ContentEncoder.encode is called for each post."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        assert mocks["content_encoder"].encode.call_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_runs_text_analysis(self, sut, mocks):
        """TextAnalyzer.analyze is called for each post."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        assert mocks["text_analyzer"].analyze.call_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_saves_features(self, sut, mocks):
        """content_repo.save_features is called with a ContentFeatures for each post."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        assert mocks["content_repo"].save_features.call_count == len(mocks["content_items"])
        saved_args = [call[0][0] for call in mocks["content_repo"].save_features.call_args_list]
        for features in saved_args:
            assert isinstance(features, ContentFeatures)

    @pytest.mark.asyncio
    async def test_marks_posts_processed(self, sut, mocks):
        """After saving, processed_at is set on saved features."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        saved_args = [call[0][0] for call in mocks["content_repo"].save_features.call_args_list]
        for features in saved_args:
            assert features.processed_at is not None

    @pytest.mark.asyncio
    async def test_atomic_save_and_mark(self, sut, mocks):
        """Features are saved (save_features called) for each post — atomicity via same call."""
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)
        # save_features is the single call that persists features + marks processed
        assert mocks["content_repo"].save_features.call_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_returns_processed_count(self, sut, mocks):
        """Result.processed_count matches number of processed posts."""
        command = ProcessContentCommand(batch_size=50)
        result = await sut.execute(command)
        assert isinstance(result, ProcessContentResult)
        assert result.processed_count == len(mocks["content_items"])

    @pytest.mark.asyncio
    async def test_no_unprocessed_posts(self, sut, mocks):
        """When no unprocessed posts, returns 0 and no NLP calls are made."""
        mocks["content_repo"].get_unprocessed.return_value = []
        command = ProcessContentCommand(batch_size=50)
        result = await sut.execute(command)
        assert result.processed_count == 0
        mocks["topic_classifier"].classify.assert_not_called()
        mocks["sentiment_analyzer"].analyze.assert_not_called()
        mocks["entity_extractor"].extract.assert_not_called()
        mocks["content_encoder"].encode.assert_not_called()
        mocks["text_analyzer"].analyze.assert_not_called()

    @pytest.mark.asyncio
    async def test_empty_text_post_returns_default_features(self, sut, mocks):
        """Empty-text post (title=None, content='') gets default features; NLP is skipped."""
        empty_post = make_empty_content()
        mocks["content_repo"].get_unprocessed.return_value = [empty_post]
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)

        mocks["content_repo"].save_features.assert_called_once()
        saved: ContentFeatures = mocks["content_repo"].save_features.call_args[0][0]
        assert saved.topic_1 is None
        assert saved.topic_2 is None
        assert saved.topic_3 is None
        assert saved.sentiment is None
        assert saved.sentiment_score is None
        assert saved.entities_persons == []
        assert saved.entities_organizations == []
        assert saved.entities_locations == []
        assert saved.embedding is None
        assert saved.processed_at is not None

        mocks["topic_classifier"].classify.assert_not_called()
        mocks["sentiment_analyzer"].analyze.assert_not_called()
        mocks["entity_extractor"].extract.assert_not_called()
        mocks["content_encoder"].encode.assert_not_called()
        mocks["text_analyzer"].analyze.assert_called_once()

    @pytest.mark.asyncio
    async def test_whitespace_only_post_treated_as_empty(self, sut, mocks):
        """Whitespace-only content is treated the same as empty — NLP skipped."""
        ws_post = make_whitespace_content()
        mocks["content_repo"].get_unprocessed.return_value = [ws_post]
        command = ProcessContentCommand(batch_size=50)
        await sut.execute(command)

        mocks["content_repo"].save_features.assert_called_once()
        saved: ContentFeatures = mocks["content_repo"].save_features.call_args[0][0]
        assert saved.topic_1 is None
        assert saved.embedding is None
        assert saved.processed_at is not None

        mocks["topic_classifier"].classify.assert_not_called()
        mocks["sentiment_analyzer"].analyze.assert_not_called()
        mocks["entity_extractor"].extract.assert_not_called()
        mocks["content_encoder"].encode.assert_not_called()

    @pytest.mark.asyncio
    async def test_batch_one_failure_others_succeed(self, sut, mocks):
        """A single NLP crash in the batch does not prevent other posts from being saved."""
        posts = [make_unprocessed_content() for _ in range(3)]
        mocks["content_repo"].get_unprocessed.return_value = posts

        valid_result = [("технологии", 0.9), ("наука", 0.7), ("экономика", 0.5)]
        mocks["topic_classifier"].classify.side_effect = [
            valid_result,
            RuntimeError("NLP crash"),
            valid_result,
        ]

        command = ProcessContentCommand(batch_size=50)
        result = await sut.execute(command)

        assert result.processed_count == 3
        assert mocks["content_repo"].save_features.call_count == 3

        saved_list = [c[0][0] for c in mocks["content_repo"].save_features.call_args_list]
        assert saved_list[0].topic_1 == "технологии"
        assert saved_list[1].topic_1 is None  # fallback
        assert saved_list[2].topic_1 == "технологии"

    @pytest.mark.asyncio
    async def test_batch_with_mixed_empty_and_valid(self, sut, mocks):
        """Batch with valid, empty, valid posts — NLP called 2 times, all 3 saved."""
        valid1 = make_unprocessed_content()
        empty = make_empty_content()
        valid2 = make_unprocessed_content()
        mocks["content_repo"].get_unprocessed.return_value = [valid1, empty, valid2]

        command = ProcessContentCommand(batch_size=50)
        result = await sut.execute(command)

        assert result.processed_count == 3
        assert mocks["topic_classifier"].classify.call_count == 2
        assert mocks["content_repo"].save_features.call_count == 3

        saved_list = [c[0][0] for c in mocks["content_repo"].save_features.call_args_list]
        assert saved_list[1].topic_1 is None
        assert saved_list[1].embedding is None

    @pytest.mark.asyncio
    async def test_nlp_error_does_not_block_batch(self, sut, mocks):
        """All NLP services raising exceptions still saves all posts with fallback features."""
        posts = [make_unprocessed_content() for _ in range(3)]
        mocks["content_repo"].get_unprocessed.return_value = posts

        mocks["topic_classifier"].classify.side_effect = RuntimeError("crash")
        mocks["sentiment_analyzer"].analyze.side_effect = RuntimeError("crash")
        mocks["entity_extractor"].extract.side_effect = RuntimeError("crash")
        mocks["content_encoder"].encode.side_effect = RuntimeError("crash")

        command = ProcessContentCommand(batch_size=50)
        result = await sut.execute(command)

        assert result.processed_count == 3
        assert mocks["content_repo"].save_features.call_count == 3

    @pytest.mark.asyncio
    async def test_hmt_applied_when_tokenizer_returns_long_token_list(self, mocks):
        """When encoder.tokenizer.encode() returns > 504 tokens, H+M+T truncation is applied."""
        # Tokenizer returns 600 tokens — triggers H+M+T
        mock_tokenizer = MagicMock()
        mock_tokenizer.encode.side_effect = lambda text, **kw: (
            list(range(3)) if text == " [...] " else list(range(600))
        )
        mock_tokenizer.decode.side_effect = lambda tokens, **kw: f"seg_{len(tokens)}"
        mocks["content_encoder"].tokenizer = mock_tokenizer

        sut_long = ProcessContentUseCase(
            content_repo=mocks["content_repo"],
            topic_classifier=mocks["topic_classifier"],
            sentiment_analyzer=mocks["sentiment_analyzer"],
            entity_extractor=mocks["entity_extractor"],
            content_encoder=mocks["content_encoder"],
            text_analyzer=mocks["text_analyzer"],
            max_nlp_tokens=512,
        )

        post = make_unprocessed_content()
        mocks["content_repo"].get_unprocessed.return_value = [post]
        command = ProcessContentCommand(batch_size=1)
        result = await sut_long.execute(command)

        assert result.processed_count == 1
        # encoder.encode is called with the H+M+T truncated text (contains " [...] ")
        encode_call_arg = mocks["content_encoder"].encode.call_args[0][0]
        assert " [...] " in encode_call_arg

    @pytest.mark.asyncio
    async def test_entity_extractor_uses_body_not_nlp_text(self, mocks):
        """entity_extractor.extract() receives the full body, not the H+M+T text."""
        # Tokenizer returns long list to trigger H+M+T
        mock_tokenizer = MagicMock()
        mock_tokenizer.encode.side_effect = lambda text, **kw: (
            list(range(3)) if text == " [...] " else list(range(600))
        )
        mock_tokenizer.decode.side_effect = lambda tokens, **kw: f"seg"
        mocks["content_encoder"].tokenizer = mock_tokenizer

        sut_long = ProcessContentUseCase(
            content_repo=mocks["content_repo"],
            topic_classifier=mocks["topic_classifier"],
            sentiment_analyzer=mocks["sentiment_analyzer"],
            entity_extractor=mocks["entity_extractor"],
            content_encoder=mocks["content_encoder"],
            text_analyzer=mocks["text_analyzer"],
        )

        post = make_unprocessed_content()  # content = "Some text about technology and science."
        mocks["content_repo"].get_unprocessed.return_value = [post]

        await sut_long.execute(ProcessContentCommand(batch_size=1))

        # entity_extractor must receive body (post.content), not the truncated nlp_text
        extract_call_arg = mocks["entity_extractor"].extract.call_args[0][0]
        assert " [...] " not in extract_call_arg, (
            "entity_extractor must receive body without H+M+T separator"
        )

    @pytest.mark.asyncio
    async def test_no_magic_number_2000_in_process_content(self, sut, mocks):
        """Verify text[:2000] hack is not applied — post with 3000 chars is not silently truncated."""
        long_content = "а " * 2000  # 4000 chars > 2000, was previously truncated
        post = ContentFeatures(
            post_id=uuid4(),
            content=long_content,
            title="Длинная статья",
            post_date=datetime.now(timezone.utc),
            processed_at=None,
        )
        mocks["content_repo"].get_unprocessed.return_value = [post]

        await sut.execute(ProcessContentCommand(batch_size=1))

        # encoder.encode must be called (text is not empty)
        assert mocks["content_encoder"].encode.call_count == 1
        # The arg passed to encoder should NOT be silently truncated to 2000 chars
        # (tokenizer returns 10 tokens, so no H+M+T; full text passed)
        encode_arg = mocks["content_encoder"].encode.call_args[0][0]
        # full_text includes title + body via prepend_title
        assert "Длинная статья" in encode_arg
        assert "а " in encode_arg

    @pytest.mark.asyncio
    async def test_oversized_bytes_triggers_byte_guard(self, mocks):
        """Body exceeding max_text_bytes is truncated to truncate_text_bytes before H+M+T."""
        # Create a body that exceeds the tiny limit
        body = "x" * 200
        post = ContentFeatures(
            post_id=uuid4(),
            content=body,
            title=None,
            post_date=datetime.now(timezone.utc),
            processed_at=None,
        )

        mock_tokenizer = MagicMock()
        mock_tokenizer.encode.return_value = list(range(10))
        mock_tokenizer.decode.side_effect = lambda tokens, **kw: f"decoded"
        mocks["content_encoder"].tokenizer = mock_tokenizer

        sut_small = ProcessContentUseCase(
            content_repo=mocks["content_repo"],
            topic_classifier=mocks["topic_classifier"],
            sentiment_analyzer=mocks["sentiment_analyzer"],
            entity_extractor=mocks["entity_extractor"],
            content_encoder=mocks["content_encoder"],
            text_analyzer=mocks["text_analyzer"],
            max_nlp_tokens=512,
            max_text_bytes=100,       # tiny limit — body of 200 chars exceeds it
            truncate_text_bytes=50,
        )

        mocks["content_repo"].get_unprocessed.return_value = [post]
        result = await sut_small.execute(ProcessContentCommand(batch_size=1))

        assert result.processed_count == 1
        # encoder.encode called with truncated body (≤ 50 bytes)
        encode_arg = mocks["content_encoder"].encode.call_args[0][0]
        assert len(encode_arg.encode("utf-8")) <= 50
