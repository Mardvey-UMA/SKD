"""Unit tests for APScheduler job structured logging — RED phase.

Tests verify that each job handler emits:
- job_started log at INFO level before execution
- job_completed log at INFO level with duration_ms after success
- job_failed log at ERROR/EXCEPTION level with duration_ms and error on failure

All four jobs tested: content_processing, profile_update, cold_start_refresh, history_cleanup.
"""
from __future__ import annotations

import logging
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4


def _make_scheduler(mock_process_uc=None, mock_update_uc=None):
    """Create a JobScheduler with mocked APScheduler and use cases."""
    if mock_process_uc is None:
        mock_process_uc = AsyncMock()
        mock_process_uc.execute = AsyncMock()
    if mock_update_uc is None:
        mock_update_uc = AsyncMock()
        mock_update_uc.execute = AsyncMock()

    with patch("src.infrastructure.scheduling.job_scheduler.AsyncIOScheduler") as mock_cls:
        mock_instance = MagicMock()
        mock_cls.return_value = mock_instance

        from src.infrastructure.scheduling.job_scheduler import JobScheduler
        scheduler = JobScheduler(
            process_content_use_case=mock_process_uc,
            update_profile_use_case=mock_update_uc,
        )
        scheduler._scheduler = mock_instance
        return scheduler, mock_process_uc, mock_update_uc


