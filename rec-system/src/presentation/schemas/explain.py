"""Pydantic v2 schemas for POST /recommendations/explain endpoint."""
from __future__ import annotations

from typing import Optional
from uuid import UUID

from pydantic import BaseModel

from src.application.services.scoring_explainer import ScoringExplanation


class ExplainRequest(BaseModel):
    user_id: UUID
    content_id: UUID


class TopicMatchEntry(BaseModel):
    topic: str
    profile_weight: float
    post_score: float


class ComponentBreakdownSchema(BaseModel):
    value: Optional[float] = None
    weight: float
    contribution: float
    # topic_match fields
    matched_topics: Optional[list[TopicMatchEntry]] = None
    # entity_match fields
    matched_entities: Optional[list[str]] = None
    interest_weights: Optional[dict[str, float]] = None
    # sentiment_match fields
    post_sentiment: Optional[str] = None
    profile_preference: Optional[dict[str, float]] = None
    # freshness fields
    age_hours: Optional[float] = None
    half_life_hours: Optional[float] = None
    # format_match fields
    post_word_count: Optional[int] = None
    profile_preferred_word_count_avg: Optional[float] = None
    post_complexity: Optional[float] = None
    profile_preferred_complexity_avg: Optional[float] = None


class ComponentsSchema(BaseModel):
    topic_match: ComponentBreakdownSchema
    embedding_sim: ComponentBreakdownSchema
    entity_match: ComponentBreakdownSchema
    sentiment_match: ComponentBreakdownSchema
    freshness: ComponentBreakdownSchema
    format_match: ComponentBreakdownSchema


class ExplainResponse(BaseModel):
    content_id: UUID
    raw_content_id: UUID
    published_content_id: Optional[UUID] = None
    final_score: float
    components: ComponentsSchema
    cold_start_applied: bool
    dead_components: list[str]
    redistributed_weights: Optional[dict[str, float]] = None
    rank_in_candidates: Optional[int] = None
    rank_in_feed: Optional[int] = None
    filtered_out_by: Optional[str] = None

    @classmethod
    def from_explanation(cls, explanation: ScoringExplanation) -> "ExplainResponse":
        comp_dict: dict[str, ComponentBreakdownSchema] = {}
        for comp in explanation.components:
            data = {
                "value": comp.value,
                "weight": comp.weight,
                "contribution": comp.contribution,
            }
            data.update(comp.details)
            comp_dict[comp.name] = ComponentBreakdownSchema(**data)

        return cls(
            content_id=explanation.content_id,
            raw_content_id=explanation.raw_content_id,
            published_content_id=explanation.published_content_id,
            final_score=explanation.final_score,
            components=ComponentsSchema(**comp_dict),
            cold_start_applied=explanation.cold_start_applied,
            dead_components=explanation.dead_components,
            redistributed_weights=explanation.redistributed_weights,
            rank_in_candidates=explanation.rank_in_candidates,
            rank_in_feed=explanation.rank_in_feed,
            filtered_out_by=explanation.filtered_out_by,
        )
