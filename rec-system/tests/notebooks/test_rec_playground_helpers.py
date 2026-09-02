"""Unit tests for rec_playground_helpers module — Phase 7 evaluation harness."""
from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID, uuid4

import pytest

# Ensure project root and notebooks/ are on sys.path
_ROOT = Path(__file__).parent.parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

_NOTEBOOKS = _ROOT / "notebooks"
if str(_NOTEBOOKS) not in sys.path:
    sys.path.insert(0, str(_NOTEBOOKS))

from notebooks.rec_playground_helpers import (  # noqa: E402
    color_code_position,
    render_diff_matrix,
    render_explainability_bars,
    render_metrics_table,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def _make_feed_result(persona_id: str, version: str, post_ids: list[UUID] | None = None):
    """Build a minimal FeedResult-like dict for tests (not importing FeedResult directly)."""
    from tests.evaluation.feed_evaluator import FeedItem, FeedResult

    items = []
    for i, pid in enumerate(post_ids or [uuid4() for _ in range(5)]):
        items.append(
            FeedItem(
                position=i + 1,
                post_id=pid,
                raw_content_id=uuid4(),
                title=f"Title {i + 1}",
                content_snippet=f"Snippet {i + 1}",
                topics=[("технологии", 0.8), ("наука", 0.4)],
                sentiment=("POS", 0.9),
                entities={},
                embedding=[0.1] * 312,
                text_metrics={"word_count": 100, "reading_time": 1.0, "complexity": 5.0},
                source_id=uuid4(),
                age_hours=2.0,
                scoring_breakdown=None,
            )
        )
    return FeedResult(
        user_id=uuid4(),
        persona_id=persona_id,
        version=version,
        seed=42,
        run_number=1,
        generated_at=datetime.now(timezone.utc),
        items=items,
        request_latency_ms=50.0,
        count=len(items),
        metadata={},
    )


def _make_proxy_metrics(topic_entropy: float = 2.5):
    """Build a ProxyMetrics with default values."""
    from tests.evaluation.proxy_metrics import ProxyMetrics

    return ProxyMetrics(
        topic_entropy=topic_entropy,
        freshness_median_hours=12.0,
        pairwise_cosine_avg=0.3,
        topic_coverage=4,
        distance_to_profile_avg=0.7,
        long_tail_ratio=0.2,
        dedup_pair_count=0,
        category_mass_balance=0.5,
        items_with_embedding=5,
        total_items=5,
    )


# ---------------------------------------------------------------------------
# color_code_position
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_color_code_position_top5():
    """Positions 1–5 should return a green CSS color."""
    for pos in range(1, 6):
        result = color_code_position(pos)
        assert "green" in result.lower(), f"Expected green for pos={pos}, got {result!r}"


@pytest.mark.unit
def test_color_code_position_mid():
    """Positions 6–15 should return a yellow CSS color."""
    for pos in range(6, 16):
        result = color_code_position(pos)
        assert "yellow" in result.lower() or "gold" in result.lower() or "#f" in result.lower(), (
            f"Expected yellow/gold for pos={pos}, got {result!r}"
        )


@pytest.mark.unit
def test_color_code_position_tail():
    """Positions 16+ should return white or empty string."""
    for pos in [16, 20, 30, 100]:
        result = color_code_position(pos)
        assert result == "" or "white" in result.lower() or result == "white", (
            f"Expected white/empty for pos={pos}, got {result!r}"
        )


# ---------------------------------------------------------------------------
# render_metrics_table
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_render_metrics_table_contains_persona_version():
    """HTML output should contain persona_id, version name, and a numeric entropy value."""
    from tests.evaluation.feed_evaluator import FeedResult

    persona_id = "tech-geek"
    version_a = "baseline"
    version_b = "live_profile"

    results = {
        (persona_id, version_a): (
            _make_feed_result(persona_id, version_a),
            _make_proxy_metrics(topic_entropy=2.5),
        ),
        (persona_id, version_b): (
            _make_feed_result(persona_id, version_b),
            _make_proxy_metrics(topic_entropy=3.1),
        ),
    }

    html = render_metrics_table(results)
    assert isinstance(html, str), "render_metrics_table must return a str"
    assert persona_id in html, f"Expected persona_id '{persona_id}' in HTML"
    assert version_a in html, f"Expected version '{version_a}' in HTML"
    assert version_b in html, f"Expected version '{version_b}' in HTML"
    # Should contain some numeric value for entropy
    assert "2.5" in html or "2.50" in html or "3.1" in html or "3.10" in html


# ---------------------------------------------------------------------------
# render_diff_matrix
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_render_diff_matrix_shows_rank_changes():
    """When same post appears at different ranks in two versions, HTML shows ↑ or ↓."""
    shared_post = uuid4()
    other_posts_a = [uuid4() for _ in range(4)]
    other_posts_b = [uuid4() for _ in range(4)]

    # In version A: shared_post is at position 1
    posts_a = [shared_post] + other_posts_a
    # In version B: shared_post is at position 5 (rank dropped)
    posts_b = other_posts_b + [shared_post]

    result_a = _make_feed_result("sports-fan", "baseline", posts_a)
    result_b = _make_feed_result("sports-fan", "live_profile", posts_b)

    results = {
        "baseline": result_a,
        "live_profile": result_b,
    }

    html = render_diff_matrix(results)
    assert isinstance(html, str), "render_diff_matrix must return a str"
    # Should contain rank change indicator
    assert "↑" in html or "↓" in html or "rank" in html.lower() or "change" in html.lower(), (
        "Expected rank change indicator (↑ or ↓) in diff matrix"
    )


# ---------------------------------------------------------------------------
# render_explainability_bars
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_render_explainability_bars_orders_by_contribution():
    """Bars should appear in descending order by contribution value."""
    breakdown = {
        "topic_match": {"value": 0.8, "weight": 0.30, "contribution": 0.24},
        "freshness": {"value": 0.5, "weight": 0.15, "contribution": 0.075},
        "embedding_sim": {"value": 0.6, "weight": 0.25, "contribution": 0.15},
        "entity_match": {"value": 0.3, "weight": 0.15, "contribution": 0.045},
        "sentiment_match": {"value": 0.4, "weight": 0.05, "contribution": 0.02},
        "format_match": {"value": 0.9, "weight": 0.10, "contribution": 0.09},
    }

    html = render_explainability_bars(breakdown)
    assert isinstance(html, str), "render_explainability_bars must return a str"

    # Check descending order: topic_match (0.24) > embedding_sim (0.15) > format_match (0.09) ...
    pos_topic = html.find("topic_match")
    pos_embedding = html.find("embedding_sim")
    pos_format = html.find("format_match")
    pos_entity = html.find("entity_match")

    assert pos_topic != -1, "topic_match should appear in output"
    assert pos_embedding != -1, "embedding_sim should appear in output"
    # The highest contribution (topic_match=0.24) should come before embedding_sim (0.15)
    assert pos_topic < pos_embedding, (
        f"topic_match (contribution=0.24) should appear before embedding_sim (0.15); "
        f"pos_topic={pos_topic}, pos_embedding={pos_embedding}"
    )
    # embedding_sim (0.15) should appear before format_match (0.09)
    assert pos_embedding < pos_format, (
        f"embedding_sim (contribution=0.15) should appear before format_match (0.09); "
        f"pos_embedding={pos_embedding}, pos_format={pos_format}"
    )
