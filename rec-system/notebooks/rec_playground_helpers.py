"""Pure helper functions for rec_playground.ipynb — Phase 7 evaluation harness.

All functions return HTML strings only. No IPython calls here.
"""
from __future__ import annotations

import html as _html_module
from typing import Any

# ---------------------------------------------------------------------------
# color_code_position
# ---------------------------------------------------------------------------


def color_code_position(pos: int) -> str:
    """Return CSS color — green for top-5, yellow for 6-15, white/empty for 16+."""
    if pos <= 5:
        return "green"
    if pos <= 15:
        return "yellow"
    return "white"


# ---------------------------------------------------------------------------
# render_side_by_side
# ---------------------------------------------------------------------------


def render_side_by_side(persona: Any, results: dict) -> str:
    """Return HTML table with columns per version.

    Args:
        persona: Persona object (uses .display_name / .id).
        results: dict of version_name -> FeedResult (or (FeedResult, ProxyMetrics)).
    """
    persona_name = getattr(persona, "display_name", str(persona))
    persona_id = getattr(persona, "id", "")

    versions: list[str] = []
    feed_results: dict[str, Any] = {}
    for k, v in results.items():
        versions.append(k)
        feed_results[k] = v[0] if isinstance(v, tuple) else v

    header_cells = "".join(f"<th>{_esc(v)}</th>" for v in versions)

    max_items = max((len(feed_results[v].items) for v in versions), default=0)

    rows: list[str] = []
    for i in range(max_items):
        row_cells: list[str] = []
        for v in versions:
            items = feed_results[v].items
            if i < len(items):
                item = items[i]
                color = color_code_position(item.position)
                top_topic = item.topics[0][0] if item.topics else ""
                cell = (
                    f'<td style="background:{color};padding:4px">'
                    f"<b>{i + 1}.</b> {_esc(item.title)}<br>"
                    f"<small>{_esc(top_topic)}</small>"
                    f"</td>"
                )
            else:
                cell = "<td>—</td>"
            row_cells.append(cell)
        rows.append("<tr>" + "".join(row_cells) + "</tr>")

    rows_html = "\n".join(rows)
    return (
        f"<h3>Side-by-side: {_esc(persona_name)} ({_esc(persona_id)})</h3>"
        f"<table border='1' cellpadding='4' cellspacing='0'>"
        f"<thead><tr><th>#</th>{header_cells}</tr></thead>"
        f"<tbody>{rows_html}</tbody>"
        f"</table>"
    )


# ---------------------------------------------------------------------------
# render_metrics_table
# ---------------------------------------------------------------------------


def render_metrics_table(results: dict) -> str:
    """Return HTML table of ProxyMetrics per version.

    Args:
        results: dict of (persona_id, version) -> (FeedResult, ProxyMetrics)
                 OR (persona_id, version) -> ProxyMetrics.
    """
    _METRIC_FIELDS = [
        ("topic_entropy", "Topic Entropy"),
        ("freshness_median_hours", "Freshness Median (h)"),
        ("pairwise_cosine_avg", "Pairwise Cosine Avg"),
        ("topic_coverage", "Topic Coverage"),
        ("distance_to_profile_avg", "Dist to Profile Avg"),
        ("long_tail_ratio", "Long Tail Ratio"),
        ("dedup_pair_count", "Dedup Pair Count"),
        ("category_mass_balance", "Category Gini"),
    ]

    header = (
        "<tr><th>Persona</th><th>Version</th>"
        + "".join(f"<th>{label}</th>" for _, label in _METRIC_FIELDS)
        + "</tr>"
    )

    rows: list[str] = []
    for key, value in results.items():
        if isinstance(key, tuple) and len(key) == 2:
            persona_id, version = key
        else:
            persona_id, version = str(key), ""

        # Extract metrics
        if isinstance(value, tuple):
            _, metrics = value
        else:
            metrics = value

        cells = f"<td>{_esc(persona_id)}</td><td>{_esc(version)}</td>"
        for field_name, _ in _METRIC_FIELDS:
            val = getattr(metrics, field_name, None)
            if val is None:
                cells += "<td>—</td>"
            elif isinstance(val, float):
                cells += f"<td>{val:.2f}</td>"
            else:
                cells += f"<td>{val}</td>"
        rows.append(f"<tr>{cells}</tr>")

    rows_html = "\n".join(rows)
    return (
        "<table border='1' cellpadding='4' cellspacing='0'>"
        f"<thead>{header}</thead>"
        f"<tbody>{rows_html}</tbody>"
        "</table>"
    )


# ---------------------------------------------------------------------------
# render_diff_matrix
# ---------------------------------------------------------------------------


