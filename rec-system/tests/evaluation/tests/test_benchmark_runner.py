"""Phase 6 tests: BenchmarkRunner (unit + integration).

Unit (no DB):
  - test_version_config_loader
  - test_apply_version_context_manager
  - test_summary_template_single_version

Integration (@pytest.mark.integration, testcontainers):
  - test_benchmark_runner_smoke_no_llm
  - test_benchmark_runner_skip_existing
"""
from __future__ import annotations

import csv
import math
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable
from unittest.mock import AsyncMock, MagicMock

import pytest

from tests.evaluation.benchmark_runner import (
    BenchmarkRunner,
    CombinationResult,
    apply_version,
    load_version_configs,
    render_summary,
)
from tests.evaluation.feed_evaluator import FeedItem, FeedResult
from tests.evaluation.models import InteractionPattern, Persona
from tests.evaluation.proxy_metrics import ProxyMetrics

_PERSONAS_DIR = Path(__file__).parent.parent / "personas"
_VERSIONS_YAML = Path(__file__).parent.parent / "versions.yaml"
_TEMPLATES_DIR = Path(__file__).parent.parent / "templates"
_SUMMARY_TEMPLATE = _TEMPLATES_DIR / "summary.md.j2"


# ---------------------------------------------------------------------------
# Unit: version config loader
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_version_config_loader_loads_all_versions():
    """load_version_configs() must return a dict with at least 'baseline' key."""
    configs = load_version_configs(_VERSIONS_YAML)
    assert isinstance(configs, dict)
    assert "baseline" in configs, "versions.yaml must contain a 'baseline' version"
    assert "live_profile" in configs
    assert "rerank" in configs


@pytest.mark.unit
def test_version_config_loader_baseline_has_empty_overrides():
    """baseline version must have empty env and rec_config_overrides."""
    configs = load_version_configs(_VERSIONS_YAML)
    baseline = configs["baseline"]
    assert (baseline.get("env") or {}) == {}
    assert (baseline.get("rec_config_overrides") or {}) == {}


@pytest.mark.unit
def test_version_config_loader_live_profile_has_env():
    """live_profile version must set at least one env var."""
    configs = load_version_configs(_VERSIONS_YAML)
    lp = configs["live_profile"]
    env = lp.get("env", {})
    assert len(env) >= 1, "live_profile must have at least one env override"


@pytest.mark.unit
def test_version_config_loader_custom_path(tmp_path):
    """load_version_configs(path) accepts a custom path."""
    custom = tmp_path / "versions.yaml"
    custom.write_text(
        "versions:\n  myver:\n    description: test\n    env: {}\n    rec_config_overrides: {}\n"
    )
    configs = load_version_configs(custom)
    assert "myver" in configs


# ---------------------------------------------------------------------------
# Unit: apply_version context manager
# ---------------------------------------------------------------------------


@pytest.mark.unit
async def test_apply_version_context_manager_sets_env():
    """apply_version must set env vars within the context and revert them after."""
    version_entry = {
        "env": {"_TEST_BENCH_VAR": "hello"},
        "rec_config_overrides": {},
    }
    assert "_TEST_BENCH_VAR" not in os.environ

    async with apply_version(version_entry):
        assert os.environ.get("_TEST_BENCH_VAR") == "hello"

    assert "_TEST_BENCH_VAR" not in os.environ


@pytest.mark.unit
async def test_apply_version_context_manager_restores_previous_value():
    """apply_version must restore original env var value, not just delete it."""
    os.environ["_TEST_BENCH_EXISTING"] = "original"
    version_entry = {
        "env": {"_TEST_BENCH_EXISTING": "overridden"},
        "rec_config_overrides": {},
    }
    try:
        async with apply_version(version_entry):
            assert os.environ["_TEST_BENCH_EXISTING"] == "overridden"
        assert os.environ["_TEST_BENCH_EXISTING"] == "original"
    finally:
        os.environ.pop("_TEST_BENCH_EXISTING", None)


@pytest.mark.unit
async def test_apply_version_context_manager_restores_on_exception():
    """apply_version must revert env vars even if the body raises."""
    version_entry = {
        "env": {"_TEST_BENCH_EXCEPT": "set"},
        "rec_config_overrides": {},
    }
    with pytest.raises(ValueError, match="inner error"):
        async with apply_version(version_entry):
            assert os.environ.get("_TEST_BENCH_EXCEPT") == "set"
            raise ValueError("inner error")

    assert "_TEST_BENCH_EXCEPT" not in os.environ


