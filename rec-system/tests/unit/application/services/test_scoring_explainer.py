"""RED tests for ScoringExplainer service — Phase 8 eval harness explainability."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock
from datetime import datetime, timezone
from uuid import uuid4, UUID

from src.application.services.scoring_explainer import (
    ScoringExplainer,
    ScoringExplanation,
    ComponentBreakdown,
    NotFoundError,
)
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.entity_interest import EntityInterest
from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.format_prefs import FormatPrefs
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.topic_vector import TopicVector


# ── helpers ───────────────────────────────────────────────────────────────────


def _profile(
    user_id: UUID | None = None,
    topics: dict | None = None,
    embedding: list[float] | None = None,
    cold_start: bool = False,
) -> UserProfile:
    uid = user_id or uuid4()
    tw = topics or {"технологии": 0.6, "наука": 0.3, "экономика": 0.1}
    return UserProfile(
        user_id=uid,
        topic_vector=TopicVector(tw),
        sentiment_prefs=SentimentPrefs({"POSITIVE": 0.4, "NEGATIVE": 0.2, "NEUTRAL": 0.4}),
        format_prefs=FormatPrefs(avg_length=400.0, avg_complexity=0.6),
        interaction_count=10,
        created_at=datetime.now(timezone.utc),
        last_updated=datetime.now(timezone.utc),
        embedding=embedding,
        cold_start=cold_start,
    )


def _features(
    post_id: UUID | None = None,
    topic_1: str = "технологии",
    topic_1_score: float = 0.9,
    topic_2: str = "наука",
    topic_2_score: float = 0.6,
    embedding: list[float] | None = None,
    sentiment: str = "POSITIVE",
    entities_persons: list[str] | None = None,
    word_count: int = 400,
    complexity: float = 0.6,
) -> ContentFeatures:
    return ContentFeatures(
        post_id=post_id or uuid4(),
        content="test content",
        post_date=datetime.now(timezone.utc),
        topic_1=topic_1,
        topic_1_score=topic_1_score,
        topic_2=topic_2,
        topic_2_score=topic_2_score,
        embedding=embedding,
        sentiment=sentiment,
        entities_persons=entities_persons or [],
        word_count=word_count,
        complexity=complexity,
        processed_at=datetime.now(timezone.utc),
    )


def _explainer(
    feat: ContentFeatures | None = None,
    prof: UserProfile | None = None,
    interests: list[EntityInterest] | None = None,
) -> ScoringExplainer:
    from src.domain.services.scorer import Scorer

    scorer = Scorer()
    content_repo = AsyncMock()
    profile_repo = AsyncMock()
    entity_interest_repo = AsyncMock()
    config_repo = AsyncMock()

    f = feat or _features()
    content_repo.get_by_ids.return_value = [f]
    content_repo.get_published_ids.return_value = {}

    p = prof or _profile()
    profile_repo.get_by_user_id.return_value = p

    entity_interest_repo.get_top_for_user.return_value = interests or []

    config_repo.get_config.side_effect = _mock_config

    return ScoringExplainer(
        scorer=scorer,
        content_repo=content_repo,
        profile_repo=profile_repo,
        entity_interest_repo=entity_interest_repo,
        config_repo=config_repo,
    )


async def _mock_config(key: str):
    if key == "scoring_weights":
        return {
            "topic_match": 0.30,
            "embedding_sim": 0.25,
            "entity_match": 0.15,
            "sentiment_match": 0.05,
            "freshness": 0.15,
            "format_match": 0.10,
        }
    if key == "ranking_params":
        return {"freshness_halflife_hours": 48}
    return None


# ── tests ─────────────────────────────────────────────────────────────────────


@pytest.mark.unit
async def test_contribution_is_value_times_weight():
    """Each active component's contribution must equal value × effective_weight."""
    emb = [0.1] * 312
    uid = uuid4()
    interest = EntityInterest(
        user_id=str(uid),
        entity_type="organization",
        entity_name="OpenAI",
        weight=2.0,
        last_seen=datetime.now(timezone.utc),
    )
    prof = _profile(user_id=uid, embedding=emb)
    feat = _features(
        embedding=emb,
        entities_persons=["OpenAI"],
    )
    ex = _explainer(feat=feat, prof=prof, interests=[interest])

    result = await ex.explain(uid, feat.post_id)

    assert isinstance(result, ScoringExplanation)
    for comp in result.components:
        if comp.value is not None:
            expected = comp.value * comp.weight
            assert abs(comp.contribution - expected) < 1e-9, (
                f"{comp.name}: contribution={comp.contribution} "
                f"!= value({comp.value}) * weight({comp.weight})"
            )


