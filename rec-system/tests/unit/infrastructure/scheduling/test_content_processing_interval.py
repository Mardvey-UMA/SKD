"""Unit tests for content_processing job interval configurability — RED phase.

Tests verify that register_content_processing_job() reads REC_CONTENT_JOB_INTERVAL_SECONDS
from the environment and registers the APScheduler job with seconds=<value>.
"""
from __future__ import annotations

from datetime import timedelta
from unittest.mock import AsyncMock, MagicMock

import pytest
from apscheduler.schedulers.asyncio import AsyncIOScheduler

from src.infrastructure.scheduling.job_scheduler import JobScheduler


class TestContentProcessingJobInterval:
    @pytest.fixture
    def mock_process_use_case(self):
        uc = MagicMock()
        uc.execute = AsyncMock()
        return uc

    @pytest.fixture
    def mock_update_use_case(self):
        uc = MagicMock()
        uc.execute = AsyncMock()
        return uc

    def _make_scheduler(self, mock_process_use_case, mock_update_use_case) -> JobScheduler:
        sched = JobScheduler(
            process_content_use_case=mock_process_use_case,
            update_profile_use_case=mock_update_use_case,
        )
        sched._scheduler = AsyncIOScheduler()
        return sched

    @pytest.mark.unit
    def test_content_processing_default_interval_is_300_seconds(
        self, mock_process_use_case, mock_update_use_case, monkeypatch
    ):
        """When REC_CONTENT_JOB_INTERVAL_SECONDS is unset, interval must be 300 seconds."""
        monkeypatch.delenv("REC_CONTENT_JOB_INTERVAL_SECONDS", raising=False)
        sched = self._make_scheduler(mock_process_use_case, mock_update_use_case)

        sched.register_content_processing_job()

        job = sched._scheduler.get_job("content_processing")
        assert job is not None
        assert job.trigger.interval == timedelta(seconds=300)

    @pytest.mark.unit
    def test_content_processing_respects_env_var_30_seconds(
        self, mock_process_use_case, mock_update_use_case, monkeypatch
    ):
        """When REC_CONTENT_JOB_INTERVAL_SECONDS=30, interval must be 30 seconds."""
        monkeypatch.setenv("REC_CONTENT_JOB_INTERVAL_SECONDS", "30")
        sched = self._make_scheduler(mock_process_use_case, mock_update_use_case)

        sched.register_content_processing_job()

        job = sched._scheduler.get_job("content_processing")
        assert job is not None
        assert job.trigger.interval == timedelta(seconds=30)