@pytest.mark.unit
async def test_apply_version_baseline_no_op():
    """baseline version with empty overrides must not mutate os.environ."""
    before = dict(os.environ)
    version_entry = {"env": {}, "rec_config_overrides": {}}
    async with apply_version(version_entry):
        pass
    # No extra keys added
    added = {k for k in os.environ if k not in before}
    assert not added, f"Unexpected env keys added: {added}"


# ---------------------------------------------------------------------------
# Unit: Jinja2 summary template
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_summary_template_single_version():
    """render_summary() with 2 personas × 1 version × 1 run must produce valid markdown."""
    if not _SUMMARY_TEMPLATE.exists():
        pytest.skip("summary.md.j2 template not yet created (GREEN phase)")

    import math

    persona_ids = ["tech-geek", "political-junkie"]
    versions = ["baseline"]
    per_version = {
        "baseline": {
            "tech-geek": {
                "topic_entropy": 2.1,
                "freshness_h": 12.5,
                "topic_coverage": 4,
                "distance_to_profile_avg": 0.72,
                "dedup_pair_count": 0,
                "llm_mean": None,
                "llm_median": None,
                "llm_ndcg_at_10": None,
            },
            "political-junkie": {
                "topic_entropy": 1.8,
                "freshness_h": 8.0,
                "topic_coverage": 3,
                "distance_to_profile_avg": 0.65,
                "dedup_pair_count": 1,
                "llm_mean": None,
                "llm_median": None,
                "llm_ndcg_at_10": None,
            },
        }
    }
    per_persona = {pid: per_version["baseline"][pid] for pid in persona_ids}

    ctx = {
        "tag": "smoke-test",
        "timestamp": "2026-04-18 10:00:00 UTC",
        "duration_minutes": "2.3",
        "total_cost": 0.0,
        "n_personas": 2,
        "n_versions": 1,
        "n_runs": 1,
        "total_runs": 2,
        "git_commit": "abc1234",
        "versions": versions,
        "persona_ids": persona_ids,
        "per_version": per_version,
        "per_persona": per_persona,
        "errors": [],
        "seed": 42,
        "judge_model": None,
        "input_tokens": 0,
        "output_tokens": 0,
    }

    rendered = render_summary(_SUMMARY_TEMPLATE, ctx)

    assert isinstance(rendered, str)
    assert len(rendered) > 100
    assert "baseline" in rendered
    assert "tech-geek" in rendered
    assert "political-junkie" in rendered
    assert "smoke-test" in rendered
    # Numeric values rendered
    assert "2.1" in rendered or "2.10" in rendered


@pytest.mark.unit
def test_summary_template_renders_errors():
    """render_summary() with errors list must include those errors."""
    if not _SUMMARY_TEMPLATE.exists():
        pytest.skip("summary.md.j2 template not yet created (GREEN phase)")

    ctx = {
        "tag": "err-test",
        "timestamp": "2026-04-18 10:00:00 UTC",
        "duration_minutes": "0.5",
        "total_cost": 0.0,
        "n_personas": 1,
        "n_versions": 1,
        "n_runs": 1,
        "total_runs": 1,
        "git_commit": "abc1234",
        "versions": ["baseline"],
        "persona_ids": ["tech-geek"],
        "per_version": {"baseline": {"tech-geek": {
            "topic_entropy": 1.0, "freshness_h": 5.0, "topic_coverage": 2,
            "distance_to_profile_avg": 0.5, "dedup_pair_count": 0,
            "llm_mean": None, "llm_median": None, "llm_ndcg_at_10": None,
        }}},
        "per_persona": {"tech-geek": {
            "topic_entropy": 1.0, "freshness_h": 5.0, "topic_coverage": 2,
            "distance_to_profile_avg": 0.5, "dedup_pair_count": 0,
            "llm_mean": None, "llm_median": None, "llm_ndcg_at_10": None,
        }},
        "errors": ["tech-geek/baseline/run1: SimulatorCorpusError: empty corpus"],
        "seed": 42,
        "judge_model": None,
        "input_tokens": 0,
        "output_tokens": 0,
    }

    rendered = render_summary(_SUMMARY_TEMPLATE, ctx)
    assert "SimulatorCorpusError" in rendered


