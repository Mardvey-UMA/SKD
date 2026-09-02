"""RED tests for Phase 4: ProxyMetricsComputer with 8 objective proxy metrics.

All tests are unit-level — synthetic FeedResults built by hand, no DB.
"""
from __future__ import annotations

import math
import uuid
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock
from uuid import UUID

import pytest

from tests.evaluation.feed_evaluator import FeedItem, FeedResult
from tests.evaluation.proxy_metrics import ProxyMetrics, ProxyMetricsComputer


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _uuid() -> UUID:
    return uuid.uuid4()


def make_item(
    topic: str,
    embedding: list[float],
    age_h: float,
    source_id: UUID | None = None,
    position: int = 1,
) -> FeedItem:
    return FeedItem(
        position=position,
        post_id=_uuid(),
        raw_content_id=_uuid(),
        title="test",
        content_snippet="test",
        topics=[(topic, 0.9)] if topic else [],
        sentiment=("NEUTRAL", 0.5),
        entities={},
        embedding=embedding,
        text_metrics={},
        source_id=source_id or _uuid(),
        age_hours=age_h,
    )


def _make_feed(items: list[FeedItem]) -> FeedResult:
    return FeedResult(
        user_id=_uuid(),
        persona_id="test",
        version="test",
        seed=42,
        run_number=1,
        generated_at=datetime.now(timezone.utc),
        items=items,
        request_latency_ms=100.0,
        count=len(items),
    )


def _make_profile(embedding: list[float] | None) -> object:
    profile = MagicMock()
    profile.embedding = embedding
    return profile


def _mock_repo(source_counts: dict[UUID, int] | None = None) -> AsyncMock:
    repo = AsyncMock()
    repo.get_source_post_counts.return_value = source_counts or {}
    return repo


# ---------------------------------------------------------------------------
# 1. topic_entropy
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_entropy_uniform_topics():
    """8 items across 4 topics (2 each) → entropy = log2(4) = 2.0."""
    items = [
        make_item("A", [1, 0, 0], 1.0),
        make_item("A", [1, 0, 0], 1.0),
        make_item("B", [0, 1, 0], 1.0),
        make_item("B", [0, 1, 0], 1.0),
        make_item("C", [0, 0, 1], 1.0),
        make_item("C", [0, 0, 1], 1.0),
        make_item("D", [1, 1, 0], 1.0),
        make_item("D", [1, 1, 0], 1.0),
    ]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert abs(result.topic_entropy - 2.0) < 1e-9


@pytest.mark.unit
@pytest.mark.asyncio
async def test_entropy_mono_topic():
    """10 items all same topic → entropy = 0.0."""
    items = [make_item("политика", [1, 0, 0], 1.0) for _ in range(10)]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.topic_entropy == pytest.approx(0.0, abs=1e-9)


# ---------------------------------------------------------------------------
# 2. freshness_median_hours
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_freshness_median_basic():
    """Ages [1, 2, 3, 4, 5] → median = 3.0."""
    items = [make_item("A", [1, 0, 0], float(h)) for h in [1, 2, 3, 4, 5]]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.freshness_median_hours == pytest.approx(3.0)


# ---------------------------------------------------------------------------
# 3. pairwise_cosine_avg
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_pairwise_cosine_identical_embeddings():
    """5 items with same embedding → pairwise avg ≈ 1.0."""
    emb = [1.0, 0.0, 0.0]
    items = [make_item("A", emb, 1.0) for _ in range(5)]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile(emb))
    assert result.pairwise_cosine_avg == pytest.approx(1.0, abs=1e-6)


@pytest.mark.unit
@pytest.mark.asyncio
async def test_pairwise_cosine_orthogonal():
    """[1,0,0] and [0,1,0] → pairwise cosine = 0.0."""
    items = [
        make_item("A", [1.0, 0.0, 0.0], 1.0),
        make_item("B", [0.0, 1.0, 0.0], 1.0),
    ]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.pairwise_cosine_avg == pytest.approx(0.0, abs=1e-9)


# ---------------------------------------------------------------------------
# 4. topic_coverage
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_topic_coverage_counts_distinct():
    """10 items across 5 topics (2 each) → coverage = 5."""
    topics = ["A", "B", "C", "D", "E"]
    items = [make_item(t, [1, 0, 0], 1.0) for t in topics for _ in range(2)]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.topic_coverage == 5