def render_diff_matrix(results: dict) -> str:
    """Compare top-30 between versions — posts only in A vs B, rank changes.

    Args:
        results: dict of version_name -> FeedResult (compares first two versions).
    """
    versions = list(results.keys())
    if len(versions) < 2:
        return "<p>Need at least two versions to diff.</p>"

    v_a, v_b = versions[0], versions[1]
    feed_a = results[v_a]
    feed_b = results[v_b]

    # Extract top-30
    items_a = feed_a.items[:30]
    items_b = feed_b.items[:30]

    rank_a: dict = {str(item.post_id): item.position for item in items_a}
    rank_b: dict = {str(item.post_id): item.position for item in items_b}

    all_post_ids = sorted(set(rank_a) | set(rank_b))

    header = (
        f"<tr><th>Post ID</th><th>Rank in {_esc(v_a)}</th>"
        f"<th>Rank in {_esc(v_b)}</th><th>Change</th></tr>"
    )

    rows: list[str] = []
    for pid in all_post_ids:
        ra = rank_a.get(pid)
        rb = rank_b.get(pid)

        if ra is None:
            ra_str = "—"
            change = "↑ new in B"
        elif rb is None:
            ra_str = str(ra)
            change = "↓ dropped from B"
        else:
            ra_str = str(ra)
            diff = ra - rb  # positive = moved up in B (lower rank number)
            if diff > 0:
                change = f"↑ +{diff}"
            elif diff < 0:
                change = f"↓ {diff}"
            else:
                change = "="

        rb_str = str(rb) if rb is not None else "—"
        short_pid = pid[:8] + "..."
        rows.append(
            f"<tr><td>{_esc(short_pid)}</td><td>{ra_str}</td>"
            f"<td>{rb_str}</td><td>{_esc(change)}</td></tr>"
        )

    rows_html = "\n".join(rows)
    return (
        f"<h3>Diff Matrix: {_esc(v_a)} vs {_esc(v_b)}</h3>"
        f"<table border='1' cellpadding='4' cellspacing='0'>"
        f"<thead>{header}</thead>"
        f"<tbody>{rows_html}</tbody>"
        f"</table>"
    )


# ---------------------------------------------------------------------------
# render_llm_panel
# ---------------------------------------------------------------------------


def render_llm_panel(results: dict) -> str:
    """LLM judgment summary.

    Args:
        results: dict of version_name -> JudgeResult  OR
                 dict of (persona_id, version) -> JudgeResult.
    """
    rows: list[str] = []
    for key, judge in results.items():
        if isinstance(key, tuple):
            persona_id, version = key
        else:
            persona_id, version = "", str(key)

        mean_rel = getattr(judge, "mean_relevance", None)
        median_rel = getattr(judge, "median_relevance", None)
        ndcg = getattr(judge, "ndcg_at_10", None)
        model = getattr(judge, "model", "")

        mean_str = f"{mean_rel:.2f}" if mean_rel is not None else "—"
        median_str = f"{median_rel:.2f}" if median_rel is not None else "—"
        ndcg_str = f"{ndcg:.3f}" if ndcg is not None else "—"

        rows.append(
            f"<tr>"
            f"<td>{_esc(persona_id)}</td><td>{_esc(version)}</td>"
            f"<td>{_esc(model)}</td>"
            f"<td>{mean_str}</td><td>{median_str}</td><td>{ndcg_str}</td>"
            f"</tr>"
        )

    header = (
        "<tr><th>Persona</th><th>Version</th><th>Model</th>"
        "<th>Mean Rel.</th><th>Median Rel.</th><th>nDCG@10</th></tr>"
    )
    rows_html = "\n".join(rows)
    return (
        "<h3>LLM Judge Summary</h3>"
        "<table border='1' cellpadding='4' cellspacing='0'>"
        f"<thead>{header}</thead>"
        f"<tbody>{rows_html}</tbody>"
        "</table>"
    )


# ---------------------------------------------------------------------------
# render_explainability_bars
# ---------------------------------------------------------------------------


def render_explainability_bars(breakdown: dict) -> str:
    """Horizontal bar chart (HTML/inline SVG) of component contributions.

    Components are sorted in descending order by contribution value.

    Args:
        breakdown: dict of component_name -> {value, weight, contribution, ...}.
    """
    sorted_components = sorted(
        breakdown.items(),
        key=lambda kv: kv[1].get("contribution", 0.0),
        reverse=True,
    )

    max_contribution = max(
        (v.get("contribution", 0.0) for _, v in sorted_components),
        default=1.0,
    ) or 1.0

    bar_height = 24
    bar_gap = 4
    label_width = 160
    chart_width = 400
    total_height = (bar_height + bar_gap) * len(sorted_components) + 10

    bars: list[str] = []
    for i, (name, comp) in enumerate(sorted_components):
        contribution = comp.get("contribution", 0.0)
        bar_w = int(chart_width * contribution / max_contribution)
        y = i * (bar_height + bar_gap)
        bars.append(
            f'<text x="{label_width - 4}" y="{y + bar_height - 6}" '
            f'text-anchor="end" font-size="12">{_esc(name)}</text>'
        )
        bars.append(
            f'<rect x="{label_width}" y="{y}" width="{bar_w}" height="{bar_height}" '
            f'fill="#4a90d9" rx="3"/>'
        )
        bars.append(
            f'<text x="{label_width + bar_w + 4}" y="{y + bar_height - 6}" '
            f'font-size="11" fill="#333">{contribution:.3f}</text>'
        )

    bars_svg = "\n".join(bars)
    total_svg_width = label_width + chart_width + 80
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{total_svg_width}" height="{total_height}">'
        f"{bars_svg}"
        f"</svg>"
    )
    return f"<div><h4>Score Breakdown</h4>{svg}</div>"


# ---------------------------------------------------------------------------
# Private utilities
# ---------------------------------------------------------------------------


def _esc(s: Any) -> str:
    """HTML-escape a value."""
    return _html_module.escape(str(s))
