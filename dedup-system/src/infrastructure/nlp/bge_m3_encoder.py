import logging
from typing import Optional

import numpy as np
import torch
from sentence_transformers import SentenceTransformer

from src.domain.interfaces.encoder_port import EncoderPort
from src.domain.services.text_window import extract_hmt

logger = logging.getLogger(__name__)


class BgeM3Encoder(EncoderPort):
    def __init__(
        self,
        model_name: str = "BAAI/bge-m3",
        device: Optional[str] = None,
        use_fp16: bool = True,
        max_tokens: int = 8192,
        max_text_bytes: int = 1_000_000,
        truncate_text_bytes: int = 500_000,
    ) -> None:
        if device is None:
            device = "cuda" if torch.cuda.is_available() else "cpu"
        if device == "cuda":
            import os
            fraction = float(os.environ.get("CUDA_MEM_FRACTION", "0.45"))
            torch.cuda.set_per_process_memory_fraction(fraction)
        self._device = device
        self._model = SentenceTransformer(model_name, device=device)
        if use_fp16 and device == "cuda":
            self._model.half()
        self._tokenizer = self._model.tokenizer
        self._max_tokens = max_tokens
        self._max_text_bytes = max_text_bytes
        self._truncate_text_bytes = truncate_text_bytes

    def encode_batch(self, texts: list[str], batch_size: int = 32) -> np.ndarray:
        prepared = []
        truncation_count = 0
        for text in texts:
            if len(text.encode("utf-8")) > self._max_text_bytes:
                logger.warning(
                    "clean_text exceeds %d bytes, hard-truncating to %d",
                    self._max_text_bytes,
                    self._truncate_text_bytes,
                )
                text = text[: self._truncate_text_bytes]
            extracted, truncated = extract_hmt(text, self._tokenizer, self._max_tokens)
            if truncated:
                truncation_count += 1
            prepared.append(extracted)

        if truncation_count > 0:
            logger.info(
                "BGE-M3 H+M+T applied to %d/%d texts",
                truncation_count,
                len(texts),
            )

        return self._model.encode(
            prepared,
            batch_size=batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
            normalize_embeddings=True,
        )
