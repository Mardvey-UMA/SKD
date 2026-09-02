"""RED tests for PersonaLoader — Phase 1 Eval Harness."""
from __future__ import annotations

from pathlib import Path

import pytest
import yaml

from tests.evaluation.models import InteractionPattern, Persona
from tests.evaluation.persona_loader import PersonaLoader, PersonaValidationError

PERSONAS_DIR = Path(__file__).parent.parent / "personas"

CANONICAL_TOPICS = {
    "политика", "экономика", "технологии", "наука", "спорт", "культура",
    "общество", "происшествия", "международные новости", "бизнес",
    "финансы", "образование", "здоровье", "развлечения", "криминал",
    "армия", "природа", "транспорт",
}

REQUIRED_IDS = {
    "tech-geek", "political-junkie", "business-follower", "sports-fan",
    "balanced-reader", "entertainment-only", "health-focused",
    "breaking-news-chaser", "long-form-reader", "skimmer",
    "negative-sentiment-seeker", "entity-focused-musk", "entity-focused-putin",
    "drifting-tech-to-politics", "cold-start-empty", "science-only",
    "crime-reader", "military-watcher", "transport-enthusiast",
    "education-culture",
}


@pytest.mark.unit
def test_load_valid_yaml():
    """loads tech-geek, returns Persona with correct fields."""
    loader = PersonaLoader(PERSONAS_DIR, valid_topics=CANONICAL_TOPICS)
    persona = loader.load("tech-geek")

    assert isinstance(persona, Persona)
    assert persona.id == "tech-geek"
    assert persona.display_name
    assert persona.description
    assert "технологии" in persona.base_interests
    assert isinstance(persona.pattern, InteractionPattern)
    assert persona.language == "ru"


@pytest.mark.unit
def test_load_all_returns_20():
    """load_all() returns exactly 20 personas."""
    loader = PersonaLoader(PERSONAS_DIR, valid_topics=CANONICAL_TOPICS)
    personas = loader.load_all()
    assert len(personas) == 20


@pytest.mark.unit
def test_validate_rejects_bad_interests_sum(tmp_path):
    """sum of base_interests != 1.0 (tolerance 0.01) → PersonaValidationError."""
    data = {
        "id": "bad-sum",
        "display_name": "Bad",
        "description": "test",
        "base_interests": {"технологии": 0.30, "наука": 0.20},  # sum=0.5
        "dislikes": {},
        "preferred_entities": [],
        "pattern": {
            "like_probability_on_interest": 0.5,
            "bookmark_probability_on_interest": 0.2,
            "dislike_probability_on_dislike": 0.5,
            "skip_probability_on_dislike": 0.7,
            "avg_dwell_seconds": 20.0,
            "scroll_depth_p50": 0.5,
            "interactions_per_session": 10,
        },
    }
    yaml_file = tmp_path / "bad_sum.yaml"
    yaml_file.write_text(yaml.dump(data, allow_unicode=True), encoding="utf-8")

    loader = PersonaLoader(tmp_path, valid_topics=CANONICAL_TOPICS)
    with pytest.raises(PersonaValidationError, match="base_interests"):
        loader.load("bad-sum")


@pytest.mark.unit
def test_validate_rejects_unknown_topic(tmp_path):
    """key not in valid_topics → PersonaValidationError."""
    data = {
        "id": "unknown-topic",
        "display_name": "Unknown",
        "description": "test",
        "base_interests": {"nonexistent_topic": 1.0},
        "dislikes": {},
        "preferred_entities": [],
        "pattern": {
            "like_probability_on_interest": 0.5,
            "bookmark_probability_on_interest": 0.2,
            "dislike_probability_on_dislike": 0.5,
            "skip_probability_on_dislike": 0.7,
            "avg_dwell_seconds": 20.0,
            "scroll_depth_p50": 0.5,
            "interactions_per_session": 10,
        },
    }
    yaml_file = tmp_path / "unknown_topic.yaml"
    yaml_file.write_text(yaml.dump(data, allow_unicode=True), encoding="utf-8")

    loader = PersonaLoader(tmp_path, valid_topics=CANONICAL_TOPICS)
    with pytest.raises(PersonaValidationError, match="unknown topic"):
        loader.load("unknown-topic")


