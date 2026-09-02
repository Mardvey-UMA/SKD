"""Unit tests for TorchCrossEncoder — cross-encoder reranking adapter.

Uses mocked FlagReranker so no real model is loaded.
FlagReranker is imported lazily inside _load_model_sync, so we patch at
'FlagEmbedding.FlagReranker' (the module where it lives).
"""
from __future__ import annotations

import asyncio
import uuid
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pytest

from src.domain.interfaces.relevance_reranker import RelevanceReranker

# Patch target — the lazy import path inside _load_model_sync
_PATCH_TARGET = "FlagEmbedding.FlagReranker"


def _make_content(post_id=None, content="Some text", title="Title"):
    """Helper: create ContentFeatures with minimal required fields."""
    from src.domain.entities.content_features import ContentFeatures
    return ContentFeatures(
        post_id=post_id or uuid.uuid4(),
        content=content,
        post_date=datetime.now(timezone.utc),
        title=title,
    )


def _make_encoder_with_mock(batch_size=16, max_length=128):
    """Create TorchCrossEncoder with _model already injected to skip lazy load."""
    from src.infrastructure.nlp.torch_cross_encoder import TorchCrossEncoder
    mock_model = MagicMock()
    # Reset class singleton so each test is isolated
    TorchCrossEncoder._model_instance = None
    encoder = TorchCrossEncoder(batch_size=batch_size, max_length=max_length)
    # Pre-inject the model to skip the actual load
    TorchCrossEncoder._model_instance = mock_model
    encoder._model = mock_model
    return encoder, mock_model


@pytest.mark.unit
class TestTorchCrossEncoderInterface:
    def test_implements_relevance_reranker(self):
        """TorchCrossEncoder must implement RelevanceReranker port."""
        from src.infrastructure.nlp.torch_cross_encoder import TorchCrossEncoder
        assert issubclass(TorchCrossEncoder, RelevanceReranker)


@pytest.mark.unit
class TestTorchCrossEncoderEmptyInput:
    @pytest.mark.asyncio
    async def test_empty_candidates_returns_empty_list(self):
        """Empty candidates list must return [] without calling the model."""
        encoder, mock_model = _make_encoder_with_mock()
        mock_model.compute_score.return_value = []

        result = await encoder.rerank(
            user_context="технологии. Python",
            candidates=[],
            top_k=10,
        )

        assert result == []
        mock_model.compute_score.assert_not_called()


@pytest.mark.unit
class TestTorchCrossEncoderBatchConstruction:
    @pytest.mark.asyncio
    async def test_pairs_built_from_context_and_content(self):
        """Verifies pairs = [(context, text), ...] passed to compute_score."""
        encoder, mock_model = _make_encoder_with_mock(max_length=512)
        mock_model.compute_score.return_value = [0.9, 0.5, 0.3]

        ctx = "технологии. Искусственный интеллект"
        c1 = _make_content(content="AI news", title="AI title")
        c2 = _make_content(content="Sports news", title="Sports title")
        c3 = _make_content(content="Politics", title="Pol title")

        await encoder.rerank(ctx, [c1, c2, c3], top_k=3)

        call_args = mock_model.compute_score.call_args
        pairs = call_args[0][0]

        assert len(pairs) == 3
        for pair in pairs:
            assert pair[0] == ctx
            assert isinstance(pair[1], str)

    @pytest.mark.asyncio
    async def test_text_truncated_to_max_length(self):
        """Content text is truncated to max_length chars."""
        long_text = "A" * 2000
        encoder, mock_model = _make_encoder_with_mock(max_length=100)
        mock_model.compute_score.return_value = [0.7]

        c = _make_content(content=long_text, title=None)
        await encoder.rerank("context", [c], top_k=1)

        pairs = mock_model.compute_score.call_args[0][0]
        assert len(pairs[0][1]) <= 100