# ---------------------------------------------------------------------------
# Integration: BenchmarkRunner smoke (no LLM)
# ---------------------------------------------------------------------------


def _make_benchmark_runner(session_factory: Callable, output_dir: Path) -> BenchmarkRunner:
    """Wire up a full BenchmarkRunner using the testcontainers DB."""
    from src.application.use_cases.generate_feed import GenerateFeedUseCase
    from src.domain.services.diversity_filter import DiversityFilter
    from src.domain.services.narrow_scorer import NarrowScorer
    from src.domain.services.profile_updater import ProfileUpdater
    from src.domain.services.scorer import Scorer
    from src.domain.services.signal_classifier import SignalClassifier
    from src.infrastructure.persistence.pg_blocked_sources_repository import PgBlockedSourcesRepository
    from src.infrastructure.persistence.pg_config_repository import PgConfigRepository
    from src.infrastructure.persistence.pg_content_repository import PgContentRepository
    from src.infrastructure.persistence.pg_entity_interest_repository import PgEntityInterestRepository
    from src.infrastructure.persistence.pg_interaction_repository import PgInteractionRepository
    from src.infrastructure.persistence.pg_user_profile_repository import PgUserProfileRepository
    from tests.evaluation.behavior_simulator import BehaviorSimulator
    from tests.evaluation.feed_evaluator import FeedEvaluator
    from tests.evaluation.proxy_metrics import ProxyMetricsComputer

    _SIGNAL_WEIGHTS = {
        "impression_read": {"threshold_duration_ms": 2000, "weight": 0.15},
        "impression_skip": {"threshold_duration_ms": 2000, "weight": -0.05},
        "open": {"weight": 0.1},
        "close_fast": {"threshold_duration_ms": 3000, "weight": -0.2},
        "close_full": {"threshold_scroll_pct": 0.85, "threshold_duration_ms": 15000, "weight": 0.5},
        "close_half": {"threshold_scroll_pct": 0.5, "threshold_duration_ms": 10000, "weight": 0.4},
        "close_other": {"weight": 0.1},
        "like": {"weight": 0.6},
        "dislike": {"weight": -0.7},
        "bookmark": {"weight": 0.8},
        "orphan_timeout_minutes": 5,
    }

    from src.domain.services.onboarding_service import OnboardingService
    from src.application.use_cases.onboard_user import OnboardUserUseCase
    from src.application.use_cases.update_profile import UpdateProfileUseCase

    onboard_uc = OnboardUserUseCase(
        profile_repo=PgUserProfileRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        onboarding_service=OnboardingService(),
        category_repo=None,
        content_repo=None,
        event_publisher=None,
    )
    update_uc = UpdateProfileUseCase(
        interaction_repo=PgInteractionRepository(session_factory),
        profile_repo=PgUserProfileRepository(session_factory),
        entity_interest_repo=PgEntityInterestRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        profile_updater=ProfileUpdater(),
        content_repo=PgContentRepository(session_factory),
    )
    simulator = BehaviorSimulator(
        content_repo=PgContentRepository(session_factory),
        profile_repo=PgUserProfileRepository(session_factory),
        interactions_repo=PgInteractionRepository(session_factory),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        update_profile_use_case=update_uc,
        onboard_use_case=onboard_uc,
    )
    generate_uc = GenerateFeedUseCase(
        profile_repo=PgUserProfileRepository(session_factory),
        content_repo=PgContentRepository(session_factory),
        entity_interest_repo=PgEntityInterestRepository(session_factory),
        interaction_repo=PgInteractionRepository(session_factory),
        config_repo=PgConfigRepository(session_factory),
        scorer=Scorer(),
        diversity_filter=DiversityFilter(),
        signal_classifier=SignalClassifier(_SIGNAL_WEIGHTS),
        profile_updater=ProfileUpdater(),
        blocked_sources_repo=PgBlockedSourcesRepository(session_factory),
        narrow_scorer=NarrowScorer(),
    )
    evaluator = FeedEvaluator(
        generate_feed_use_case=generate_uc,
        content_repo=PgContentRepository(session_factory),
        session_factory=session_factory,
    )
    metrics = ProxyMetricsComputer(content_repo=PgContentRepository(session_factory))

    version_config = load_version_configs(_VERSIONS_YAML)

    return BenchmarkRunner(
        simulator=simulator,
        evaluator=evaluator,
        metrics=metrics,
        judge=None,
        version_config=version_config,
        output_dir=output_dir,
        count=5,
        base_seed=42,
        skip_existing=False,
        session_factory=session_factory,
    )


