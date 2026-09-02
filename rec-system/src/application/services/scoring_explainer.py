"""ScoringExplainer — per-component scoring breakdown for dev explainability."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any
from uuid import UUID

from src.domain.interfaces.config_repository import ConfigRepository
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.services.scorer import Scorer

_DEFAULT_SCORING_WEIGHTS: dict[str, float] = {
    "topic_match": 0.30,
    "embedding_sim": 0.25,
    "entity_match": 0.15,
    "sentiment_match": 0.05,
    "freshness": 0.15,
    "format_match": 0.10,
}
_DEFAULT_HALFLIFE_HOURS = 48


class NotFoundError(Exception):
    """Raised when user or content cannot be located."""


@dataclass(frozen=True)
class ComponentBreakdown:
    name: str
    value: float | None
    weight: float
    contribution: float
    details: dict = field(default_factory=dict)


@dataclass(frozen=True)
class ScoringExplanation:
    content_id: UUID
    raw_content_id: UUID
    published_content_id: UUID | None
    final_score: float
    components: list[ComponentBreakdown]
    cold_start_applied: bool
    dead_components: list[str]
    redistributed_weights: dict[str, float] | None
    rank_in_candidates: int | None
    rank_in_feed: int | None
    filtered_out_by: str | None


class ScoringExplainer:
    """Produces a per-component score breakdown for a (user_id, content_id) pair.

    Accepts either a raw_content.id or a published_content.id as content_id.
    """

    def __init__(
        self,
        scorer: Scorer,
        content_repo: ContentRepository,
        profile_repo: UserProfileRepository,
        entity_interest_repo: EntityInterestRepository,
        config_repo: ConfigRepository,
    ) -> None:
        self._scorer = scorer
        self._content_repo = content_repo
        self._profile_repo = profile_repo
        self._entity_interest_repo = entity_interest_repo
        self._config_repo = config_repo

    async def explain(
        self,
        user_id: UUID,
        content_id: UUID,
        include_rank: bool = False,
    ) -> ScoringExplanation:
        """Compute and return per-component scoring explanation.

        Args:
            user_id: UUID of the user.
            content_id: Either a posts_features.post_id or a published_content.id.
            include_rank: Reserved for future rank-in-feed computation (always None now).

        Raises:
            NotFoundError: When user profile or content is not found.
        """
        raw_content_id, published_content_id = await self._resolve_ids(content_id)

        features_list = await self._content_repo.get_by_ids([raw_content_id])
        if not features_list:
            raise NotFoundError(f"Content features not found for id={raw_content_id}")
        features = features_list[0]

        profile = await self._profile_repo.get_by_user_id(user_id)
        if profile is None:
            raise NotFoundError(f"User profile not found for user_id={user_id}")

        interests_list = await self._entity_interest_repo.get_top_for_user(user_id, limit=100)
        entity_interests_set = {e.entity_name for e in interests_list}
        entity_weights_dict = {e.entity_name: e.weight for e in interests_list}

        scoring_weights = await self._config_repo.get_config("scoring_weights") or _DEFAULT_SCORING_WEIGHTS
        ranking_params = await self._config_repo.get_config("ranking_params") or {}
        halflife_hours = ranking_params.get("freshness_halflife_hours", _DEFAULT_HALFLIFE_HOURS)
        config = {"scoring_weights": scoring_weights, "freshness_halflife_hours": halflife_hours}

        breakdown = self._scorer.explain_score(profile, features, entity_interests_set, config)

        # Augment entity_match with per-entity interest weights
        matched_entities: list[str] = breakdown["components"]["entity_match"].get("matched_entities", [])
        breakdown["components"]["entity_match"]["interest_weights"] = {
            e: entity_weights_dict[e]
            for e in matched_entities
            if e in entity_weights_dict
        }

        components = [
            ComponentBreakdown(
                name=name,
                value=comp["value"],
                weight=comp["weight"],
                contribution=comp["contribution"],
                details={k: v for k, v in comp.items() if k not in ("value", "weight", "contribution")},
            )
            for name, comp in breakdown["components"].items()
        ]

        return ScoringExplanation(
            content_id=content_id,
            raw_content_id=raw_content_id,
            published_content_id=published_content_id,
            final_score=breakdown["final_score"],
            components=components,
            cold_start_applied=breakdown["cold_start_applied"],
            dead_components=breakdown["dead_components"],
            redistributed_weights=breakdown["redistributed_weights"],
            rank_in_candidates=None,
            rank_in_feed=None,
            filtered_out_by=None,
        )

    async def _resolve_ids(self, content_id: UUID) -> tuple[UUID, UUID | None]:
        """Return (raw_content_id, published_content_id).

        Tries content_id as raw post_id first; falls back to published_content.id lookup.
        """
        features_list = await self._content_repo.get_by_ids([content_id])
        if features_list:
            pub_map = await self._content_repo.get_published_ids([content_id])
            return content_id, pub_map.get(content_id)

        raw_id = await self._content_repo.get_raw_id_by_published_id(content_id)
        if raw_id is None:
            raise NotFoundError(f"Content not found for id={content_id}")
        pub_map = await self._content_repo.get_published_ids([raw_id])
        return raw_id, pub_map.get(raw_id) or content_id