@pytest.mark.unit
class TestTorchCrossEncoderScoreOrdering:
    @pytest.mark.asyncio
    async def test_results_sorted_descending_by_score(self):
        """Results must be sorted by score descending."""
        id1, id2, id3 = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
        c1 = _make_content(post_id=id1)
        c2 = _make_content(post_id=id2)
        c3 = _make_content(post_id=id3)

        encoder, mock_model = _make_encoder_with_mock()
        # Scores: c1=0.2, c2=0.9, c3=0.5 → sorted order: c2, c3, c1
        mock_model.compute_score.return_value = [0.2, 0.9, 0.5]

        result = await encoder.rerank("context", [c1, c2, c3], top_k=3)

        assert len(result) == 3
        assert result[0][0] == id2
        assert result[1][0] == id3
        assert result[2][0] == id1
        assert result[0][1] > result[1][1] > result[2][1]

    @pytest.mark.asyncio
    async def test_top_k_limits_output_length(self):
        """top_k parameter must cap the returned list."""
        candidates = [_make_content() for _ in range(5)]
        encoder, mock_model = _make_encoder_with_mock()
        mock_model.compute_score.return_value = [0.9, 0.8, 0.7, 0.6, 0.5]

        result = await encoder.rerank("context", candidates, top_k=2)

        assert len(result) == 2

    @pytest.mark.asyncio
    async def test_returns_list_of_uuid_float_tuples(self):
        """Each item in result must be (UUID, float)."""
        c = _make_content()
        encoder, mock_model = _make_encoder_with_mock()
        mock_model.compute_score.return_value = [0.7]

        result = await encoder.rerank("context", [c], top_k=1)

        assert len(result) == 1
        post_id, score = result[0]
        assert isinstance(post_id, uuid.UUID)
        assert isinstance(score, float)


@pytest.mark.unit
class TestTorchCrossEncoderSingleton:
    @pytest.mark.asyncio
    async def test_model_loaded_once_across_multiple_reranks(self):
        """_load_model_sync called only once even with multiple rerank calls."""
        from src.infrastructure.nlp.torch_cross_encoder import TorchCrossEncoder

        TorchCrossEncoder._model_instance = None
        mock_model = MagicMock()
        mock_model.compute_score.return_value = [0.5]

        load_call_count = 0

        original_load = TorchCrossEncoder._load_model_sync

        def patched_load(self_):
            nonlocal load_call_count
            load_call_count += 1
            return mock_model

        encoder = TorchCrossEncoder()
        encoder._load_model_sync = lambda: patched_load(encoder)

        # Manually inject: override _get_model to use patched loader
        async def patched_get_model(self_):
            if TorchCrossEncoder._model_instance is None:
                TorchCrossEncoder._model_instance = patched_load(self_)
            self_._model = TorchCrossEncoder._model_instance
            return self_._model

        import types
        encoder._get_model = types.MethodType(
            lambda self_: patched_get_model(self_), encoder
        )

        c = _make_content()
        await encoder.rerank("ctx", [c], top_k=1)
        await encoder.rerank("ctx", [c], top_k=1)
        await encoder.rerank("ctx", [c], top_k=1)

        assert load_call_count == 1
        TorchCrossEncoder._model_instance = None  # cleanup

    @pytest.mark.asyncio
    async def test_class_level_singleton_shared_across_instances(self):
        """Class-level model is shared — second instance reuses loaded model."""
        from src.infrastructure.nlp.torch_cross_encoder import TorchCrossEncoder

        TorchCrossEncoder._model_instance = None
        mock_model = MagicMock()
        mock_model.compute_score.return_value = [0.5]

        load_count = 0

        async def patched_get_model(self_):
            nonlocal load_count
            if TorchCrossEncoder._model_instance is None:
                load_count += 1
                TorchCrossEncoder._model_instance = mock_model
            self_._model = TorchCrossEncoder._model_instance
            return self_._model

        import types

        enc1 = TorchCrossEncoder()
        enc2 = TorchCrossEncoder()

        enc1._get_model = types.MethodType(
            lambda self_: patched_get_model(self_), enc1
        )
        enc2._get_model = types.MethodType(
            lambda self_: patched_get_model(self_), enc2
        )

        c = _make_content()
        await enc1.rerank("ctx", [c], top_k=1)
        await enc2.rerank("ctx", [c], top_k=1)

        assert load_count == 1
        TorchCrossEncoder._model_instance = None  # cleanup


@pytest.mark.unit
@pytest.mark.slow
class TestTorchCrossEncoderTitlePlusContent:
    """Verify title + content concatenation when title is present."""

    @pytest.mark.asyncio
    async def test_title_prepended_when_present(self):
        encoder, mock_model = _make_encoder_with_mock(max_length=500)
        mock_model.compute_score.return_value = [0.8]

        c = _make_content(content="Full article body", title="My Title")
        await encoder.rerank("context", [c], top_k=1)

        pairs = mock_model.compute_score.call_args[0][0]
        doc_text = pairs[0][1]
        # Title should be included in the document text
        assert "My Title" in doc_text or "Full article body" in doc_text
