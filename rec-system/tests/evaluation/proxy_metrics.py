"""Phase 4: Proxy metrics for feed quality evaluation.

Eight objective, deterministic metrics computed on any FeedResult.
Pure computation — no LLM, no DB writes. Used for regression detection.
"""
from __future__ import annotations

import logging
import math
import statistics
from dataclasses import dataclass
from uuid import UUID

from tests.evaluation.feed_evaluator import FeedItem, FeedResult

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ProxyMetrics:
    topic_entropy: float            # Shannon entropy of top-1 topic distribution (log2)
    freshness_median_hours: float   # median of item.age_hours
    pairwise_cosine_avg: float      # mean cos(e_i, e_j) over unique pairs (i<j) with embeddings
    topic_coverage: int             # count of distinct top-1 topics
    distance_to_profile_avg: float  # mean cos(profile.embedding, item.embedding)
    long_tail_ratio: float          # % of items from bottom-50% sources by post count
    dedup_pair_count: int           # count of unique pairs with cos > 0.85
    category_mass_balance: float    # Gini coefficient of top-1 topic distribution

    items_with_embedding: int
    total_items: int


class ProxyMetricsComputer:
    def __init__(self, content_repo) -> None:
        self._content_repo = content_repo

    async def compute(
        self,
        feed: FeedResult,
        user_profile,
    ) -> ProxyMetrics:
        items = feed.items
        total = len(items)

        emb_items = [i for i in items if len(i.embedding) > 0]
        n_with_emb = len(emb_items)

        if total > 0 and n_with_emb < total:
            pct_missing = (total - n_with_emb) / total * 100
            logger.warning(
                "%.1f%% of items missing embeddings (%d/%d)",
                pct_missing,
                total - n_with_emb,
                total,
            )

        source_counts: dict[UUID, int] = await self._content_repo.get_source_post_counts()

        return ProxyMetrics(
            topic_entropy=_compute_entropy(items),
            freshness_median_hours=_compute_freshness_median(items),
            pairwise_cosine_avg=_compute_pairwise_cosine(emb_items),
            topic_coverage=_compute_coverage(items),
            distance_to_profile_avg=_compute_distance_to_profile(emb_items, user_profile),
            long_tail_ratio=_compute_long_tail(items, source_counts),
            dedup_pair_count=_compute_dedup_pairs(emb_items),
            category_mass_balance=_compute_gini(items),
            items_with_embedding=n_with_emb,
            total_items=total,
        )


# ---------------------------------------------------------------------------
# Pure metric functions
# ---------------------------------------------------------------------------


def _compute_entropy(items: list[FeedItem]) -> float:
    if not items:
        logger.warning("topic_entropy: empty feed — returning nan")
        return float("nan")
    counts: dict[str, int] = {}
    for item in items:
        if item.topics:
            label = item.topics[0][0]
            counts[label] = counts.get(label, 0) + 1
    if not counts:
        return float("nan")
    total = sum(counts.values())
    entropy = 0.0
    for cnt in counts.values():
        p = cnt / total
        if p > 0:
            entropy -= p * math.log2(p)
    return entropy


def _compute_freshness_median(items: list[FeedItem]) -> float:
    if not items:
        logger.warning("freshness_median_hours: empty feed — returning nan")
        return float("nan")
    return statistics.median(i.age_hours for i in items)


def _compute_pairwise_cosine(emb_items: list[FeedItem]) -> float:
    """Mean cosine over unique pairs (i<j). Returns 0.0 with <2 eligible items."""
    if len(emb_items) < 2:
        return 0.0
    total = 0.0
    count = 0
    for i in range(len(emb_items)):
        for j in range(i + 1, len(emb_items)):
            total += _cosine(emb_items[i].embedding, emb_items[j].embedding)
            count += 1
    return total / count if count > 0 else 0.0


def _compute_coverage(items: list[FeedItem]) -> int:
    if not items:
        return 0
    return len({item.topics[0][0] for item in items if item.topics})


def _compute_distance_to_profile(emb_items: list[FeedItem], user_profile) -> float:
    profile_emb = getattr(user_profile, "embedding", None)
    if profile_emb is None:
        logger.warning("distance_to_profile_avg: profile.embedding is None — returning nan")
        return float("nan")
    profile_list = list(profile_emb)
    if _norm(profile_list) == 0.0:
        logger.warning("distance_to_profile_avg: profile embedding is zero-vector — returning nan")
        return float("nan")
    if not emb_items:
        logger.warning("distance_to_profile_avg: no items with embedding — returning nan")
        return float("nan")
    total = sum(_cosine(profile_list, item.embedding) for item in emb_items)
    return total / len(emb_items)


def _compute_long_tail(items: list[FeedItem], source_counts: dict[UUID, int]) -> float:
    if not items:
        return 0.0
    if not source_counts:
        return 0.0
    sorted_sources = sorted(source_counts.items(), key=lambda x: x[1])
    cutoff = len(sorted_sources) // 2
    bottom_half = {src for src, _ in sorted_sources[:cutoff]}
    bottom_count = sum(1 for item in items if item.source_id in bottom_half)
    return bottom_count / len(items)


def _compute_dedup_pairs(emb_items: list[FeedItem]) -> int:
    """Count unique pairs (i<j) where cosine(e_i, e_j) > 0.85.

    This is an embedding-concentration metric, NOT a dedup-pipeline correctness
    check. A high value does NOT indicate that EXACT/DUPLICATE posts leaked into
    the feed — the pipeline correctly filters those via Union-Find clustering.

    Expected behaviour in this corpus:
    - Entity-focused personas (e.g. Musk → all бизнес/технологии) will show
      counts of 20–75 because every post in the feed shares the same entity
      embedding anchor. This is correct feed behaviour, not a bug.
    - "Международные новости" dominates 59.6 % of the corpus; any feed heavy
      on that category will have many high-cosine pairs.
    - Values of 0–15 are typical for balanced personas.

    To detect true dedup leaks (EXACT/DUPLICATE posts in the feed), query
    data_flow.similarities directly and cross-reference with feed post_ids.
    """
    count = 0
    for i in range(len(emb_items)):
        for j in range(i + 1, len(emb_items)):
            if _cosine(emb_items[i].embedding, emb_items[j].embedding) > 0.85:
                count += 1
    return count


def _compute_gini(items: list[FeedItem]) -> float:
    """Gini coefficient of top-1 topic distribution.

    Formula: G = sum|p_i - p_j| / (2 * n * sum_p)
    where n = #distinct topics with p>0 and sum_p = 1.

    Special case: n=1 (all items on one topic) → returns 1.0 by convention
    (degenerate monopoly — maximum concentration, no pairs to compare).
    Empty feed → nan.
    """
    if not items:
        logger.warning("category_mass_balance: empty feed — returning nan")
        return float("nan")
    counts: dict[str, int] = {}
    for item in items:
        if item.topics:
            label = item.topics[0][0]
            counts[label] = counts.get(label, 0) + 1
    if not counts:
        return float("nan")
    total = sum(counts.values())
    probs = [c / total for c in counts.values()]
    n = len(probs)
    if n == 1:
        return 1.0
    pair_sum = sum(abs(probs[i] - probs[j]) for i in range(n) for j in range(n))
    return pair_sum / (2 * n * 1.0)


# ---------------------------------------------------------------------------
# Math utilities
# ---------------------------------------------------------------------------


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b:
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = _norm(a)
    norm_b = _norm(b)
    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0
    return dot / (norm_a * norm_b)


def _norm(v: list[float]) -> float:
    return math.sqrt(sum(x * x for x in v))
