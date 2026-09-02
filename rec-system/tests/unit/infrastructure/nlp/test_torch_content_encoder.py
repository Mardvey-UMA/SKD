"""Unit tests for TorchContentEncoder — uses SentenceTransformer (reference pipeline)."""
from __future__ import annotations

import pytest
import numpy as np
import math
from unittest.mock import MagicMock, patch

from src.domain.interfaces.content_encoder import ContentEncoder

EMBEDDING_DIM = 312


class TestTorchContentEncoder:
    @pytest.fixture
    def mock_st_model(self):
        model = MagicMock()
        # encode returns numpy array of shape (batch, 312), L2-normalized
        vec = np.random.randn(1, EMBEDDING_DIM).astype(np.float32)
        vec = vec / np.linalg.norm(vec, axis=1, keepdims=True)
        model.encode.return_value = vec
        return model

    @pytest.fixture
    def sut(self, mock_st_model):
        with patch("src.infrastructure.nlp.torch_content_encoder.SentenceTransformer") as mock_st_cls:
            mock_st_cls.return_value = mock_st_model
            from src.infrastructure.nlp.torch_content_encoder import TorchContentEncoder
            return TorchContentEncoder(model_name="test-model", batch_size=32)

    def test_implements_interface(self, sut):
        assert isinstance(sut, ContentEncoder)

    @pytest.mark.asyncio
    async def test_encode_returns_312_dim(self, sut):
        result = await sut.encode("Тестовый текст.")
        assert isinstance(result, list)
        assert len(result) == EMBEDDING_DIM

    @pytest.mark.asyncio
    async def test_output_is_l2_normalized(self, sut):
        result = await sut.encode("Текст.")
        norm = math.sqrt(sum(v ** 2 for v in result))
        assert abs(norm - 1.0) < 1e-4

    @pytest.mark.asyncio
    async def test_encode_batch(self, sut, mock_st_model):
        texts = ["Текст один.", "Текст два.", "Текст три."]
        batch_vec = np.random.randn(3, EMBEDDING_DIM).astype(np.float32)
        batch_vec = batch_vec / np.linalg.norm(batch_vec, axis=1, keepdims=True)
        mock_st_model.encode.return_value = batch_vec

        result = await sut.encode_batch(texts)
        assert isinstance(result, list)
        assert len(result) == 3
        for vec in result:
            assert len(vec) == EMBEDDING_DIM

    @pytest.mark.asyncio
    async def test_encode_calls_sentence_transformer(self, sut, mock_st_model):
        await sut.encode("Проверка вызова.")
        mock_st_model.encode.assert_called_once()
        call_kwargs = mock_st_model.encode.call_args[1]
        assert call_kwargs["normalize_embeddings"] is True
        assert call_kwargs["convert_to_numpy"] is True

    @pytest.mark.asyncio
    async def test_uses_correct_default_model(self):
        from src.infrastructure.nlp.torch_content_encoder import TorchContentEncoder
        import inspect
        sig = inspect.signature(TorchContentEncoder.__init__)
        default = sig.parameters["model_name"].default
        assert default == "cointegrated/rubert-tiny2"
