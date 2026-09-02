"""JobScheduler — APScheduler-based background job runner."""
from __future__ import annotations

import asyncio
import json
import logging
import os
import time
from datetime import datetime, timezone
from typing import Optional

from apscheduler.schedulers.asyncio import AsyncIOScheduler

from src.application.use_cases.process_content import ProcessContentUseCase
from src.application.use_cases.update_profile import UpdateProfileUseCase
from src.application.dto.process_content import ProcessContentCommand
from src.application.dto.update_profile import UpdateProfileCommand
from src.domain.interfaces.cache_client import CacheClient
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.recommendation_history_repository import RecommendationHistoryRepository
from src.infrastructure.metrics import rec_content_processing_duration_seconds

logger = logging.getLogger(__name__)

COLD_START_CACHE_KEY = "rec:cold-start:trending"
COLD_START_CACHE_TTL = 3600  # 1 hour
COLD_START_ITEM_LIMIT = 50


class JobScheduler:
    """Manages periodic background jobs using APScheduler's AsyncIOScheduler.

    Registers four periodic jobs:
    - Content processing job (every N minutes)
    - Profile update job (every N minutes)
    - Cold-start trending list refresh (every COLD_START_REFRESH_MINUTES minutes)
    - Recommendation history cleanup (every 24 hours)

    Each job invokes its corresponding use case or repository method.
    """

    def __init__(
        self,
        process_content_use_case: ProcessContentUseCase,
        update_profile_use_case: UpdateProfileUseCase,
        interval_minutes: int = 5,
    ) -> None:
        self._process_content_use_case = process_content_use_case
        self._update_profile_use_case = update_profile_use_case
        self._interval_minutes = interval_minutes
        self._scheduler = AsyncIOScheduler()

        # Optional cold-start dependencies (set via set_cold_start_dependencies)
        self._content_repo: Optional[ContentRepository] = None
        self._cache_client: Optional[CacheClient] = None

        # Optional history cleanup dependency (set via set_history_cleanup_dependencies)
        self._history_repo: Optional[RecommendationHistoryRepository] = None

    def set_cold_start_dependencies(
        self,
        content_repo: ContentRepository,
        cache_client: CacheClient,
    ) -> None:
        """Inject dependencies required for the cold-start refresh job."""
        self._content_repo = content_repo
        self._cache_client = cache_client

    def set_history_cleanup_dependencies(
        self,
        history_repo: RecommendationHistoryRepository,
    ) -> None:
        """Inject dependency required for the recommendation history cleanup job."""
        self._history_repo = history_repo

    def register_content_processing_job(self) -> None:
        """Register the content processing periodic job."""
        interval_seconds = int(os.environ.get("REC_CONTENT_JOB_INTERVAL_SECONDS", "300"))
        self._scheduler.add_job(
            self._run_content_processing,
            trigger="interval",
            seconds=interval_seconds,
            id="content_processing",
            replace_existing=True,
        )

    def register_profile_update_job(self) -> None:
        """Register the profile update periodic job."""
        self._scheduler.add_job(
            self._run_profile_update,
            trigger="interval",
            minutes=self._interval_minutes,
            id="profile_update",
            replace_existing=True,
        )

    def register_cold_start_refresh_job(self) -> None:
        """Register the cold-start trending list refresh job.

        Runs immediately on startup, then every COLD_START_REFRESH_MINUTES minutes.
        """
        refresh_minutes = int(os.environ.get("COLD_START_REFRESH_MINUTES", "60"))
        self._scheduler.add_job(
            self._run_cold_start_refresh,
            trigger="interval",
            minutes=refresh_minutes,
            id="cold_start_refresh",
            replace_existing=True,
            next_run_time=datetime.now(timezone.utc),  # run immediately on startup
        )

    def register_history_cleanup_job(self) -> None:
        """Register the recommendation history cleanup job (runs every 24 hours)."""
        self._scheduler.add_job(
            self._run_history_cleanup,
            trigger="interval",
            hours=24,
            id="history_cleanup",
            replace_existing=True,
        )

    def start(self) -> None:
        """Start the scheduler."""
        self._scheduler.start()

    def stop(self) -> None:
        """Shut down the scheduler cleanly."""
        self._scheduler.shutdown()

    async def _run_content_processing(self) -> None:
        """Job callback: invoke ProcessContentUseCase.

        Has a hard 5-minute timeout to prevent hanging forever and blocking
        subsequent APScheduler runs (max_instances=1).
        """
        job_name = "content_processing"
        job_timeout = int(os.environ.get("REC_CONTENT_JOB_TIMEOUT", "300"))
        batch_size = int(os.environ.get("REC_CONTENT_BATCH_SIZE", "20"))
        logger.info("job_started", extra={"job": job_name, "batch_size": batch_size})
        start = time.monotonic()
        try:
            async with asyncio.timeout(job_timeout):
                result = await self._process_content_use_case.execute(
                    ProcessContentCommand(batch_size=batch_size)
                )
            _elapsed = time.monotonic() - start
            duration_ms = int(_elapsed * 1000)
            rec_content_processing_duration_seconds.observe(_elapsed)
            logger.info(
                "job_completed",
                extra={
                    "job": job_name,
                    "duration_ms": duration_ms,
                    "processed": getattr(result, "processed_count", None),
                    "failed": None,
                },
            )
        except TimeoutError:
            duration_ms = int((time.monotonic() - start) * 1000)
            rec_content_processing_duration_seconds.observe(time.monotonic() - start)
            logger.error(
                "job_timeout",
                extra={"job": job_name, "duration_ms": duration_ms, "timeout_s": job_timeout},
            )
        except Exception as exc:
            duration_ms = int((time.monotonic() - start) * 1000)
            rec_content_processing_duration_seconds.observe(time.monotonic() - start)
            logger.exception(
                "job_failed",
                extra={"job": job_name, "duration_ms": duration_ms, "error": str(exc)},
            )

    async def _run_profile_update(self) -> None:
        """Job callback: invoke UpdateProfileUseCase."""
        job_name = "profile_update"
        logger.info("job_started", extra={"job": job_name})
        start = time.monotonic()
        try:
            result = await self._update_profile_use_case.execute(
                UpdateProfileCommand(user_id=None)
            )
            duration_ms = int((time.monotonic() - start) * 1000)
            logger.info(
                "job_completed",
                extra={
                    "job": job_name,
                    "duration_ms": duration_ms,
                    "processed": getattr(result, "events_processed", None),
                    "failed": None,
                },
            )
        except Exception as exc:
            duration_ms = int((time.monotonic() - start) * 1000)
            logger.exception(
                "job_failed",
                extra={"job": job_name, "duration_ms": duration_ms, "error": str(exc)},
            )

    async def _run_cold_start_refresh(self) -> None:
        """Job callback: refresh cold-start trending list in Redis.

        Queries the 50 most recent published content items and stores
        them as JSON in Redis key 'rec:cold-start:trending' with TTL 3600s.
        """
        job_name = "cold_start_refresh"
        logger.info("job_started", extra={"job": job_name})
        start = time.monotonic()
        try:
            if self._content_repo is None or self._cache_client is None:
                duration_ms = int((time.monotonic() - start) * 1000)
                logger.info(
                    "job_completed",
                    extra={"job": job_name, "duration_ms": duration_ms, "processed": 0, "failed": None},
                )
                return

            ids = await self._content_repo.get_recent_published_ids(limit=COLD_START_ITEM_LIMIT)
            generated_at = datetime.now(timezone.utc).isoformat()
            payload = json.dumps({
                "items": [str(item_id) for item_id in ids],
                "generated_at": generated_at,
            })
            await self._cache_client.set(COLD_START_CACHE_KEY, payload, ttl=COLD_START_CACHE_TTL)
            duration_ms = int((time.monotonic() - start) * 1000)
            logger.info(
                "job_completed",
                extra={
                    "job": job_name,
                    "duration_ms": duration_ms,
                    "processed": len(ids),
                    "failed": None,
                },
            )
        except Exception as exc:
            duration_ms = int((time.monotonic() - start) * 1000)
            logger.exception(
                "job_failed",
                extra={"job": job_name, "duration_ms": duration_ms, "error": str(exc)},
            )

    async def _run_history_cleanup(self) -> None:
        """Job callback: delete recommendation_history entries older than N days.

        Retention period is configurable via HISTORY_CLEANUP_DAYS env var (default: 30).
        """
        job_name = "history_cleanup"
        logger.info("job_started", extra={"job": job_name})
        start = time.monotonic()
        try:
            if self._history_repo is None:
                duration_ms = int((time.monotonic() - start) * 1000)
                logger.info(
                    "job_completed",
                    extra={"job": job_name, "duration_ms": duration_ms, "processed": 0, "failed": None},
                )
                return

            cleanup_days = int(os.environ.get("HISTORY_CLEANUP_DAYS", "30"))
            deleted = await self._history_repo.delete_older_than(days=cleanup_days)
            duration_ms = int((time.monotonic() - start) * 1000)
            logger.info(
                "job_completed",
                extra={
                    "job": job_name,
                    "duration_ms": duration_ms,
                    "processed": deleted,
                    "failed": None,
                },
            )
        except Exception as exc:
            duration_ms = int((time.monotonic() - start) * 1000)
            logger.exception(
                "job_failed",
                extra={"job": job_name, "duration_ms": duration_ms, "error": str(exc)},
            )