@pytest.mark.integration
async def test_benchmark_runner_smoke_no_llm(
    eval_session_factory,
    seed_published_content,
    tmp_path,
):
    """BenchmarkRunner with 2 personas × baseline × 1 run, judge=None.

    Verifies:
    - metrics.csv exists with 2 data rows + header
    - feeds/tech-geek_baseline_run1.json exists and parses
    - summary.md exists and non-empty
    - errors.log exists
    - personas_snapshot.yaml, config_snapshot.yaml, versions_used.yaml exist
    """
    from tests.evaluation.persona_loader import PersonaLoader

    output_dir = tmp_path / "benchmark_output"
    runner = _make_benchmark_runner(eval_session_factory, output_dir)

    personas = [
        PersonaLoader(_PERSONAS_DIR).load("tech-geek"),
        PersonaLoader(_PERSONAS_DIR).load("political-junkie"),
    ]

    results = await runner.run(personas=personas, versions=["baseline"], runs=1)

    # 2 combinations, 0 failures expected (or at most tolerate 1)
    assert len(results) == 2
    assert all(isinstance(r, CombinationResult) for r in results)

    # metrics.csv: header + 2 rows
    metrics_csv = output_dir / "metrics.csv"
    assert metrics_csv.exists(), "metrics.csv must be created"
    with metrics_csv.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    assert len(rows) == 2, f"Expected 2 data rows, got {len(rows)}"
    assert "topic_entropy" in rows[0]
    assert "persona_id" in rows[0]

    # feeds/tech-geek_baseline_run1.json
    feed_path = output_dir / "feeds" / "tech-geek_baseline_run1.json"
    assert feed_path.exists(), "Feed JSON for tech-geek must exist"
    feed = FeedResult.from_json(feed_path.read_text(encoding="utf-8"))
    assert feed.persona_id == "tech-geek"
    assert feed.version == "baseline"
    assert feed.run_number == 1

    # summary.md
    summary_path = output_dir / "summary.md"
    assert summary_path.exists(), "summary.md must be created"
    summary_text = summary_path.read_text(encoding="utf-8")
    assert len(summary_text) > 50

    # errors.log
    errors_log = output_dir / "errors.log"
    assert errors_log.exists(), "errors.log must always be created"

    # YAML snapshots
    assert (output_dir / "personas_snapshot.yaml").exists()
    assert (output_dir / "config_snapshot.yaml").exists()
    assert (output_dir / "versions_used.yaml").exists()


@pytest.mark.integration
async def test_benchmark_runner_skip_existing(
    eval_session_factory,
    seed_published_content,
    tmp_path,
):
    """Second run with --skip-existing must reuse first run's feed files."""
    from tests.evaluation.persona_loader import PersonaLoader
    import time

    output_dir = tmp_path / "skip_test_output"
    runner = _make_benchmark_runner(eval_session_factory, output_dir)

    personas = [PersonaLoader(_PERSONAS_DIR).load("tech-geek")]

    # First run
    results1 = await runner.run(personas=personas, versions=["baseline"], runs=1)
    assert len(results1) == 1

    feed_path = output_dir / "feeds" / "tech-geek_baseline_run1.json"
    assert feed_path.exists()
    mtime_first = feed_path.stat().st_mtime

    # Brief pause to ensure mtime would differ if file was rewritten
    await asyncio.sleep(0.05)

    # Second run with skip_existing=True
    runner2 = BenchmarkRunner(
        simulator=runner._simulator,
        evaluator=runner._evaluator,
        metrics=runner._metrics,
        judge=None,
        version_config=runner._version_config,
        output_dir=output_dir,
        count=5,
        base_seed=42,
        skip_existing=True,
        session_factory=eval_session_factory,
    )
    results2 = await runner2.run(personas=personas, versions=["baseline"], runs=1)
    assert len(results2) == 1

    mtime_second = feed_path.stat().st_mtime
    assert mtime_second == mtime_first, "File must NOT be re-written when skip_existing=True"


import asyncio
