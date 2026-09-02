"""Unit tests for cold-start refresh and history cleanup APScheduler jobs — RED phase."""
from __future__ import annotations

import json
import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch, call
from uuid import uuid4


class TestColdStartRefreshJob:
    """Tests for the cold-start trending list refresh job."""

    @pytest.fixture
    def mock_cache_client(self):
        cache = AsyncMock()
        cache.set = AsyncMock()
        cache.get = AsyncMock(return_value=None)
        return cache

    @pytest.fixture
    def mock_content_repo(self):
        repo = AsyncMock()
        repo.get_recent_published_ids = AsyncMock(return_value=[uuid4() for _ in range(10)])
        return repo

    @pytest.fixture
    def scheduler_with_cold_start(self, mock_content_repo, mock_cache_client):
        """Create a JobScheduler instance with cold-start dependencies."""
        with patch("src.infrastructure.scheduling.job_scheduler.AsyncIOScheduler") as mock_sched_cls:
            mock_sched_instance = MagicMock()
            mock_sched_cls.return_value = mock_sched_instance

            from src.infrastructure.scheduling.job_scheduler import JobScheduler
            mock_process_uc = AsyncMock()
            mock_update_uc = AsyncMock()
            scheduler = JobScheduler(
                process_content_use_case=mock_process_uc,
                update_profile_use_case=mock_update_uc,
            )
            scheduler._scheduler = mock_sched_instance
            scheduler.set_cold_start_dependencies(
                content_repo=mock_content_repo,
                cache_client=mock_cache_client,
            )
            return scheduler, mock_sched_instance

    def test_register_cold_start_refresh_job_method_exists(self, scheduler_with_cold_start):
        scheduler, _ = scheduler_with_cold_start
        assert hasattr(scheduler, "register_cold_start_refresh_job")

    def test_register_cold_start_refresh_job_calls_add_job(self, scheduler_with_cold_start):
        scheduler, mock_sched_instance = scheduler_with_cold_start
        scheduler.register_cold_start_refresh_job()
        mock_sched_instance.add_job.assert_called()

    @pytest.mark.asyncio
    async def test_cold_start_job_writes_json_to_redis(self, scheduler_with_cold_start, mock_content_repo, mock_cache_client):
        scheduler, _ = scheduler_with_cold_start
        await scheduler._run_cold_start_refresh()
        mock_cache_client.set.assert_called_once()
        call_args = mock_cache_client.set.call_args
        key = call_args[0][0] if call_args[0] else call_args.kwargs.get("key")
        assert key == "rec:cold-start:trending"

    @pytest.mark.asyncio
    async def test_cold_start_job_queries_published_content(self, scheduler_with_cold_start, mock_content_repo):
        scheduler, _ = scheduler_with_cold_start
        await scheduler._run_cold_start_refresh()
        mock_content_repo.get_recent_published_ids.assert_called_once()

    @pytest.mark.asyncio
    async def test_cold_start_job_limits_to_50_items(self, scheduler_with_cold_start, mock_content_repo):
        scheduler, _ = scheduler_with_cold_start
        await scheduler._run_cold_start_refresh()
        call_args = mock_content_repo.get_recent_published_ids.call_args
        limit = call_args[0][0] if call_args[0] else call_args.kwargs.get("limit")
        assert limit == 50

    @pytest.mark.asyncio
    async def test_cold_start_job_stores_valid_json_with_items_and_generated_at(
        self, scheduler_with_cold_start, mock_content_repo, mock_cache_client
    ):
        uuids = [uuid4() for _ in range(5)]
        mock_content_repo.get_recent_published_ids.return_value = uuids
        scheduler, _ = scheduler_with_cold_start

        await scheduler._run_cold_start_refresh()

        call_args = mock_cache_client.set.call_args
        # Value is second positional arg or keyword 'value'
        value = call_args[0][1] if len(call_args[0]) > 1 else call_args.kwargs.get("value")
        data = json.loads(value)
        assert "items" in data
        assert "generated_at" in data
        assert len(data["items"]) == 5

    @pytest.mark.asyncio
    async def test_cold_start_job_sets_ttl_3600(self, scheduler_with_cold_start, mock_cache_client):
        scheduler, _ = scheduler_with_cold_start
        await scheduler._run_cold_start_refresh()
        call_args = mock_cache_client.set.call_args
        ttl = call_args.kwargs.get("ttl") or (call_args[0][2] if len(call_args[0]) > 2 else None)
        assert ttl == 3600


class TestHistoryCleanupJob:
    """Tests for the recommendation history cleanup job."""

    @pytest.fixture
    def mock_history_repo(self):
        repo = AsyncMock()
        repo.delete_older_than = AsyncMock(return_value=5)
        return repo

    @pytest.fixture
    def scheduler_with_history(self, mock_history_repo):
        with patch("src.infrastructure.scheduling.job_scheduler.AsyncIOScheduler") as mock_sched_cls:
            mock_sched_instance = MagicMock()
            mock_sched_cls.return_value = mock_sched_instance

            from src.infrastructure.scheduling.job_scheduler import JobScheduler
            mock_process_uc = AsyncMock()
            mock_update_uc = AsyncMock()
            scheduler = JobScheduler(
                process_content_use_case=mock_process_uc,
                update_profile_use_case=mock_update_uc,
            )
            scheduler._scheduler = mock_sched_instance
            scheduler.set_history_cleanup_dependencies(
                history_repo=mock_history_repo,
            )
            return scheduler, mock_sched_instance

    def test_register_history_cleanup_job_method_exists(self, scheduler_with_history):
        scheduler, _ = scheduler_with_history
        assert hasattr(scheduler, "register_history_cleanup_job")

    def test_register_history_cleanup_job_calls_add_job(self, scheduler_with_history):
        scheduler, mock_sched_instance = scheduler_with_history
        scheduler.register_history_cleanup_job()
        mock_sched_instance.add_job.assert_called()

    @pytest.mark.asyncio
    async def test_history_cleanup_job_deletes_old_entries(self, scheduler_with_history, mock_history_repo):
        scheduler, _ = scheduler_with_history
        await scheduler._run_history_cleanup()
        mock_history_repo.delete_older_than.assert_called_once()

    @pytest.mark.asyncio
    async def test_history_cleanup_job_uses_30_day_default(self, scheduler_with_history, mock_history_repo):
        scheduler, _ = scheduler_with_history
        await scheduler._run_history_cleanup()
        call_args = mock_history_repo.delete_older_than.call_args
        days = call_args[0][0] if call_args[0] else call_args.kwargs.get("days")
        assert days == 30
