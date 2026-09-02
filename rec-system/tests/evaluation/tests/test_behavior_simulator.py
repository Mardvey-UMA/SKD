"""Integration tests for BehaviorSimulator (Phase 2 — RED phase).

All tests are @pytest.mark.integration and use testcontainers PG.
Fixtures are in tests/evaluation/tests/conftest.py.
"""
from __future__ import annotations

import uuid
from pathlib import Path

import pytest

from tests.evaluation.behavior_simulator import (
    BehaviorSimulator,
    SimulatedUserState,
)
from tests.evaluation.persona_loader import PersonaLoader

_PERSONA_DIR = Path(__file__).parent.parent / "personas"


def _loader() -> PersonaLoader:
    return PersonaLoader(_PERSONA_DIR)


# ---------------------------------------------------------------------------
# Test 1: tech-geek builds a tech-heavy profile
# ---------------------------------------------------------------------------


@pytest.mark.integration
@pytest.mark.slow
async def test_simulate_tech_geek_builds_tech_profile(simulator):
    """tech-geek: 25 interactions → profile top-1 topic == технологии."""
    persona = _loader().load("tech-geek")
    user_id = uuid.uuid4()

    state: SimulatedUserState = await simulator.simulate(persona, user_id, seed=42)

    assert state.user_id == user_id
    assert state.persona_id == "tech-geek"
    assert len(state.interactions) == persona.pattern.interactions_per_session
    assert len(state.sampled_post_ids) == persona.pattern.interactions_per_session
    assert state.profile_snapshot is not None

    topic_weights = state.profile_snapshot.topic_vector.weights
    assert topic_weights, "topic_vector should not be empty after interactions"

    top_topic = max(topic_weights, key=lambda t: topic_weights[t])
    assert top_topic == "технологии", (
        f"Expected top topic 'технологии', got '{top_topic}'. Weights: {topic_weights}"
    )


# ---------------------------------------------------------------------------
# Test 2: determinism — two identical runs produce identical state
# ---------------------------------------------------------------------------


@pytest.mark.integration
@pytest.mark.slow
async def test_determinism(simulator_factory):
    """Two runs with the same persona+seed produce identical event_ids and profile."""
    persona = _loader().load("tech-geek")

    user_id_a = uuid.uuid4()
    user_id_b = uuid.uuid4()

    state_a = await simulator_factory().simulate(persona, user_id_a, seed=7)
    state_b = await simulator_factory().simulate(persona, user_id_b, seed=7)

    assert len(state_a.interactions) == len(state_b.interactions)

    # event_types must match: same seed → same sequence of random decisions.
    # event_ids are user-id-dependent (XOR with user_id to avoid DB collision),
    # so we compare the event_type sequence rather than raw event_ids.
    types_a = [i.event_type for i in state_a.interactions]
    types_b = [i.event_type for i in state_b.interactions]
    assert types_a == types_b, (
        "event_type sequences differ between runs with same seed — simulator is not deterministic"
    )

    # Profile topic_vectors must be numerically identical (same decisions → same EMA updates)
    weights_a = state_a.profile_snapshot.topic_vector.weights
    weights_b = state_b.profile_snapshot.topic_vector.weights
    assert set(weights_a.keys()) == set(weights_b.keys()), (
        "topic_vector keys differ between runs"
    )
    for topic, w_a in weights_a.items():
        w_b = weights_b[topic]
        assert abs(w_a - w_b) < 1e-9, (
            f"topic '{topic}' weight differs: {w_a} vs {w_b}"
        )


# ---------------------------------------------------------------------------
# Test 3: drift persona shifts topic distribution after threshold
# ---------------------------------------------------------------------------


@pytest.mark.integration
@pytest.mark.slow
async def test_drift_persona(simulator, eval_content_repo):
    """drifting-tech-to-politics: last-10 posts have more политика than first-10."""
    persona = _loader().load("drifting-tech-to-politics")
    assert persona.drift_after_interactions == 10
    assert persona.drift_phase2 is not None

    user_id = uuid.uuid4()
    state: SimulatedUserState = await simulator.simulate(persona, user_id, seed=99)

    assert len(state.sampled_post_ids) == persona.pattern.interactions_per_session

    features = await eval_content_repo.get_by_ids(state.sampled_post_ids)
    topic_by_post = {f.post_id: f.topic_1 for f in features}

    first_10 = state.sampled_post_ids[:10]
    last_10 = state.sampled_post_ids[-10:]

    politics_count_first = sum(1 for pid in first_10 if topic_by_post.get(pid) == "политика")
    politics_count_last = sum(1 for pid in last_10 if topic_by_post.get(pid) == "политика")

    assert politics_count_last > politics_count_first, (
        f"Expected more 'политика' in last-10 vs first-10. "
        f"First: {politics_count_first}, Last: {politics_count_last}"
    )


# ---------------------------------------------------------------------------
# Test 4: cold-start persona — no interactions, profile exists
# ---------------------------------------------------------------------------


@pytest.mark.integration
async def test_cold_start_persona(simulator):
    """cold-start-empty (interactions_per_session=0): interactions=[], profile exists."""
    persona = _loader().load("cold-start-empty")
    assert persona.pattern.interactions_per_session == 0

    user_id = uuid.uuid4()
    state: SimulatedUserState = await simulator.simulate(persona, user_id, seed=42)

    assert state.interactions == []
    assert state.sampled_post_ids == []
    assert state.profile_snapshot is not None
    assert state.profile_snapshot.user_id == user_id
    assert len(state.onboarding_categories) >= 1


# ---------------------------------------------------------------------------
# Test 5: political-junkie avoids sports
# ---------------------------------------------------------------------------


@pytest.mark.integration
@pytest.mark.slow
async def test_dislike_avoidance(simulator, eval_content_repo):
    """political-junkie (spорт dislike=0.90): < 5% of sampled posts are 'спорт'."""
    persona = _loader().load("political-junkie")
    assert persona.dislikes.get("спорт", 0) >= 0.8

    user_id = uuid.uuid4()
    state: SimulatedUserState = await simulator.simulate(persona, user_id, seed=55)

    assert len(state.sampled_post_ids) > 0

    features = await eval_content_repo.get_by_ids(state.sampled_post_ids)
    topic_by_post = {f.post_id: f.topic_1 for f in features}

    sports_count = sum(
        1 for pid in state.sampled_post_ids if topic_by_post.get(pid) == "спорт"
    )
    ratio = sports_count / len(state.sampled_post_ids)

    assert ratio < 0.05, (
        f"Expected < 5% sports posts, got {ratio:.1%} "
        f"({sports_count}/{len(state.sampled_post_ids)})"
    )