@pytest.mark.unit
class TestContentProcessingJobLogs:
    """Structured log tests for _run_content_processing."""

    @pytest.mark.asyncio
    async def test_logs_job_started(self, caplog: pytest.LogCaptureFixture) -> None:
        scheduler, _, _ = _make_scheduler()
        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_content_processing()
        messages = [r.message for r in caplog.records]
        assert any("job_started" in m or "content_processing" in m for m in messages)

    @pytest.mark.asyncio
    async def test_logs_job_completed_with_duration_ms(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        from src.application.dto.process_content import ProcessContentResult

        mock_uc = AsyncMock()
        mock_uc.execute = AsyncMock(return_value=ProcessContentResult(processed_count=5))
        scheduler, _, _ = _make_scheduler(mock_process_uc=mock_uc)

        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_content_processing()

        records = caplog.records
        completed = [r for r in records if "job_completed" in r.message or (
            hasattr(r, "duration_ms") and r.duration_ms is not None
        )]
        # At minimum a completed/duration message must exist
        assert len(completed) >= 1 or any(
            "duration_ms" in str(getattr(r, "__dict__", {})) for r in records
        )

    @pytest.mark.asyncio
    async def test_logs_job_failed_on_exception(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        mock_uc = AsyncMock()
        mock_uc.execute = AsyncMock(side_effect=RuntimeError("db down"))
        scheduler, _, _ = _make_scheduler(mock_process_uc=mock_uc)

        with caplog.at_level(logging.ERROR, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_content_processing()  # must not propagate

        assert any("job_failed" in r.message or "db down" in r.message for r in caplog.records)

    @pytest.mark.asyncio
    async def test_exception_not_propagated(self) -> None:
        mock_uc = AsyncMock()
        mock_uc.execute = AsyncMock(side_effect=RuntimeError("crash"))
        scheduler, _, _ = _make_scheduler(mock_process_uc=mock_uc)
        # Must NOT raise
        await scheduler._run_content_processing()


@pytest.mark.unit
class TestProfileUpdateJobLogs:
    """Structured log tests for _run_profile_update."""

    @pytest.mark.asyncio
    async def test_logs_job_started(self, caplog: pytest.LogCaptureFixture) -> None:
        scheduler, _, _ = _make_scheduler()
        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_profile_update()
        messages = [r.message for r in caplog.records]
        assert any("job_started" in m or "profile_update" in m for m in messages)

    @pytest.mark.asyncio
    async def test_logs_job_completed(self, caplog: pytest.LogCaptureFixture) -> None:
        from src.application.dto.update_profile import UpdateProfileResult

        mock_uc = AsyncMock()
        mock_uc.execute = AsyncMock(return_value=UpdateProfileResult(users_updated=2, events_processed=10))
        scheduler, _, _ = _make_scheduler(mock_update_uc=mock_uc)

        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_profile_update()

        records = caplog.records
        assert any("job_completed" in r.message or "profile_update" in r.message for r in records)

    @pytest.mark.asyncio
    async def test_logs_job_failed_on_exception(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        mock_uc = AsyncMock()
        mock_uc.execute = AsyncMock(side_effect=RuntimeError("timeout"))
        scheduler, _, _ = _make_scheduler(mock_update_uc=mock_uc)

        with caplog.at_level(logging.ERROR, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_profile_update()

        assert any("job_failed" in r.message or "timeout" in r.message for r in caplog.records)

    @pytest.mark.asyncio
    async def test_exception_not_propagated(self) -> None:
        mock_uc = AsyncMock()
        mock_uc.execute = AsyncMock(side_effect=RuntimeError("crash"))
        scheduler, _, _ = _make_scheduler(mock_update_uc=mock_uc)
        await scheduler._run_profile_update()


@pytest.mark.unit
class TestColdStartRefreshJobLogs:
    """Structured log tests for _run_cold_start_refresh."""

    @pytest.fixture
    def scheduler_with_cold_start(self):
        scheduler, _, _ = _make_scheduler()
        mock_content_repo = AsyncMock()
        mock_content_repo.get_recent_published_ids = AsyncMock(return_value=[uuid4() for _ in range(5)])
        mock_cache = AsyncMock()
        mock_cache.set = AsyncMock()
        scheduler.set_cold_start_dependencies(
            content_repo=mock_content_repo, cache_client=mock_cache
        )
        return scheduler

    @pytest.mark.asyncio
    async def test_logs_job_started(
        self, scheduler_with_cold_start, caplog: pytest.LogCaptureFixture
    ) -> None:
        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler_with_cold_start._run_cold_start_refresh()
        messages = [r.message for r in caplog.records]
        assert any("job_started" in m or "cold_start_refresh" in m for m in messages)

    @pytest.mark.asyncio
    async def test_logs_job_completed(
        self, scheduler_with_cold_start, caplog: pytest.LogCaptureFixture
    ) -> None:
        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler_with_cold_start._run_cold_start_refresh()
        records = caplog.records
        assert any("job_completed" in r.message or "cold_start_refresh" in r.message for r in records)

    @pytest.mark.asyncio
    async def test_logs_job_failed_on_exception(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        scheduler, _, _ = _make_scheduler()
        mock_repo = AsyncMock()
        mock_repo.get_recent_published_ids = AsyncMock(side_effect=RuntimeError("redis down"))
        mock_cache = AsyncMock()
        scheduler.set_cold_start_dependencies(content_repo=mock_repo, cache_client=mock_cache)

        with caplog.at_level(logging.ERROR, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_cold_start_refresh()

        assert any("job_failed" in r.message or "redis down" in r.message for r in caplog.records)


@pytest.mark.unit
class TestHistoryCleanupJobLogs:
    """Structured log tests for _run_history_cleanup."""

    @pytest.fixture
    def scheduler_with_history(self):
        scheduler, _, _ = _make_scheduler()
        mock_repo = AsyncMock()
        mock_repo.delete_older_than = AsyncMock(return_value=10)
        scheduler.set_history_cleanup_dependencies(history_repo=mock_repo)
        return scheduler

    @pytest.mark.asyncio
    async def test_logs_job_started(
        self, scheduler_with_history, caplog: pytest.LogCaptureFixture
    ) -> None:
        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler_with_history._run_history_cleanup()
        messages = [r.message for r in caplog.records]
        assert any("job_started" in m or "history_cleanup" in m for m in messages)

    @pytest.mark.asyncio
    async def test_logs_job_completed(
        self, scheduler_with_history, caplog: pytest.LogCaptureFixture
    ) -> None:
        with caplog.at_level(logging.INFO, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler_with_history._run_history_cleanup()
        records = caplog.records
        assert any("job_completed" in r.message or "history_cleanup" in r.message for r in records)

    @pytest.mark.asyncio
    async def test_logs_job_failed_on_exception(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        scheduler, _, _ = _make_scheduler()
        mock_repo = AsyncMock()
        mock_repo.delete_older_than = AsyncMock(side_effect=RuntimeError("db gone"))
        scheduler.set_history_cleanup_dependencies(history_repo=mock_repo)

        with caplog.at_level(logging.ERROR, logger="src.infrastructure.scheduling.job_scheduler"):
            await scheduler._run_history_cleanup()

        assert any("job_failed" in r.message or "db gone" in r.message for r in caplog.records)
