"""GenerateFeedUseCase application use case."""
from __future__ import annotations

import os
import time
from collections import defaultdict
from typing import List, Optional, Set
from uuid import UUID

from src.infrastructure.metrics import rec_feed_request_latency_ms, rec_feed_requests_total

from src.application.dto.generate_feed import GenerateFeedRequest, GenerateFeedResponse
from src.domain.entities.content_features import ContentFeatures
from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository
from src.domain.interfaces.config_repository import ConfigRepository
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.interfaces.live_profile_computer import LiveProfileComputer
from src.domain.interfaces.relevance_reranker import RelevanceReranker
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.services.diversity_filter import DiversityFilter
from src.domain.services.narrow_scorer import NarrowScorer
from src.domain.services.profile_updater import ProfileUpdater
from src.domain.services.scorer import Scorer
from src.domain.services.signal_classifier import SignalClassifier
from src.domain.value_objects.post_score import PostScore


def _minmax(values: list[float]) -> list[float]:
    """Normalize a list to [0, 1]. Returns list of 0.0 on zero variance."""
    if not values:
        return []
    lo = min(values)
    hi = max(values)
    if hi == lo:
        return [0.0] * len(values)
    return [(v - lo) / (hi - lo) for v in values]


class GenerateFeedUseCase:
    """Orchestrates the full feed generation pipeline.

    1. Process unprocessed events for this user inline
    2. Load profile, config, blacklist
    3. Candidate retrieval:
         * NARROW path (request.include_source_ids) → get_candidates_by_sources
         * Standard two-stage path (freshness + embedding) otherwise
       Both paths filter by effective_exclude = request.exclude ∪ blacklist.
    4. Dedup clustering (both paths)
    5. Score — Scorer (NORMAL) or NarrowScorer (NARROW)
    6. DiversityFilter applied only in NORMAL; skipped in NARROW
    7. Map to published_content IDs and return GenerateFeedResponse.
    """

    def __init__(
        self,
        profile_repo: UserProfileRepository,
        content_repo: ContentRepository,
        entity_interest_repo: EntityInterestRepository,
        interaction_repo: InteractionRepository,
        config_repo: ConfigRepository,
        scorer: Scorer,
        diversity_filter: DiversityFilter,
        signal_classifier: SignalClassifier,
        profile_updater: ProfileUpdater,
        blocked_sources_repo: BlockedSourcesRepository,
        narrow_scorer: NarrowScorer,
        live_profile_service: Optional[LiveProfileComputer] = None,
        reranker: Optional[RelevanceReranker] = None,
        context_builder=None,
    ) -> None:
        self._profile_repo = profile_repo
        self._content_repo = content_repo
        self._entity_interest_repo = entity_interest_repo
        self._interaction_repo = interaction_repo
        self._config_repo = config_repo
        self._scorer = scorer
        self._diversity_filter = diversity_filter
        self._signal_classifier = signal_classifier
        self._profile_updater = profile_updater
        self._blocked_sources_repo = blocked_sources_repo
        self._narrow_scorer = narrow_scorer
        self._live_profile_service = live_profile_service
        self._reranker = reranker
        self._context_builder = context_builder

    async def execute(self, request: GenerateFeedRequest) -> GenerateFeedResponse:
        start_time = time.monotonic()
        user_id: UUID = request.user_id
        feed_size: int = request.feed_size
        ranking_mode: str = (request.ranking_mode or "NORMAL").upper()
        include = list(request.include_source_ids or [])
        _source_label = "narrow" if include else "personalized"

        # Load config — fall back to empty dict if not found
        config = await self._config_repo.get_config("feed") or {}

        # On-demand profile update from unprocessed interactions
        events_processed = await self._process_user_events_inline(user_id, config)

        # Load profile and blacklist concurrently is fine but keep sequential for clarity
        profile = await self._profile_repo.get_by_user_id(user_id)

        # Live profile blending (behind REC_FEATURE_LIVE_PROFILE env flag)
        if (
            self._live_profile_service is not None
            and os.environ.get("REC_FEATURE_LIVE_PROFILE", "false").lower() == "true"
            and profile is not None
        ):
            live_config = await self._config_repo.get_config("live_profile_params") or {}
            blend_alpha = float(live_config.get("blend", 0.6))
            recent_n = int(live_config.get("recent_n", 15))
            decay_hours = float(live_config.get("decay_hours", 24.0))
            live_emb = await self._live_profile_service.compute(
                user_id=user_id,
                stored_profile=profile,
                blend_alpha=blend_alpha,
                recent_n=recent_n,
                decay_hours=decay_hours,
            )
            if live_emb is not None:
                profile = profile.with_updated_embedding(live_emb)

        blacklist: Set[UUID] = await self._blocked_sources_repo.get_source_ids_for_user(user_id)

        # Merge blacklist into the effective exclude set; caller exclude + blacklist.
        request_exclude = set(request.exclude_source_ids or [])
        effective_exclude = request_exclude | set(blacklist)
        effective_exclude_list: List[UUID] = list(effective_exclude) if effective_exclude else []

        # Tunables
        max_age_hours = config.get("max_age_hours", 48)
        freshness_limit = config.get("freshness_candidate_limit", 500)
        embedding_limit = config.get("embedding_candidate_limit", 500)
        narrow_limit = freshness_limit + embedding_limit

        # Candidate retrieval — branch on include_source_ids (narrow path)
        if include:
            all_candidates = await self._content_repo.get_candidates_by_sources(
                include_source_ids=include,
                exclude_source_ids=effective_exclude_list,
                max_age_hours=max_age_hours,
                limit=narrow_limit,
            )
        else:
            freshness_candidates = await self._content_repo.get_candidates_by_freshness(
                max_age_hours=max_age_hours,
                limit=freshness_limit,
                exclude_source_ids=effective_exclude_list,
            )

            embedding_candidates: List[ContentFeatures] = []
            if profile is not None and profile.embedding is not None:
                embedding_candidates = await self._content_repo.get_candidates_by_embedding(
                    embedding=profile.embedding,
                    limit=embedding_limit,
                    exclude_source_ids=effective_exclude_list,
                )

            seen: Set[UUID] = set()
            all_candidates = []
            for content in freshness_candidates + embedding_candidates:
                if content.post_id in seen:
                    continue
                seen.add(content.post_id)
                all_candidates.append(content)

        if not all_candidates or profile is None:
            elapsed_ms = int((time.monotonic() - start_time) * 1000)
            rec_feed_requests_total.labels(source=_source_label).inc()
            rec_feed_request_latency_ms.labels(source=_source_label).observe(elapsed_ms)
            return GenerateFeedResponse(
                feed=[],
                meta={
                    "total": 0,
                    "candidates_scored": 0,
                    "profile_events_processed": events_processed,
                    "generation_time_ms": elapsed_ms,
                    "unmapped_posts": 0,
                },
            )

        # Dedup clustering — applies in both modes
        dedup_config = await self._config_repo.get_config("dedup") or {}
        if dedup_config.get("enable_dedup_clustering", True):
            candidate_post_ids = [c.post_id for c in all_candidates]
            clusters = await self._content_repo.get_dedup_clusters(candidate_post_ids)
            cluster_groups: dict = defaultdict(list)
            for c in all_candidates:
                cluster_groups[clusters.get(c.post_id, id(c))].append(c)
            all_candidates = [
                max(group, key=lambda c: c.post_date)
                for group in cluster_groups.values()
            ]

        # Scoring + filtering — diverges on ranking_mode
        if ranking_mode == "NARROW":
            # Pure cosine + freshness; no diversity filter.
            narrow_scores = self._narrow_scorer.score_batch(all_candidates, profile, config)
            narrow_scores.sort(key=lambda ps: ps.score, reverse=True)
            filtered: List[PostScore] = narrow_scores[:feed_size]
        else:
            entity_interests_list = await self._entity_interest_repo.get_top_for_user(
                user_id, limit=100
            )
            entity_interests_set = {ei.entity_name for ei in entity_interests_list}

            scoring_input = [(content, entity_interests_set) for content in all_candidates]
            scored = self._scorer.score_batch(scoring_input, profile, config)
            scored.sort(key=lambda ps: ps.score, reverse=True)

            # Cross-encoder reranking (Phase C) — behind REC_FEATURE_RERANK flag
            if (
                self._reranker is not None
                and self._context_builder is not None
                and os.environ.get("REC_FEATURE_RERANK", "false").lower() == "true"
            ):
                rerank_cfg = await self._config_repo.get_config("rerank_params") or {}
                top_n = int(rerank_cfg.get("top_k", 100))
                blend = float(rerank_cfg.get("weight", 0.5))
                min_cands = int(rerank_cfg.get("min_candidates", 30))

                if len(scored) >= min_cands:
                    pre_sorted = scored[:top_n]
                    pre_sorted_ids = {ps.post_id for ps in pre_sorted}
                    content_map_rerank = {
                        c.post_id: c
                        for c in all_candidates
                        if c.post_id in pre_sorted_ids
                    }
                    pre_sorted_content = [
                        content_map_rerank[ps.post_id]
                        for ps in pre_sorted
                        if ps.post_id in content_map_rerank
                    ]

                    user_ctx = await self._context_builder.build(
                        profile=profile,
                        entity_interests=entity_interests_list,
                    )
                    rerank_pairs = await self._reranker.rerank(
                        user_ctx, pre_sorted_content, top_k=top_n
                    )
                    rerank_scores = {post_id: s for post_id, s in rerank_pairs}

                    norm_original = _minmax([ps.score for ps in pre_sorted])
                    norm_rerank = _minmax(
                        [rerank_scores.get(ps.post_id, 0.0) for ps in pre_sorted]
                    )

                    # Blend original + rerank scores in-place on PostScore objects
                    reranked: List[PostScore] = []
                    for ps, o, r in zip(pre_sorted, norm_original, norm_rerank):
                        blended = (1.0 - blend) * o + blend * r
                        reranked.append(PostScore(post_id=ps.post_id, score=blended))

                    # Items beyond top_n keep original scoring score unchanged
                    beyond = scored[top_n:]

                    reranked.sort(key=lambda ps: ps.score, reverse=True)
                    scored = reranked + beyond

            content_map = {c.post_id: c for c in all_candidates}
            diversity_input = [
                (content_map[ps.post_id], ps.score)
                for ps in scored
                if ps.post_id in content_map
            ]

            related_map: Optional[dict] = None
            if dedup_config.get("enable_related_spacing", True):
                candidate_post_ids = [c.post_id for c in all_candidates]
                related_map = await self._content_repo.get_related_pairs(candidate_post_ids)

            filtered = self._diversity_filter.filter(
                diversity_input, feed_size, config, related_map=related_map
            )

        # Map to published_content IDs; exclude unmapped posts
        feed_post_ids = [ps.post_id for ps in filtered]
        published_map = await self._content_repo.get_published_ids(feed_post_ids)

        # Compute per-item scoring breakdown when requested (NORMAL mode only)
        breakdown_by_post_id: dict = {}
        if request.include_breakdown and ranking_mode != "NARROW":
            score_config = {
                "scoring_weights": config.get("scoring_weights", {}),
                "freshness_halflife_hours": config.get("halflife_hours", 48),
            }
            for ps in filtered:
                if ps.post_id in content_map:
                    breakdown_by_post_id[ps.post_id] = self._scorer.explain_score(
                        profile, content_map[ps.post_id], entity_interests_set, score_config
                    )

        feed: list[dict] = []
        for ps in filtered:
            pub_id = published_map.get(ps.post_id)
            if pub_id is not None:
                feed.append({"post_id": str(pub_id), "score": ps.score, "raw_post_id": str(ps.post_id)})

        unmapped_count = len(filtered) - len(feed)
        elapsed_ms = int((time.monotonic() - start_time) * 1000)
        rec_feed_requests_total.labels(source=_source_label).inc()
        rec_feed_request_latency_ms.labels(source=_source_label).observe(elapsed_ms)
        meta = {
            "total": len(feed),
            "candidates_scored": len(all_candidates),
            "profile_events_processed": events_processed,
            "generation_time_ms": elapsed_ms,
            "unmapped_posts": unmapped_count,
        }

        if not request.include_breakdown:
            return GenerateFeedResponse(feed=feed, meta=meta)

        # Build items_detailed parallel to feed
        items_detailed: list[dict] = []
        for item in feed:
            raw_pid = UUID(item["raw_post_id"])
            bd = breakdown_by_post_id.get(raw_pid)
            if bd is not None:
                components = {
                    name: (comp["value"] if comp["value"] is not None else 0.0)
                    for name, comp in bd["components"].items()
                }
            else:
                components = {k: 0.0 for k in (
                    "topic_match", "embedding_sim", "entity_match",
                    "sentiment_match", "freshness", "format_match"
                )}
            items_detailed.append({
                "content_id": item["post_id"],
                "raw_content_id": item["raw_post_id"],
                "final_score": bd["final_score"] if bd else item["score"],
                "scoring_components": components,
                "cold_start_applied": bd["cold_start_applied"] if bd else False,
                "rerank_score": None,
                "filtered_out_by": None,
            })

        latency_breakdown = {
            "retrieval_ms": 0,
            "scoring_ms": 0,
            "rerank_ms": 0,
            "total_ms": elapsed_ms,
        }

        profile_snapshot: dict = {}
        if profile is not None:
            profile_snapshot = {
                "interaction_count": profile.interaction_count,
                "cold_start": profile.cold_start,
            }

        feature_flags = {
            "live_profile": os.environ.get("REC_FEATURE_LIVE_PROFILE", "false").lower() == "true",
            "hot_arrival": os.environ.get("REC_FEATURE_HOT_ARRIVAL", "false").lower() == "true",
            "rerank": os.environ.get("REC_FEATURE_RERANK", "false").lower() == "true",
        }

        return GenerateFeedResponse(
            feed=feed,
            meta=meta,
            items_detailed=items_detailed,
            latency_breakdown=latency_breakdown,
            profile_snapshot=profile_snapshot,
            feature_flags=feature_flags,
        )

    async def _process_user_events_inline(self, user_id: UUID, config: dict) -> int:
        """Process unprocessed interactions for the user inline before feed generation."""
        interactions = await self._interaction_repo.get_unprocessed_for_user(user_id)
        if not interactions:
            return 0

        profile = await self._profile_repo.get_by_user_id(user_id)
        if profile is None:
            return 0

        signals = self._signal_classifier.classify_batch(interactions)

        post_ids = list({s.post_id for s in signals})
        features_list = await self._content_repo.get_by_ids(post_ids)
        content_features_map = {cf.post_id: cf for cf in features_list}

        updated_profile, entity_changes = self._profile_updater.apply_batch(
            profile=profile,
            signals=signals,
            content_features_map=content_features_map,
            config=config,
        )

        await self._profile_repo.save(updated_profile)

        for entity_interest in entity_changes:
            await self._entity_interest_repo.upsert(entity_interest)

        interaction_ids = [inter.id for inter in interactions]
        await self._interaction_repo.mark_processed(interaction_ids)

        return len(interactions)
