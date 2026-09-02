"""TorchSentimentAnalyzer — 3-class sentiment classification via pipeline API."""
from __future__ import annotations

import asyncio

import torch
from transformers import pipeline

from src.domain.interfaces.sentiment_analyzer import SentimentAnalyzer

VALID_LABELS = {"POSITIVE", "NEGATIVE", "NEUTRAL"}


class TorchSentimentAnalyzer(SentimentAnalyzer):
    """Sentiment analyzer using HuggingFace pipeline API.

    Model: blanchefort/rubert-base-cased-sentiment
    Pipeline handles tokenization, inference, and label extraction from model config.
    """

    def __init__(
        self,
        model_name: str = "blanchefort/rubert-base-cased-sentiment",
        batch_size: int = 128,
        device: str = "cpu",
    ) -> None:
        self._batch_size = batch_size
        self._pipe = pipeline(
            "text-classification",
            model=model_name,
            device=device,
            torch_dtype=torch.bfloat16,
        )

    async def analyze(self, text: str) -> tuple[str, float]:
        """Classify the sentiment of the given text.

        Returns:
            Tuple of (sentiment_label, confidence_score) where label is
            POSITIVE, NEGATIVE, or NEUTRAL.
        """
        result = await asyncio.to_thread(self._analyze_sync, text)
        label = result[0]["label"].upper()
        score = round(float(result[0]["score"]), 4)

        if label not in VALID_LABELS:
            label = "NEUTRAL"

        return (label, score)

    @torch.no_grad()
    def _analyze_sync(self, text: str) -> list[dict]:
        """Synchronous analysis — runs in thread pool."""
        return self._pipe(text, truncation=True, max_length=512)
