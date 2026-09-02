"""Unit tests for RecConfigLoader TTL cache — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock, patch


class TestRecConfigLoaderCache:
    @pytest.fixture
    def mock_config_repo(self):
        repo = MagicMock()
        repo.get_config = AsyncMock(return_value={"key": "value"})
        return repo

    @pytest.fixture
    def sut(self, mock_config_repo):
        from src.infrastructure.config.rec_config_loader import RecConfigLoader
        return RecConfigLoader(config_repo=mock_config_repo, cache_ttl_seconds=60)

    # --- Cache miss/hit ---

    @pytest.mark.asyncio
    async def test_first_call_hits_db(self, sut, mock_config_repo):
        await sut.get("signal_weights")
        mock_config_repo.get_config.assert_called_once_with("signal_weights")

    @pytest.mark.asyncio
    async def test_second_call_within_ttl_returns_cached_value(self, sut, mock_config_repo):
        result1 = await sut.get("signal_weights")
        result2 = await sut.get("signal_weights")
        # Only one DB call
        assert mock_config_repo.get_config.call_count == 1
        # Same object returned from cache
        assert result1 is result2

    @pytest.mark.asyncio
    async def test_cache_miss_on_first_call(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {"fresh": True}
        result = await sut.get("scoring_weights")
        assert result == {"fresh": True}
        mock_config_repo.get_config.assert_called_once_with("scoring_weights")

    @pytest.mark.asyncio
    async def test_cache_hit_on_second_call(self, sut, mock_config_repo):
        await sut.get("ranking_params")
        mock_config_repo.get_config.reset_mock()
        await sut.get("ranking_params")
        mock_config_repo.get_config.assert_not_called()

    # --- TTL expiry ---

    @pytest.mark.asyncio
    async def test_expired_ttl_hits_db_again(self, sut, mock_config_repo):
        import time
        now = 1000.0
        with patch("time.time", return_value=now):
            await sut.get("profile_params")

        # Advance time past TTL (60s)
        with patch("time.time", return_value=now + 61.0):
            await sut.get("profile_params")

        assert mock_config_repo.get_config.call_count == 2

    @pytest.mark.asyncio
    async def test_not_expired_within_ttl_uses_cache(self, sut, mock_config_repo):
        import time
        now = 1000.0
        with patch("time.time", return_value=now):
            await sut.get("profile_params")

        with patch("time.time", return_value=now + 59.0):
            await sut.get("profile_params")

        assert mock_config_repo.get_config.call_count == 1

    # --- Invalidation ---

    @pytest.mark.asyncio
    async def test_invalidate_specific_key_removes_only_that_key(self, sut, mock_config_repo):
        await sut.get("k1")
        await sut.get("k2")
        mock_config_repo.get_config.reset_mock()

        sut.invalidate("k1")

        # k1 should be fetched from DB again
        await sut.get("k1")
        assert mock_config_repo.get_config.call_count == 1

        # k2 should still be cached
        await sut.get("k2")
        assert mock_config_repo.get_config.call_count == 1

    @pytest.mark.asyncio
    async def test_invalidate_all_clears_entire_cache(self, sut, mock_config_repo):
        await sut.get("k1")
        await sut.get("k2")
        mock_config_repo.get_config.reset_mock()

        sut.invalidate()  # no key → clear all

        await sut.get("k1")
        await sut.get("k2")
        assert mock_config_repo.get_config.call_count == 2

    @pytest.mark.asyncio
    async def test_invalidate_nonexistent_key_is_noop(self, sut):
        sut.invalidate("does_not_exist")  # should not raise

    # --- Constructor defaults ---

    def test_default_ttl_is_60(self, mock_config_repo):
        from src.infrastructure.config.rec_config_loader import RecConfigLoader
        loader = RecConfigLoader(config_repo=mock_config_repo)
        assert loader._ttl == 60

    def test_custom_ttl_is_respected(self, mock_config_repo):
        from src.infrastructure.config.rec_config_loader import RecConfigLoader
        loader = RecConfigLoader(config_repo=mock_config_repo, cache_ttl_seconds=120)
        assert loader._ttl == 120

    def test_env_var_ttl_override(self, mock_config_repo):
        import os
        from src.infrastructure.config.rec_config_loader import RecConfigLoader
        os.environ["REC_CONFIG_CACHE_TTL_SECONDS"] = "300"
        try:
            loader = RecConfigLoader(config_repo=mock_config_repo)
            assert loader._ttl == 300
        finally:
            del os.environ["REC_CONFIG_CACHE_TTL_SECONDS"]

    # --- Typed methods still work ---

    @pytest.mark.asyncio
    async def test_get_signal_weights_uses_cache(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {"like": {"weight": 0.6}}
        r1 = await sut.get_signal_weights()
        r2 = await sut.get_signal_weights()
        assert mock_config_repo.get_config.call_count == 1
        assert r1 is r2

    @pytest.mark.asyncio
    async def test_get_scoring_weights_uses_cache(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {"topic_match": 0.30}
        r1 = await sut.get_scoring_weights()
        r2 = await sut.get_scoring_weights()
        assert mock_config_repo.get_config.call_count == 1
        assert r1 is r2
