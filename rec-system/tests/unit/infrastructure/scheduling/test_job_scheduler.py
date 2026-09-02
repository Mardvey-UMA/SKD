"""Unit tests for JobScheduler — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import MagicMock, AsyncMock, patch, call


class TestJobScheduler:
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

    @pytest.fixture
    def mock_scheduler_instance(self):
        sched = MagicMock()
        sched.add_job = MagicMock()
        sched.start = MagicMock()
        sched.shutdown = MagicMock()
        return sched

    @pytest.fixture
    def sut(self, mock_process_use_case, mock_update_use_case, mock_scheduler_instance):
        with patch("src.infrastructure.scheduling.job_scheduler.AsyncIOScheduler") as mock_sched_cls:
            mock_sched_cls.return_value = mock_scheduler_instance
            from src.infrastructure.scheduling.job_scheduler import JobScheduler
            scheduler = JobScheduler(
                process_content_use_case=mock_process_use_case,
                update_profile_use_case=mock_update_use_case,
                interval_minutes=5,
            )
            scheduler._scheduler = mock_scheduler_instance
            return scheduler

    def test_registers_content_processing_job(self, sut, mock_scheduler_instance):
        sut.register_content_processing_job()
        mock_scheduler_instance.add_job.assert_called()
        # Verify interval_minutes=5 is used
        call_kwargs = mock_scheduler_instance.add_job.call_args
        assert call_kwargs is not None

    def test_registers_profile_update_job(self, sut, mock_scheduler_instance):
        sut.register_profile_update_job()
        mock_scheduler_instance.add_job.assert_called()

    def test_start_begins_scheduler(self, sut, mock_scheduler_instance):
        sut.start()
        mock_scheduler_instance.start.assert_called_once()

    def test_stop_shuts_down_scheduler(self, sut, mock_scheduler_instance):
        sut.stop()
        mock_scheduler_instance.shutdown.assert_called_once()

    @pytest.mark.asyncio
    async def test_content_job_calls_use_case(self, sut, mock_process_use_case):
        await sut._run_content_processing()
        mock_process_use_case.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_profile_job_calls_use_case(self, sut, mock_update_use_case):
        await sut._run_profile_update()
        mock_update_use_case.execute.assert_called_once()
