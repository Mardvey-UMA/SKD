#!/usr/bin/env python3
"""Benchmark runner CLI for the rec-system evaluation harness.

Runs the full evaluation pipeline for N personas × M versions × K runs,
producing artifacts under --output:

  feeds/{persona}_{version}_run{N}.json
  llm_judgments/{persona}_{version}_run{N}.json  (--enable-llm-judge only)
  metrics.csv
  costs.csv                                       (--enable-llm-judge only)
  summary.md
  personas_snapshot.yaml
  config_snapshot.yaml
  versions_used.yaml
  errors.log

Example:
    uv run python scripts/benchmark.py \\
        --personas tech-geek,political-junkie \\
        --versions baseline \\
        --runs 3 --count 30 --seed 42 \\
        --output .claude/artifacts/eval/benchmarks/2026-04-18_smoke/ \\
        --tag smoke
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("benchmark")


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="rec-system benchmark runner — evaluates recommendations across personas × versions × runs",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )

    persona_group = parser.add_mutually_exclusive_group(required=True)
    persona_group.add_argument(
        "--personas",
        metavar="IDS",
        help="Comma-separated persona IDs (e.g. tech-geek,political-junkie)",
    )
    persona_group.add_argument(
        "--personas-all",
        action="store_true",
        help="Run all personas found in tests/evaluation/personas/",
    )

    parser.add_argument(
        "--versions",
        metavar="VERSIONS",
        default="baseline",
        help="Comma-separated version names from versions.yaml (default: baseline)",
    )
    parser.add_argument(
        "--runs",
        type=int,
        default=1,
        metavar="N",
        help="Number of runs per (persona × version) combination (default: 1)",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=30,
        metavar="N",
        help="Feed size per evaluation run (default: 30)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Base random seed — incremented by run number (default: 42)",
    )
    parser.add_argument(
        "--enable-llm-judge",
        action="store_true",
        help="Enable LLM-as-judge scoring (calls Claude CLI; incurs cost)",
    )
    parser.add_argument(
        "--judge-model",
        default="haiku",
        help="Claude model to use for judging (default: haiku)",
    )
    parser.add_argument(
        "--output",
        metavar="DIR",
        help=(
            "Output directory for artifacts. "
            "Defaults to .claude/artifacts/eval/benchmarks/{timestamp}_{tag}/"
        ),
    )
    parser.add_argument(
        "--tag",
        default="benchmark",
        help="Short label included in the output directory and summary (default: benchmark)",
    )
    parser.add_argument(
        "--compact-snippets",
        action="store_true",
        default=False,
        help=(
            "Truncate per-post snippets to 100 chars in LLM judge prompts "
            "(default: 200). Reduces prompt size and judge latency."
        ),
    )
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Skip combinations whose feed JSON already exists in --output",
    )
    parser.add_argument(
        "--db-url",
        default=None,
        metavar="URL",
        help=(
            "PostgreSQL async URL (overrides DATABASE_URL env var). "
            "E.g. postgresql+asyncpg://user:pass@host:5432/content_agg_db"
        ),
    )

    return parser.parse_args()


def _resolve_output_dir(args: argparse.Namespace) -> Path:
    if args.output:
        return Path(args.output)
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d_%H%M%S")
    return _PROJECT_ROOT / ".claude" / "artifacts" / "eval" / "benchmarks" / f"{ts}_{args.tag}"


async def _main(args: argparse.Namespace) -> int:
    import os

    from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

    from tests.evaluation.benchmark_runner import BenchmarkRunner, load_version_configs
    from tests.evaluation.persona_loader import PersonaLoader

    _PERSONAS_DIR = _PROJECT_ROOT / "tests" / "evaluation" / "personas"
    _VERSIONS_YAML = _PROJECT_ROOT / "tests" / "evaluation" / "versions.yaml"

    # Resolve DB URL
    db_url = args.db_url or os.environ.get("DATABASE_URL", "")
    if not db_url:
        logger.error(
            "No database URL provided. Set DATABASE_URL env var or pass --db-url."
        )
        return 1

    # Normalise to asyncpg
    if "asyncpg" not in db_url and "psycopg2" not in db_url:
        db_url = db_url.replace("postgresql://", "postgresql+asyncpg://", 1)
    elif "psycopg2" in db_url:
        db_url = db_url.replace("postgresql+psycopg2://", "postgresql+asyncpg://", 1)

    engine = create_async_engine(
        db_url,
        echo=False,
        connect_args={"server_settings": {"search_path": "data_flow,public"}},
    )
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    # Load personas
    loader = PersonaLoader(_PERSONAS_DIR)
    if args.personas_all:
        personas = loader.load_all()
        logger.info("Loaded %d personas from %s", len(personas), _PERSONAS_DIR)
    else:
        persona_ids = [p.strip() for p in args.personas.split(",") if p.strip()]
        personas = [loader.load(pid) for pid in persona_ids]
        logger.info("Loaded personas: %s", [p.id for p in personas])

    # Load versions
    version_config = load_version_configs(_VERSIONS_YAML)
    requested_versions = [v.strip() for v in args.versions.split(",") if v.strip()]
    unknown = [v for v in requested_versions if v not in version_config]
    if unknown:
        logger.error("Unknown versions: %s. Available: %s", unknown, list(version_config))
        return 1

    # Resolve output dir
    output_dir = _resolve_output_dir(args)
    output_dir.mkdir(parents=True, exist_ok=True)
    logger.info("Output directory: %s", output_dir)

    # Build component stack
    from src.application.use_cases.generate_feed import GenerateFeedUseCase
    from src.application.use_cases.onboard_user import OnboardUserUseCase
    from src.application.use_cases.update_profile import UpdateProfileUseCase
    from src.domain.services.diversity_filter import DiversityFilter
    from src.domain.services.narrow_scorer import NarrowScorer
    from src.domain.services.onboarding_service import OnboardingService
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
    metrics_computer = ProxyMetricsComputer(
        content_repo=PgContentRepository(session_factory)
    )

    judge = None
    if args.enable_llm_judge:
        from tests.evaluation.llm_judge import LLMJudge
        snippet_max_len = 100 if args.compact_snippets else 200
        judge = LLMJudge(model=args.judge_model, snippet_max_len=snippet_max_len)
        logger.info(
            "LLM judge enabled (model: %s, snippet_max_len: %d)",
            args.judge_model, snippet_max_len,
        )

    runner = BenchmarkRunner(
        simulator=simulator,
        evaluator=evaluator,
        metrics=metrics_computer,
        judge=judge,
        version_config=version_config,
        output_dir=output_dir,
        count=args.count,
        base_seed=args.seed,
        skip_existing=args.skip_existing,
        session_factory=session_factory,
        tag=args.tag,
    )

    logger.info(
        "Starting benchmark: %d personas × %d versions × %d runs = %d combinations",
        len(personas),
        len(requested_versions),
        args.runs,
        len(personas) * len(requested_versions) * args.runs,
    )

    results = await runner.run(
        personas=personas,
        versions=requested_versions,
        runs=args.runs,
    )

    failed = sum(1 for r in results if r.errors and not all("skipped" in e for e in r.errors))
    succeeded = len(results) - failed
    logger.info(
        "Benchmark complete: %d succeeded, %d failed. Artifacts: %s",
        succeeded,
        failed,
        output_dir,
    )

    await engine.dispose()
    return 0


def main() -> None:
    args = _parse_args()
    sys.exit(asyncio.run(_main(args)))


if __name__ == "__main__":
    main()
