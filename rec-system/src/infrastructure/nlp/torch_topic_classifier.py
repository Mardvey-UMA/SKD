"""TorchTopicClassifier — zero-shot NLI classification into 18 topics via pipeline API."""
from __future__ import annotations

import asyncio

import torch
from transformers import pipeline

from src.domain.interfaces.topic_classifier import TopicClassifier

TOPICS = [
    "политика", "экономика", "технологии", "наука", "спорт", "культура",
    "общество", "происшествия", "международные новости", "бизнес",
    "финансы", "образование", "здоровье", "развлечения", "криминал",
    "армия", "природа", "транспорт",
]

HYPOTHESIS_TEMPLATE = "Этот текст про {}."
TOP_K = 3


class TorchTopicClassifier(TopicClassifier):
    """Zero-shot NLI topic classifier using HuggingFace pipeline API.

    Model: cointegrated/rubert-base-cased-nli-threeway
    Pipeline handles NLI pair construction, entailment extraction, and ranking.
    """

    def __init__(
        self,
        model_name: str = "cointegrated/rubert-base-cased-nli-threeway",
        batch_size: int = 32,
        device: str = "cpu",
    ) -> None:
        self._batch_size = batch_size
        self._pipe = pipeline(
            "zero-shot-classification",
            model=model_name,
            device=device,
            torch_dtype=torch.bfloat16,
        )

    async def classify(self, text: str) -> list[tuple[str, float]]:
        """Classify text into top-3 topic categories with confidence scores."""
        if not text or not text.strip():
            return []
        result = await asyncio.to_thread(self._classify_sync, text)
        labels = result["labels"][:TOP_K]
        scores = result["scores"][:TOP_K]
        return [(label, round(float(score), 4)) for label, score in zip(labels, scores)]

    @torch.no_grad()
    def _classify_sync(self, text: str) -> dict:
        """Synchronous classification — runs in thread pool."""
        return self._pipe(
            text,
            candidate_labels=TOPICS,
            hypothesis_template=HYPOTHESIS_TEMPLATE,
            multi_label=False,
            truncation=True,
            max_length=512,
        )
