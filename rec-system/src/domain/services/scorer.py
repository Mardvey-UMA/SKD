"""Scorer domain service."""
from __future__ import annotations

import math
from datetime import datetime, timezone
from typing import Dict, Any, List, Set, Tuple

from src.domain.entities.user_profile import UserProfile
from src.domain.entities.content_features import ContentFeatures
from src.domain.value_objects.post_score import PostScore


class Scorer:
    """Computes the 6-component scoring formula for (post, profile) pairs.

    Implements design/05-scoring-ranking.md:
    - topic_match, embedding_sim, entity_match, sentiment_match, freshness, format_match
    - Cold start dynamic weight redistribution
    """

    def score_batch(
        self,
        candidates: List[Tuple[ContentFeatures, Set[str]]],
        profile: UserProfile,
        config: Dict[str, Any],
    ) -> List[PostScore]:
        """Score all candidates and return sorted list (descending)."""
        scoring_weights = config.get("scoring_weights", {
            "topic_match": 0.30,
            "embedding_sim": 0.25,
            "entity_match": 0.15,
            "sentiment_match": 0.05,
            "freshness": 0.15,
            "format_match": 0.10,
        })
        halflife_hours = config.get("freshness_halflife_hours", 48)

        results = []
        for content, entity_interests in candidates:
            score = self._compute_score(profile, content, entity_interests, scoring_weights, halflife_hours)
            results.append(PostScore(post_id=content.post_id, score=score))

        results.sort(key=lambda ps: ps.score, reverse=True)
        return results

    def _compute_score(
        self,
        profile: UserProfile,
        content: ContentFeatures,
        entity_interests: Set[str],
        weights: Dict[str, float],
        halflife_hours: float,
    ) -> float:
        """Compute weighted score with cold start redistribution."""
        # Compute component values
        topic_val = self.compute_topic_match(profile, content)
        embedding_val = self.compute_embedding_sim(profile, content)
        entity_val = self.compute_entity_match(content, entity_interests)
        sentiment_val = self.compute_sentiment_match(profile, content)
        freshness_val = self.compute_freshness(content, halflife_hours)
        format_val = self.compute_format_match(profile, content)

        # Determine dead components for cold start redistribution
        embedding_is_dead = profile.embedding is None
        entity_is_dead = len(entity_interests) == 0 and len(content.all_entities()) == 0

        dead_weight = 0.0
        if embedding_is_dead:
            dead_weight += weights.get("embedding_sim", 0.25)
        if entity_is_dead:
            dead_weight += weights.get("entity_match", 0.15)

        if dead_weight > 0.0:
            # Redistribute dead weight proportionally among active components
            active_components = {
                "topic_match": (topic_val, weights.get("topic_match", 0.30)),
                "sentiment_match": (sentiment_val, weights.get("sentiment_match", 0.05)),
                "freshness": (freshness_val, weights.get("freshness", 0.15)),
                "format_match": (format_val, weights.get("format_match", 0.10)),
            }
            if not embedding_is_dead:
                active_components["embedding_sim"] = (embedding_val, weights.get("embedding_sim", 0.25))
            if not entity_is_dead:
                active_components["entity_match"] = (entity_val, weights.get("entity_match", 0.15))

            active_weight_sum = sum(w for _, w in active_components.values())
            if active_weight_sum == 0.0:
                return 0.0

            score = 0.0
            for component_val, component_w in active_components.values():
                redistributed_w = component_w + dead_weight * (component_w / active_weight_sum)
                score += redistributed_w * component_val
            return score

        # No cold start: standard weighted sum
        score = (
            weights.get("topic_match", 0.30) * topic_val
            + weights.get("embedding_sim", 0.25) * embedding_val
            + weights.get("entity_match", 0.15) * entity_val
            + weights.get("sentiment_match", 0.05) * sentiment_val
            + weights.get("freshness", 0.15) * freshness_val
            + weights.get("format_match", 0.10) * format_val
        )
        return score

    def compute_topic_match(self, profile: UserProfile, content: ContentFeatures) -> float:
        """Sum of profile.topic_vector[topic] * post_score, clamped to [0, 1]."""
        topic_scores = content.topic_dict()
        match = sum(
            profile.topic_vector.get(topic) * score
            for topic, score in topic_scores.items()
        )
        return min(max(match, 0.0), 1.0)

    def compute_embedding_sim(self, profile: UserProfile, content: ContentFeatures) -> float:
        """Cosine similarity (dot product for L2-normalized), clamped to [0, 1]."""
        if profile.embedding is None or content.embedding is None:
            return 0.0
        dot = sum(p * c for p, c in zip(profile.embedding, content.embedding))
        return max(0.0, min(dot, 1.0))

    def compute_entity_match(self, content: ContentFeatures, entity_interests: Set[str]) -> float:
        """min(count of matching entities / 3, 1.0). Returns 0.0 if no entities."""
        all_entities = content.all_entities()
        if not all_entities:
            return 0.0
        matches = sum(1 for e in all_entities if e in entity_interests)
        return min(matches / 3.0, 1.0)

    def compute_sentiment_match(self, profile: UserProfile, content: ContentFeatures) -> float:
        """profile.sentiment_prefs[post.sentiment]."""
        if content.sentiment is None:
            return 0.0
        return profile.sentiment_prefs.get(content.sentiment)

    def compute_freshness(self, content: ContentFeatures, halflife_hours: float) -> float:
        """exp(-0.693 * hours_since_post / halflife_hours)."""
        now = datetime.now(timezone.utc)
        post_date = content.post_date
        if post_date.tzinfo is None:
            post_date = post_date.replace(tzinfo=timezone.utc)
        hours_since = (now - post_date).total_seconds() / 3600.0
        hours_since = max(0.0, hours_since)
        return math.exp(-0.693 * hours_since / halflife_hours)

    def compute_format_match(self, profile: UserProfile, content: ContentFeatures) -> float:
        """length_match * complexity_match formula."""
        if content.word_count is None or content.complexity is None:
            return 0.0
        avg_length = profile.format_prefs.avg_length
        avg_complexity = profile.format_prefs.avg_complexity

        length_match = max(0.0, 1.0 - abs(content.word_count - avg_length) / 500)
        complexity_match = max(0.0, 1.0 - abs(content.complexity - avg_complexity))

        return length_match * complexity_match

    def explain_score(
        self,
        profile: UserProfile,
        content: ContentFeatures,
        entity_interests: Set[str],
        config: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Return per-component scoring breakdown for explainability.

        Additive — does not change the score_batch / _compute_score API.
        Returns a dict with keys: final_score, components, cold_start_applied,
        dead_components, redistributed_weights.
        """
        scoring_weights = config.get("scoring_weights", {
            "topic_match": 0.30,
            "embedding_sim": 0.25,
            "entity_match": 0.15,
            "sentiment_match": 0.05,
            "freshness": 0.15,
            "format_match": 0.10,
        })
        halflife_hours = config.get("freshness_halflife_hours", 48)

        topic_val = self.compute_topic_match(profile, content)
        embedding_val = self.compute_embedding_sim(profile, content)
        entity_val = self.compute_entity_match(content, entity_interests)
        sentiment_val = self.compute_sentiment_match(profile, content)
        freshness_val = self.compute_freshness(content, halflife_hours)
        format_val = self.compute_format_match(profile, content)

        embedding_is_dead = profile.embedding is None
        entity_is_dead = len(entity_interests) == 0 and len(content.all_entities()) == 0

        dead_components: List[str] = []
        if embedding_is_dead:
            dead_components.append("embedding_sim")
        if entity_is_dead:
            dead_components.append("entity_match")

        base_weights: Dict[str, float] = {
            "topic_match": scoring_weights.get("topic_match", 0.30),
            "embedding_sim": scoring_weights.get("embedding_sim", 0.25),
            "entity_match": scoring_weights.get("entity_match", 0.15),
            "sentiment_match": scoring_weights.get("sentiment_match", 0.05),
            "freshness": scoring_weights.get("freshness", 0.15),
            "format_match": scoring_weights.get("format_match", 0.10),
        }

        redistributed_weights: Dict[str, float] | None = None
        effective_weights = dict(base_weights)

        if dead_components:
            dead_weight = sum(base_weights[k] for k in dead_components)
            active_names = [k for k in base_weights if k not in dead_components]
            active_weight_sum = sum(base_weights[k] for k in active_names)
            if active_weight_sum > 0:
                redistributed_weights = {}
                for name in base_weights:
                    if name in dead_components:
                        redistributed_weights[name] = 0.0
                    else:
                        w = base_weights[name]
                        redistributed_weights[name] = w + dead_weight * (w / active_weight_sum)
                effective_weights = redistributed_weights

        component_values: Dict[str, float | None] = {
            "topic_match": topic_val,
            "embedding_sim": None if embedding_is_dead else embedding_val,
            "entity_match": None if entity_is_dead else entity_val,
            "sentiment_match": sentiment_val,
            "freshness": freshness_val,
            "format_match": format_val,
        }

        final_score = 0.0
        components_result: Dict[str, Any] = {}
        for name, val in component_values.items():
            eff_w = effective_weights.get(name, 0.0)
            effective_val = val if val is not None else 0.0
            contribution = eff_w * effective_val
            final_score += contribution
            components_result[name] = {
                "value": val,
                "weight": eff_w,
                "contribution": contribution,
            }

        # Component-specific detail fields
        topic_dict = content.topic_dict()
        components_result["topic_match"]["matched_topics"] = [
            {"topic": t, "profile_weight": profile.topic_vector.get(t), "post_score": s}
            for t, s in topic_dict.items()
        ]

        all_entities = content.all_entities()
        matched_entities = [e for e in all_entities if e in entity_interests]
        components_result["entity_match"]["matched_entities"] = matched_entities

        components_result["sentiment_match"]["post_sentiment"] = content.sentiment
        components_result["sentiment_match"]["profile_preference"] = profile.sentiment_prefs.to_dict()

        now = datetime.now(timezone.utc)
        post_date = content.post_date
        if post_date.tzinfo is None:
            post_date = post_date.replace(tzinfo=timezone.utc)
        age_hours = max(0.0, (now - post_date).total_seconds() / 3600.0)
        components_result["freshness"]["age_hours"] = round(age_hours, 2)
        components_result["freshness"]["half_life_hours"] = halflife_hours

        components_result["format_match"]["post_word_count"] = content.word_count
        components_result["format_match"]["profile_preferred_word_count_avg"] = profile.format_prefs.avg_length
        components_result["format_match"]["post_complexity"] = content.complexity
        components_result["format_match"]["profile_preferred_complexity_avg"] = profile.format_prefs.avg_complexity

        return {
            "final_score": final_score,
            "components": components_result,
            "cold_start_applied": bool(dead_components),
            "dead_components": dead_components,
            "redistributed_weights": redistributed_weights,
        }