# ---------------------------------------------------------------------------
# 5. distance_to_profile_avg
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_distance_to_profile_identical_direction():
    """profile=[1,0,0], all items=[1,0,0] → distance avg ≈ 1.0."""
    emb = [1.0, 0.0, 0.0]
    items = [make_item("A", emb, 1.0) for _ in range(5)]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile(emb))
    assert result.distance_to_profile_avg == pytest.approx(1.0, abs=1e-6)


# ---------------------------------------------------------------------------
# 6. long_tail_ratio
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_long_tail_ratio_bottom_half():
    """4 sources with counts [100, 50, 10, 5]. Items from bottom-50% sources → ratio=1.0."""
    src_a = _uuid()  # count=100 (top half)
    src_b = _uuid()  # count=50  (top half)
    src_c = _uuid()  # count=10  (bottom half)
    src_d = _uuid()  # count=5   (bottom half)

    source_counts = {src_a: 100, src_b: 50, src_c: 10, src_d: 5}

    items = [
        make_item("A", [1, 0, 0], 1.0, source_id=src_c),
        make_item("B", [0, 1, 0], 1.0, source_id=src_d),
    ]
    feed = _make_feed(items)

    repo = _mock_repo(source_counts)
    computer = ProxyMetricsComputer(content_repo=repo)
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.long_tail_ratio == pytest.approx(1.0)


# ---------------------------------------------------------------------------
# 7. dedup_pair_count
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_dedup_pair_count_detects_duplicates():
    """3 identical items → 3 pairs; 2 identical + 1 orthogonal → 1 pair."""
    emb_same = [1.0, 0.0, 0.0]
    emb_diff = [0.0, 1.0, 0.0]

    # Case A: 3 identical
    items_a = [make_item("A", emb_same, 1.0) for _ in range(3)]
    feed_a = _make_feed(items_a)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result_a = await computer.compute(feed_a, _make_profile(emb_same))
    assert result_a.dedup_pair_count == 3

    # Case B: 2 identical + 1 orthogonal
    items_b = [
        make_item("A", emb_same, 1.0),
        make_item("A", emb_same, 1.0),
        make_item("B", emb_diff, 1.0),
    ]
    feed_b = _make_feed(items_b)
    result_b = await computer.compute(feed_b, _make_profile(emb_same))
    assert result_b.dedup_pair_count == 1


# ---------------------------------------------------------------------------
# 8. category_mass_balance (Gini)
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_category_mass_balance_perfect_balance():
    """Uniform distribution across 4 topics → Gini = 0."""
    items = [make_item(t, [1, 0, 0], 1.0) for t in ["A", "B", "C", "D"] for _ in range(3)]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.category_mass_balance == pytest.approx(0.0, abs=1e-9)


@pytest.mark.unit
@pytest.mark.asyncio
async def test_category_mass_balance_monopoly():
    """All items on one topic → Gini = 1.0 (degenerate monopoly, n=1 special case)."""
    items = [make_item("политика", [1, 0, 0], 1.0) for _ in range(10)]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))
    assert result.category_mass_balance == pytest.approx(1.0)


# ---------------------------------------------------------------------------
# 9. Edge cases
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_empty_feed_no_crash():
    """Empty feed → ProxyMetrics with nans/zeros where documented, no exception."""
    feed = _make_feed([])
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile([1.0, 0.0, 0.0]))

    assert isinstance(result, ProxyMetrics)
    assert math.isnan(result.topic_entropy)
    assert math.isnan(result.freshness_median_hours)
    assert result.pairwise_cosine_avg == 0.0
    assert result.topic_coverage == 0
    assert result.long_tail_ratio == 0.0
    assert result.dedup_pair_count == 0
    assert math.isnan(result.category_mass_balance)
    assert result.total_items == 0
    assert result.items_with_embedding == 0


@pytest.mark.unit
@pytest.mark.asyncio
async def test_missing_embeddings_excluded_from_cos_metrics():
    """Half items have empty embedding → pairwise & distance computed on the rest only."""
    emb = [1.0, 0.0, 0.0]
    items = [
        make_item("A", emb, 1.0),
        make_item("A", emb, 1.0),
        make_item("A", [], 1.0),   # no embedding
        make_item("A", [], 1.0),   # no embedding
    ]
    feed = _make_feed(items)
    computer = ProxyMetricsComputer(content_repo=_mock_repo())
    result = await computer.compute(feed, _make_profile(emb))

    assert result.items_with_embedding == 2
    assert result.total_items == 4
    # Pairwise computed on 2 items with same embedding → avg = 1.0
    assert result.pairwise_cosine_avg == pytest.approx(1.0, abs=1e-6)
    # Profile distance computed only on the 2 items with embedding
    assert result.distance_to_profile_avg == pytest.approx(1.0, abs=1e-6)
