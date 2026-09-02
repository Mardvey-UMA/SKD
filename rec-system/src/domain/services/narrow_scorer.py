"""NarrowScorer domain service — Phase 1 user-sources NARROW ranking mode.

Two-component formula (cosine + freshness) that replaces the full 6-component
Scorer when the caller passes ranking_mode=NARROW. DiversityFilter is bypassed
by GenerateFeedUseCase in NARROW mode — the user has already narrowed scope by
selecting a source list, so extra diversity would fight user intent.
"""
from __future__ import annotations

import math
from datetime import datetime, timezone
from typing import Any, Dict, List

from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.post_score import PostScore


class NarrowScorer:
    """Scores candidates using weighted cosine similarity + freshness decay."""

    _DEFAULTS = {"cosine": 0.7, "freshness": 0.3, "halflife_hours": 48}

    def score_batch(
        self,
        candidates: List[ContentFeatures],
        profile: UserProfile,
        config: Dict[str, Any],
    ) -> List[PostScore]:
        """Score each candidate and return a DESC-sorted list of PostScore."""
        weights = (config or {}).get("narrow_ranking_weights", self._DEFAULTS)
        w_cosine = float(weights.get("cosine", self._DEFAULTS["cosine"]))
        w_fresh = float(weights.get("freshness", self._DEFAULTS["freshness"]))
        halflife = float(weights.get("halflife_hours", self._DEFAULTS["halflife_hours"]))

        # Cold-start fallback: if the profile has no embedding, cosine is dead
        # everywhere, so give freshness the whole weight budget.
        profile_embedding = profile.embedding
        cosine_is_dead = profile_embedding is None

        scored: List[PostScore] = []
        for content in candidates:
            cosine_val = (
                0.0
                if cosine_is_dead or content.embedding is None
                else self._cosine(profile_embedding, content.embedding)
            )
            freshness_val = self._freshness(content, halflife)

            if cosine_is_dead:
                score = freshness_val  # 100% freshness
            else:
                score = w_cosine * cosine_val + w_fresh * freshness_val

            scored.append(PostScore(post_id=content.post_id, score=score))

        scored.sort(key=lambda ps: ps.score, reverse=True)
        return scored

    @staticmethod
    def _cosine(a: List[float], b: List[float]) -> float:
        """Cosine similarity; assumes L2-normalized vectors. Clamped to [0, 1]."""
        dot = sum(x * y for x, y in zip(a, b))
        return max(0.0, min(dot, 1.0))

    @staticmethod
    def _freshness(content: ContentFeatures, halflife_hours: float) -> float:
        """exp(-ln(2) * hours_since_processed / halflife_hours), clamped non-negative age."""
        now = datetime.now(timezone.utc)
        ref = content.processed_at or content.post_date
        if ref.tzinfo is None:
            ref = ref.replace(tzinfo=timezone.utc)
        hours = max(0.0, (now - ref).total_seconds() / 3600.0)
        return math.exp(-math.log(2.0) * hours / halflife_hours)
