"""TorchContentEncoder — 312-dimensional L2-normalized embeddings.

Matches the reference pipeline: uses SentenceTransformer with normalize_embeddings=True
for correct pooling strategy (not just CLS token).
"""
from __future__ import annotations

import asyncio

import torch
from sentence_transformers import SentenceTransformer

from src.domain.interfaces.content_encoder import ContentEncoder


class TorchContentEncoder(ContentEncoder):
    """Dense embedding encoder using cointegrated/rubert-tiny2 via SentenceTransformer.

    Matches the reference pipeline exactly:
    - SentenceTransformer handles correct pooling strategy
    - normalize_embeddings=True for L2 normalization
    - 312-dimensional output vectors
    """

    def __init__(
        self,
        model_name: str = "cointegrated/rubert-tiny2",
        batch_size: int = 32,
        device: str = "cpu",
    ) -> None:
        self._batch_size = batch_size
        self._model = SentenceTransformer(model_name, device=device)

    @property
    def tokenizer(self):
        """Expose the underlying HuggingFace tokenizer for token-aware truncation (T5)."""
        return self._model.tokenizer

    async def encode(self, text: str) -> list[float]:
        """Encode a single text into a 312-dimensional L2-normalized vector."""
        embedding = await asyncio.to_thread(self._encode_sync, [text], 1)
        return embedding[0].tolist()

    async def encode_batch(self, texts: list[str]) -> list[list[float]]:
        """Encode multiple texts into 312-dimensional L2-normalized vectors."""
        embeddings = await asyncio.to_thread(self._encode_sync, texts, self._batch_size)
        return [emb.tolist() for emb in embeddings]

    @torch.no_grad()
    def _encode_sync(self, texts: list[str], batch_size: int):
        """Synchronous encoding — runs in thread pool."""
        return self._model.encode(
            texts,
            batch_size=batch_size,
            convert_to_numpy=True,
            normalize_embeddings=True,
        )
