"""PersonaLoader — loads and validates persona YAML files."""
from __future__ import annotations

from pathlib import Path

import yaml

from tests.evaluation.models import InteractionPattern, Persona


class PersonaValidationError(ValueError):
    """Raised when a persona YAML fails validation."""


def _slug_from_filename(filename: str) -> str:
    """Convert snake_case filename (without .yaml) to kebab-case id."""
    return Path(filename).stem.replace("_", "-")


class PersonaLoader:
    def __init__(self, personas_dir: Path, valid_topics: set[str] | None = None) -> None:
        self._dir = personas_dir
        self._valid_topics = valid_topics

    def load(self, persona_id: str) -> Persona:
        filename = persona_id.replace("-", "_") + ".yaml"
        path = self._dir / filename
        if not path.exists():
            raise FileNotFoundError(f"Persona file not found: {path}")

        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
        persona = self._parse(raw)
        self.validate(persona)

        expected_id = _slug_from_filename(filename)
        if persona.id != expected_id:
            raise PersonaValidationError(
                f"id '{persona.id}' does not match filename '{filename}' "
                f"(expected id '{expected_id}')"
            )

        return persona

    def load_all(self) -> list[Persona]:
        personas = []
        for yaml_file in sorted(self._dir.glob("*.yaml")):
            persona_id = _slug_from_filename(yaml_file.name)
            personas.append(self.load(persona_id))
        return personas

    def validate(self, persona: Persona) -> None:
        self._validate_interests_sum(persona)
        self._validate_topic_keys(persona)
        self._validate_probabilities(persona)
        self._validate_interactions_per_session(persona)
        self._validate_drift(persona)

    def _parse(self, raw: dict) -> Persona:
        pattern_data = raw["pattern"]
        pattern = InteractionPattern(
            like_probability_on_interest=float(pattern_data["like_probability_on_interest"]),
            bookmark_probability_on_interest=float(pattern_data["bookmark_probability_on_interest"]),
            dislike_probability_on_dislike=float(pattern_data["dislike_probability_on_dislike"]),
            skip_probability_on_dislike=float(pattern_data["skip_probability_on_dislike"]),
            avg_dwell_seconds=float(pattern_data["avg_dwell_seconds"]),
            scroll_depth_p50=float(pattern_data["scroll_depth_p50"]),
            interactions_per_session=int(pattern_data["interactions_per_session"]),
        )
        return Persona(
            id=raw["id"],
            display_name=raw["display_name"],
            description=raw["description"],
            base_interests=dict(raw.get("base_interests") or {}),
            dislikes=dict(raw.get("dislikes") or {}),
            preferred_entities=list(raw.get("preferred_entities") or []),
            pattern=pattern,
            drift_phase2=dict(raw["drift_phase2"]) if raw.get("drift_phase2") else None,
            drift_after_interactions=int(raw.get("drift_after_interactions", 0)),
            language=raw.get("language", "ru"),
        )

    def _validate_interests_sum(self, persona: Persona) -> None:
        total = sum(persona.base_interests.values())
        if abs(total - 1.0) > 0.01:
            raise PersonaValidationError(
                f"Persona '{persona.id}': base_interests sum={total:.4f}, expected 1.0 ±0.01"
            )

    def _validate_topic_keys(self, persona: Persona) -> None:
        if self._valid_topics is None:
            return
        for key in list(persona.base_interests) + list(persona.dislikes) + list(persona.drift_phase2 or {}):
            if key not in self._valid_topics:
                raise PersonaValidationError(
                    f"Persona '{persona.id}': unknown topic '{key}'. "
                    f"Valid topics: {sorted(self._valid_topics)}"
                )

    def _validate_probabilities(self, persona: Persona) -> None:
        prob_fields = {
            "like_probability_on_interest": persona.pattern.like_probability_on_interest,
            "bookmark_probability_on_interest": persona.pattern.bookmark_probability_on_interest,
            "dislike_probability_on_dislike": persona.pattern.dislike_probability_on_dislike,
            "skip_probability_on_dislike": persona.pattern.skip_probability_on_dislike,
            "scroll_depth_p50": persona.pattern.scroll_depth_p50,
        }
        for field_name, value in prob_fields.items():
            if not (0.0 <= value <= 1.0):
                raise PersonaValidationError(
                    f"Persona '{persona.id}': probability field '{field_name}'={value} "
                    f"is out of range [0.0, 1.0]"
                )

    def _validate_interactions_per_session(self, persona: Persona) -> None:
        if persona.pattern.interactions_per_session < 0:
            raise PersonaValidationError(
                f"Persona '{persona.id}': interactions_per_session must be >= 0"
            )

    def _validate_drift(self, persona: Persona) -> None:
        if persona.drift_phase2 is not None and persona.drift_after_interactions <= 0:
            raise PersonaValidationError(
                f"Persona '{persona.id}': drift_phase2 is set but drift_after_interactions={persona.drift_after_interactions} "
                f"(must be > 0)"
            )
