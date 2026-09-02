"""RecConfigLoader — typed access to rec_config groups with in-memory TTL cache."""
from __future__ import annotations

import os
import time
from typing import Any

from src.domain.interfaces.config_repository import ConfigRepository

# Default values from design/09-configuration.md
_DEFAULT_SIGNAL_WEIGHTS: dict = {
    "impression_read": {"threshold_duration_ms": 2000, "weight": 0.15},
    "impression_skip": {"threshold_duration_ms": 2000, "weight": -0.05},
    "open": {"weight": 0.1},
    "close_fast": {"threshold_duration_ms": 3000, "weight": -0.2},
    "close_full": {"threshold_scroll_pct": 0.85, "threshold_duration_ms": 15000, "weight": 0.5},
    "close_half": {"threshold_scroll_pct": 0.5, "threshold_duration_ms": 10000, "weight": 0.4},
    "close_other": {"weight": 0.1},
    "like": {"weight": 0.6},
    "dislike": {"weight": -0.7},
    "bookmark": {"weight": 0.8},
}

_DEFAULT_SCORING_WEIGHTS: dict = {
    "topic_match": 0.30,
    "embedding_sim": 0.25,
    "entity_match": 0.15,
    "sentiment_match": 0.05,
    "freshness": 0.15,
    "format_match": 0.10,
}

_DEFAULT_PROFILE_PARAMS: dict = {
    "learning_rate": 0.08,
    "entity_min_weight": 0.4,
    "entity_max_per_post": 5,
    "entity_cleanup_days": 30,
    "entity_decay_factor": 0.95,
    "job_interval_minutes": 5,
    "job_batch_threshold": 20,
    "open_orphan_timeout_minutes": 5,
}

_DEFAULT_RANKING_PARAMS: dict = {
    "freshness_halflife_hours": 48,
    "candidate_freshness_limit": 300,
    "candidate_embedding_limit": 200,
    "feed_size": 200,
    "page_size": 30,
    "max_topic_streak": 3,
    "max_topic_ratio": 0.40,
    "candidate_max_age_days": 7,
}

_DEFAULT_ONBOARDING_PARAMS: dict = {
    "baseline_weight": 0.01,
    "min_topics": 3,
    "max_topics": 5,
}

_DEFAULT_TTL_SECONDS = 60


class RecConfigLoader:
    """Typed access to rec_config groups via ConfigRepository.

    Caches DB values in memory with a configurable TTL (default 60 s).
    Override TTL via constructor arg or REC_CONFIG_CACHE_TTL_SECONDS env var.
    """

    def __init__(
        self,
        config_repo: ConfigRepository,
        cache_ttl_seconds: int | None = None,
    ) -> None:
        self._config_repo = config_repo
        env_ttl = os.environ.get("REC_CONFIG_CACHE_TTL_SECONDS")
        if cache_ttl_seconds is not None:
            self._ttl = cache_ttl_seconds
        elif env_ttl is not None:
            self._ttl = int(env_ttl)
        else:
            self._ttl = _DEFAULT_TTL_SECONDS
        # {key: (expiry_timestamp, value)}
        self._cache: dict[str, tuple[float, Any]] = {}

    async def get(self, key: str) -> Any:
        """Return config value for key, using cache when valid."""
        now = time.time()
        cached = self._cache.get(key)
        if cached is not None:
            expiry, value = cached
            if now < expiry:
                return value
        value = await self._config_repo.get_config(key)
        self._cache[key] = (now + self._ttl, value)
        return value

    # ConfigRepository-compatible alias so this loader can be a drop-in replacement.
    async def get_config(self, key: str) -> Any:
        return await self.get(key)

    def invalidate(self, key: str | None = None) -> None:
        """Remove one key (or all) from the cache."""
        if key is None:
            self._cache.clear()
        else:
            self._cache.pop(key, None)

    # --- Typed convenience accessors ---

    async def get_signal_weights(self) -> dict:
        value = await self.get("signal_weights")
        return value if value is not None else _DEFAULT_SIGNAL_WEIGHTS

    async def get_scoring_weights(self) -> dict:
        value = await self.get("scoring_weights")
        return value if value is not None else _DEFAULT_SCORING_WEIGHTS

    async def get_profile_params(self) -> dict:
        value = await self.get("profile_params")
        return value if value is not None else _DEFAULT_PROFILE_PARAMS

    async def get_ranking_params(self) -> dict:
        value = await self.get("ranking_params")
        return value if value is not None else _DEFAULT_RANKING_PARAMS

    async def get_onboarding_params(self) -> dict:
        value = await self.get("onboarding_params")
        return value if value is not None else _DEFAULT_ONBOARDING_PARAMS
