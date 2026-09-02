"""HotContentEncoder — runs the NLP pipeline on a single post on arrival."""
from __future__ import annotations

import logging
import time
from uuid import UUID

logger = logging.getLogger(__name__)


class HotContentEncoder:
    """Processes a single incoming post through the NLP pipeline immediately.

    Delegates to ProcessContentUseCase.process_single_by_id which loads the
    raw_content row, runs all 5 NLP operations, UPSERTs posts_features, and
    marks is_processed_by_rec=true — reusing the exact same code path as the
    background content_processing scheduler.
    """

    def __init__(self, process_content_use_case) -> None:
        self._use_case = process_content_use_case

    async def process(self, content_id: UUID) -> None:
        """Encode a single post by content_id and log end-to-end latency."""
        start = time.monotonic()
        found = await self._use_case.process_single_by_id(content_id)
        if not found:
            logger.warning(
                "hot_content_not_found: raw_content row missing for content_id=%s",
                content_id,
            )
            return
        latency_ms = int((time.monotonic() - start) * 1000)
        logger.info(
            "hot_content_processed: content_id=%s",
            content_id,
            extra={"content_id": str(content_id), "latency_ms": latency_ms},
        )
