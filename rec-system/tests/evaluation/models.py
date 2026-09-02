"""Persona data models for the evaluation harness."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal
from uuid import UUID


@dataclass(frozen=True)
class InteractionPattern:
    like_probability_on_interest: float
    bookmark_probability_on_interest: float
    dislike_probability_on_dislike: float
    skip_probability_on_dislike: float
    avg_dwell_seconds: float
    scroll_depth_p50: float
    interactions_per_session: int


@dataclass(frozen=True)
class Persona:
    id: str
    display_name: str
    description: str
    base_interests: dict[str, float]
    dislikes: dict[str, float]
    preferred_entities: list[str]
    pattern: InteractionPattern
    drift_phase2: dict[str, float] | None = None
    drift_after_interactions: int = 0
    language: Literal["ru"] = "ru"
