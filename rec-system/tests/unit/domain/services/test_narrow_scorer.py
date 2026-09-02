"""RED tests for NarrowScorer domain service (Phase 1 user-sources).

NarrowScorer scores candidates for the NARROW ranking mode using a
cosine + freshness combination (diversity filter is bypassed in NARROW).
"""
from __future__ import annotations

import math
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest

from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector


DEFAULT_NARROW_WEIGHTS = {"cosine": 0.7, "freshness": 0.3, "halflife_hours": 48}
DEFAULT_CONFIG = {"narrow_ranking_weights": DEFAULT_NARROW_WEIGHTS}


def make_profile(embedding=None) -> UserProfile:
    now = datetime.now(timezone.utc)
    return UserProfile(
        user_id=uuid4(),
        topic_vector=TopicVector({t: 1.0 / 18 for t in TopicVector.TOPICS}),
        sentiment_prefs=SentimentPrefs.create_uniform(),
        format_prefs=FormatPrefs.create_default(),
        interaction_count=0,
        created_at=now,
        last_updated=now,
        embedding=embedding,
    )


def make_content(
    post_id=None,
    embedding=None,
    age_hours: float = 0.0,
) -> ContentFeatures:
    now = datetime.now(timezone.utc)
    return ContentFeatures(
        post_id=post_id if post_id is not None else uuid4(),
        content="c",
        post_date=now - timedelta(hours=age_hours),
        embedding=embedding,
        processed_at=now - timedelta(hours=age_hours),
    )


class TestNarrowScorer:
    def test_importable(self):
        from src.domain.services.narrow_scorer import NarrowScorer

        assert NarrowScorer is not None

    def test_score_batch_returns_sorted_post_scores(self):
        from src.domain.services.narrow_scorer import NarrowScorer
        from src.domain.value_objects.post_score import PostScore

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0, 0.0, 0.0])
        fresh = make_content(embedding=[1.0, 0.0, 0.0], age_hours=0)
        stale = make_content(embedding=[1.0, 0.0, 0.0], age_hours=100)

        result = scorer.score_batch([fresh, stale], profile, DEFAULT_CONFIG)

        assert isinstance(result, list)
        assert all(isinstance(ps, PostScore) for ps in result)
        assert len(result) == 2
        # fresh should outrank stale
        assert result[0].post_id == fresh.post_id
        assert result[0].score > result[1].score

    def test_cosine_plus_freshness_formula_at_zero_age(self):
        """At age=0 and identical embeddings, score = 0.7*1.0 + 0.3*1.0 = 1.0."""
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0, 0.0, 0.0])
        post = make_content(embedding=[1.0, 0.0, 0.0], age_hours=0)

        result = scorer.score_batch([post], profile, DEFAULT_CONFIG)

        assert result[0].score == pytest.approx(1.0, abs=1e-3)

    def test_freshness_halves_at_halflife(self):
        """At age = halflife (48h) with orthogonal embeddings, score = 0.3*0.5 = 0.15."""
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0, 0.0, 0.0])
        # orthogonal embedding → cosine = 0
        post = make_content(embedding=[0.0, 1.0, 0.0], age_hours=48)

        result = scorer.score_batch([post], profile, DEFAULT_CONFIG)

        assert result[0].score == pytest.approx(0.3 * 0.5, abs=1e-2)

    def test_no_profile_embedding_falls_back_to_freshness_only(self):
        """Profile.embedding=None → score is purely the freshness component normalized to 1.0."""
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=None)
        post_fresh = make_content(embedding=[1.0, 0.0, 0.0], age_hours=0)

        result = scorer.score_batch([post_fresh], profile, DEFAULT_CONFIG)

        # freshness is 1.0 at age 0; weights collapse to freshness=1.0 → score ~ 1.0
        assert result[0].score == pytest.approx(1.0, abs=1e-3)

    def test_post_missing_embedding_contributes_zero_cosine(self):
        """If the candidate has no embedding, cosine component is 0 and score is freshness-only."""
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0, 0.0, 0.0])
        post = make_content(embedding=None, age_hours=0)

        result = scorer.score_batch([post], profile, DEFAULT_CONFIG)

        # cosine = 0, freshness = 1 → score = 0.7*0 + 0.3*1 = 0.3
        assert result[0].score == pytest.approx(0.3, abs=1e-3)

    def test_uses_weights_from_config(self):
        """Weights from config.narrow_ranking_weights override defaults."""
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0, 0.0, 0.0])
        post = make_content(embedding=[1.0, 0.0, 0.0], age_hours=0)

        config = {
            "narrow_ranking_weights": {
                "cosine": 0.9,
                "freshness": 0.1,
                "halflife_hours": 24,
            }
        }
        result = scorer.score_batch([post], profile, config)

        # 0.9 * 1.0 + 0.1 * 1.0 = 1.0 at age=0
        assert result[0].score == pytest.approx(1.0, abs=1e-3)

    def test_empty_candidates_returns_empty_list(self):
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0])
        assert scorer.score_batch([], profile, DEFAULT_CONFIG) == []

    def test_cosine_clamped_to_zero_when_negative(self):
        """Negative cosine similarity is clamped to 0."""
        from src.domain.services.narrow_scorer import NarrowScorer

        scorer = NarrowScorer()
        profile = make_profile(embedding=[1.0, 0.0, 0.0])
        post = make_content(embedding=[-1.0, 0.0, 0.0], age_hours=0)

        result = scorer.score_batch([post], profile, DEFAULT_CONFIG)

        # cosine clamped to 0, freshness = 1 → 0.7*0 + 0.3*1 = 0.3
        assert result[0].score == pytest.approx(0.3, abs=1e-3)