@pytest.mark.unit
async def test_dead_component_redistribution():
    """When embedding is absent, embedding_sim is dead; remaining weights sum to 1.0."""
    uid = uuid4()
    prof = _profile(user_id=uid, embedding=None)
    feat = _features(embedding=None)
    ex = _explainer(feat=feat, prof=prof)

    result = await ex.explain(uid, feat.post_id)

    assert "embedding_sim" in result.dead_components
    assert result.redistributed_weights is not None
    total = sum(result.redistributed_weights.values())
    assert abs(total - 1.0) < 1e-9, f"Redistributed weights sum={total}, expected 1.0"
    emb_comp = next(c for c in result.components if c.name == "embedding_sim")
    assert emb_comp.contribution == 0.0


@pytest.mark.unit
async def test_cold_start_redistribution():
    """Profile with cold_start=True and no embedding → cold_start_applied=True."""
    uid = uuid4()
    prof = _profile(user_id=uid, embedding=None, cold_start=True)
    feat = _features(embedding=None)
    ex = _explainer(feat=feat, prof=prof)

    result = await ex.explain(uid, feat.post_id)

    assert result.cold_start_applied is True
    assert result.redistributed_weights is not None
    # Final score must still be positive (active components)
    assert result.final_score > 0.0


@pytest.mark.unit
async def test_matched_topics_in_topic_match_details():
    """topic_match component details include matched_topics for user's top interests."""
    uid = uuid4()
    prof = _profile(
        user_id=uid,
        topics={"технологии": 0.7, "наука": 0.2, "экономика": 0.1},
        embedding=None,
    )
    feat = _features(
        embedding=None,
        topic_1="технологии",
        topic_1_score=0.92,
        topic_2="наука",
        topic_2_score=0.55,
    )
    ex = _explainer(feat=feat, prof=prof)

    result = await ex.explain(uid, feat.post_id)

    topic_comp = next(c for c in result.components if c.name == "topic_match")
    matched = topic_comp.details.get("matched_topics", [])
    assert len(matched) >= 1
    topic_names = [m["topic"] for m in matched]
    assert "технологии" in topic_names
    tech_entry = next(m for m in matched if m["topic"] == "технологии")
    assert tech_entry["post_score"] == pytest.approx(0.92, abs=1e-6)
    assert tech_entry["profile_weight"] > 0.0


@pytest.mark.unit
async def test_raises_not_found_when_content_missing():
    """NotFoundError raised when content_id maps to nothing in DB."""
    uid = uuid4()
    prof = _profile(user_id=uid)
    feat = _features()

    ex = _explainer(feat=feat, prof=prof)
    # Override: get_by_ids returns nothing, get_raw_id_by_published_id returns None
    ex._content_repo.get_by_ids.return_value = []
    ex._content_repo.get_raw_id_by_published_id = AsyncMock(return_value=None)

    with pytest.raises(NotFoundError):
        await ex.explain(uid, uuid4())


@pytest.mark.unit
async def test_raises_not_found_when_user_missing():
    """NotFoundError raised when user_id has no profile."""
    uid = uuid4()
    feat = _features()

    ex = _explainer(feat=feat)
    ex._profile_repo.get_by_user_id.return_value = None

    with pytest.raises(NotFoundError):
        await ex.explain(uid, feat.post_id)
