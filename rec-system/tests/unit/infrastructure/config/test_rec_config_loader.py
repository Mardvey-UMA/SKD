"""Unit tests for RecConfigLoader — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, MagicMock


class TestRecConfigLoader:
    @pytest.fixture
    def mock_config_repo(self):
        repo = MagicMock()
        repo.get_config = AsyncMock()
        return repo

    @pytest.fixture
    def sut(self, mock_config_repo):
        from src.infrastructure.config.rec_config_loader import RecConfigLoader
        return RecConfigLoader(config_repo=mock_config_repo)

    @pytest.mark.asyncio
    async def test_get_signal_weights(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {
            "impression_read": {"threshold_duration_ms": 2000, "weight": 0.15},
            "like": {"weight": 0.6},
        }
        result = await sut.get_signal_weights()
        assert isinstance(result, dict)
        assert "impression_read" in result or "like" in result
        mock_config_repo.get_config.assert_called_once_with("signal_weights")

    @pytest.mark.asyncio
    async def test_get_scoring_weights(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {
            "topic_match": 0.30,
            "embedding_sim": 0.25,
            "entity_match": 0.15,
            "sentiment_match": 0.05,
            "freshness": 0.15,
            "format_match": 0.10,
        }
        result = await sut.get_scoring_weights()
        assert isinstance(result, dict)
        assert abs(sum(result.values()) - 1.0) < 0.01
        mock_config_repo.get_config.assert_called_once_with("scoring_weights")

    @pytest.mark.asyncio
    async def test_get_profile_params(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {
            "learning_rate": 0.08,
            "entity_cleanup_days": 30,
        }
        result = await sut.get_profile_params()
        assert isinstance(result, dict)
        assert "learning_rate" in result
        mock_config_repo.get_config.assert_called_once_with("profile_params")

    @pytest.mark.asyncio
    async def test_get_ranking_params(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {
            "feed_size": 200,
            "freshness_halflife_hours": 48,
        }
        result = await sut.get_ranking_params()
        assert isinstance(result, dict)
        assert "feed_size" in result
        mock_config_repo.get_config.assert_called_once_with("ranking_params")

    @pytest.mark.asyncio
    async def test_get_onboarding_params(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = {
            "baseline_weight": 0.01,
            "min_topics": 3,
            "max_topics": 5,
        }
        result = await sut.get_onboarding_params()
        assert isinstance(result, dict)
        assert "baseline_weight" in result
        mock_config_repo.get_config.assert_called_once_with("onboarding_params")

    @pytest.mark.asyncio
    async def test_provides_defaults_if_missing(self, sut, mock_config_repo):
        mock_config_repo.get_config.return_value = None
        result = await sut.get_scoring_weights()
        assert isinstance(result, dict)
        # Default scoring weights must be present
        assert "topic_match" in result
        assert "embedding_sim" in result
        assert "freshness" in result