@pytest.mark.unit
def test_validate_rejects_out_of_range_probability(tmp_path):
    """like_probability > 1.0 → PersonaValidationError."""
    data = {
        "id": "bad-prob",
        "display_name": "Bad Prob",
        "description": "test",
        "base_interests": {"технологии": 1.0},
        "dislikes": {},
        "preferred_entities": [],
        "pattern": {
            "like_probability_on_interest": 1.5,  # out of range
            "bookmark_probability_on_interest": 0.2,
            "dislike_probability_on_dislike": 0.5,
            "skip_probability_on_dislike": 0.7,
            "avg_dwell_seconds": 20.0,
            "scroll_depth_p50": 0.5,
            "interactions_per_session": 10,
        },
    }
    yaml_file = tmp_path / "bad_prob.yaml"
    yaml_file.write_text(yaml.dump(data, allow_unicode=True), encoding="utf-8")

    loader = PersonaLoader(tmp_path, valid_topics=CANONICAL_TOPICS)
    with pytest.raises(PersonaValidationError, match="probability"):
        loader.load("bad-prob")


@pytest.mark.unit
def test_drift_schema():
    """drifting-tech-to-politics loads with drift_phase2 and drift_after_interactions."""
    loader = PersonaLoader(PERSONAS_DIR, valid_topics=CANONICAL_TOPICS)
    persona = loader.load("drifting-tech-to-politics")

    assert persona.drift_phase2 is not None
    assert persona.drift_after_interactions > 0
    assert "политика" in persona.drift_phase2


@pytest.mark.unit
def test_cold_start_persona():
    """cold-start-empty has interactions_per_session=0."""
    loader = PersonaLoader(PERSONAS_DIR, valid_topics=CANONICAL_TOPICS)
    persona = loader.load("cold-start-empty")

    assert persona.pattern.interactions_per_session == 0


@pytest.mark.unit
def test_id_matches_filename(tmp_path):
    """tech_geek.yaml must yield id 'tech-geek'; mismatch → PersonaValidationError."""
    data = {
        "id": "wrong-id",
        "display_name": "Wrong",
        "description": "test",
        "base_interests": {"технологии": 1.0},
        "dislikes": {},
        "preferred_entities": [],
        "pattern": {
            "like_probability_on_interest": 0.5,
            "bookmark_probability_on_interest": 0.2,
            "dislike_probability_on_dislike": 0.5,
            "skip_probability_on_dislike": 0.7,
            "avg_dwell_seconds": 20.0,
            "scroll_depth_p50": 0.5,
            "interactions_per_session": 10,
        },
    }
    # filename tech_geek.yaml → expected id "tech-geek"
    yaml_file = tmp_path / "tech_geek.yaml"
    yaml_file.write_text(yaml.dump(data, allow_unicode=True), encoding="utf-8")

    loader = PersonaLoader(tmp_path, valid_topics=CANONICAL_TOPICS)
    with pytest.raises(PersonaValidationError, match="id.*filename|filename.*id"):
        loader.load("tech-geek")


@pytest.mark.unit
def test_drift_without_drift_after_interactions_raises(tmp_path):
    """drift_phase2 set but drift_after_interactions=0 → PersonaValidationError."""
    data = {
        "id": "bad-drift",
        "display_name": "Bad Drift",
        "description": "test",
        "base_interests": {"технологии": 1.0},
        "dislikes": {},
        "preferred_entities": [],
        "drift_phase2": {"политика": 0.8, "технологии": 0.2},
        "drift_after_interactions": 0,
        "pattern": {
            "like_probability_on_interest": 0.5,
            "bookmark_probability_on_interest": 0.2,
            "dislike_probability_on_dislike": 0.5,
            "skip_probability_on_dislike": 0.7,
            "avg_dwell_seconds": 20.0,
            "scroll_depth_p50": 0.5,
            "interactions_per_session": 10,
        },
    }
    yaml_file = tmp_path / "bad_drift.yaml"
    yaml_file.write_text(yaml.dump(data, allow_unicode=True), encoding="utf-8")

    loader = PersonaLoader(tmp_path, valid_topics=CANONICAL_TOPICS)
    with pytest.raises(PersonaValidationError, match="drift"):
        loader.load("bad-drift")


@pytest.mark.unit
def test_every_persona_from_required_list():
    """Set of loaded ids exactly matches 20-item required list."""
    loader = PersonaLoader(PERSONAS_DIR, valid_topics=CANONICAL_TOPICS)
    personas = loader.load_all()
    loaded_ids = {p.id for p in personas}
    assert loaded_ids == REQUIRED_IDS
