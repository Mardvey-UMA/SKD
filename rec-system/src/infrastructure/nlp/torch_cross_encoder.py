"""TorchCrossEncoder — BGE cross-encoder reranking adapter.

Uses BAAI/bge-reranker-v2-m3 via FlagEmbedding for multilingual (Russian-capable)
cross-encoder reranking.

Key design decisions:
- Class-level singleton for the model: loaded lazily on first call, shared
  across all instances to avoid loading the same model multiple times.
- asyncio.Lock per instance guards the lazy-load path.
- GPU used when available; falls back to CPU with a warning (non-fatal).
- Text truncated to max_length chars before pair construction.
"""
from __future__ import annotations

import asyncio
import logging
from uuid import UUID

from src.domain.interfaces.relevance_reranker import RelevanceReranker

logger = logging.getLogger(__name__)


class TorchCrossEncoder(RelevanceReranker):
    """Cross-encoder reranker backed by BAAI/bge-reranker-v2-m3.

    The FlagReranker model is loaded lazily on the first call and kept as a
    class-level singleton so successive instances share the same weights.
    """

    # Class-level singleton — shared across all TorchCrossEncoder instances.
    _model_instance = None
    _class_lock = asyncio.Lock()

    def __init__(
        self,
        model_name: str = "BAAI/bge-reranker-v2-m3",
        batch_size: int = 16,
        max_length: int = 512,
        device: str = "auto",
    ) -> None:
        self._model_name = model_name
        self._batch_size = batch_size
        self._max_length = max_length
        self._device = device
        self._lock = asyncio.Lock()
        # _model is the instance-level reference to the class singleton
        self._model = None

    async def rerank(
        self,
        user_context: str,
        candidates: list,
        top_k: int,
    ) -> list[tuple[UUID, float]]:
        """Score candidates against user_context and return top_k sorted descending."""
        if not candidates:
            return []

        model = await self._get_model()

        pairs = self._build_pairs(user_context, candidates)
        scores: list[float] = await asyncio.to_thread(
            self._compute_scores_sync, model, pairs
        )

        ranked = sorted(
            zip([c.post_id for c in candidates], scores),
            key=lambda x: x[1],
            reverse=True,
        )
        return [(post_id, float(score)) for post_id, score in ranked[:top_k]]

    # ------------------------------------------------------------------
    # Internals
    # ------------------------------------------------------------------

    async def _get_model(self):
        """Lazy-load the class-level model singleton (thread-safe)."""
        if TorchCrossEncoder._model_instance is not None:
            self._model = TorchCrossEncoder._model_instance
            return self._model

        # Double-checked locking pattern
        async with TorchCrossEncoder._class_lock:
            if TorchCrossEncoder._model_instance is None:
                TorchCrossEncoder._model_instance = await asyncio.to_thread(
                    self._load_model_sync
                )
            self._model = TorchCrossEncoder._model_instance

        return self._model

    def _load_model_sync(self):
        """Synchronous model loading — called in thread pool."""
        from FlagEmbedding import FlagReranker  # type: ignore[import]

        device = self._resolve_device()
        logger.info("Loading cross-encoder model %s on %s", self._model_name, device)

        try:
            model = FlagReranker(
                self._model_name,
                use_fp16=(device == "cuda"),
                device=device,
            )
        except Exception as exc:
            logger.warning(
                "Failed to load model on %s (%s). Falling back to CPU.", device, exc
            )
            model = FlagReranker(self._model_name, use_fp16=False, device="cpu")

        return model

    def _resolve_device(self) -> str:
        """Resolve device: prefer GPU when 'auto', fall back to CPU with warning."""
        if self._device != "auto":
            return self._device

        try:
            import torch
            if torch.cuda.is_available():
                return "cuda"
        except ImportError:
            pass

        logger.warning(
            "GPU not available for TorchCrossEncoder — using CPU. "
            "Reranking will be slower."
        )
        return "cpu"

    def _build_pairs(self, user_context: str, candidates: list) -> list[tuple[str, str]]:
        """Build (query, document) pairs for the cross-encoder."""
        pairs = []
        for c in candidates:
            parts = []
            if c.title:
                parts.append(c.title)
            parts.append(c.content)
            doc_text = " ".join(parts)[: self._max_length]
            pairs.append((user_context, doc_text))
        return pairs

    @staticmethod
    def _compute_scores_sync(model, pairs: list[tuple[str, str]]) -> list[float]:
        """Synchronous score computation — runs in thread pool."""
        scores = model.compute_score(pairs)
        if isinstance(scores, (int, float)):
            scores = [scores]
        return [float(s) for s in scores]
