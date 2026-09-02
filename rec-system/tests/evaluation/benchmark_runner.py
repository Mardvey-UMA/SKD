"""Phase 6: Benchmark Runner for the rec-system evaluation harness.

Orchestrates N personas × M versions × K runs, persisting per-combination
artifacts (feeds, judgments, metrics CSV, Jinja2 summary) to disk.
"""
from __future__ import annotations

import contextlib
import csv
import json
import logging
import os
import statistics
import subprocess
import time
import traceback
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import yaml

from tests.evaluation.behavior_simulator import BehaviorSimulator
from tests.evaluation.db_utils import reset_eval_user
from tests.evaluation.feed_evaluator import FeedEvaluator, FeedResult
from tests.evaluation.llm_judge import JudgeResult, LLMJudge
from tests.evaluation.models import Persona
from tests.evaluation.proxy_metrics import ProxyMetrics, ProxyMetricsComputer

logger = logging.getLogger(__name__)

_VERSIONS_YAML_PATH = Path(__file__).parent / "versions.yaml"
_TEMPLATES_DIR = Path(__file__).parent / "templates"


# ---------------------------------------------------------------------------
# Version config loader
# ---------------------------------------------------------------------------


def load_version_configs(path: Path | None = None) -> dict:
    """Load versions.yaml and return {version_name: config_dict}."""
    p = path or _VERSIONS_YAML_PATH
    raw = yaml.safe_load(p.read_text(encoding="utf-8"))
    return raw.get("versions", {})


# ---------------------------------------------------------------------------
# apply_version — standalone async context manager
# ---------------------------------------------------------------------------


@contextlib.asynccontextmanager
async def apply_version(version_entry: dict, session_factory=None):
    """Apply version overrides (env vars + rec_config DB rows) as a context.

    Restores original values on exit, even if the body raises.

    Args:
        version_entry: dict with optional keys 'env' and 'rec_config_overrides'
        session_factory: async session factory for DB overrides (may be None)
    """
    env_overrides: dict = version_entry.get("env") or {}
    rec_overrides: dict = version_entry.get("rec_config_overrides") or {}

    # --- Apply env vars ---
    old_env: dict[str, str | None] = {}
    for k, v in env_overrides.items():
        old_env[k] = os.environ.get(k)
        os.environ[k] = str(v)

    # --- Apply rec_config DB overrides ---
    old_rec: dict[str, Any] = {}
    if rec_overrides and session_factory is not None:
        from sqlalchemy import text as sa_text
        async with session_factory() as session:
            for key, val in rec_overrides.items():
                res = await session.execute(
                    sa_text("SELECT value FROM data_flow.rec_config WHERE key = :k"),
                    {"k": key},
                )
                row = res.fetchone()
                old_rec[key] = row[0] if row is not None else None
                if row is not None:
                    await session.execute(
                        sa_text(
                            "UPDATE data_flow.rec_config SET value = :v::jsonb WHERE key = :k"
                        ),
                        {"v": json.dumps(val), "k": key},
                    )
                else:
                    await session.execute(
                        sa_text(
                            "INSERT INTO data_flow.rec_config (key, value) VALUES (:k, :v::jsonb)"
                        ),
                        {"k": key, "v": json.dumps(val)},
                    )
            await session.commit()

    try:
        yield
    finally:
        # Restore env vars
        for k, old_v in old_env.items():
            if old_v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = old_v

        # Restore rec_config
        if rec_overrides and session_factory is not None:
            from sqlalchemy import text as sa_text
            try:
                async with session_factory() as session:
                    for key, old_v in old_rec.items():
                        if old_v is None:
                            await session.execute(
                                sa_text(
                                    "DELETE FROM data_flow.rec_config WHERE key = :k"
                                ),
                                {"k": key},
                            )
                        else:
                            await session.execute(
                                sa_text(
                                    "UPDATE data_flow.rec_config SET value = :v::jsonb WHERE key = :k"
                                ),
                                {"v": json.dumps(old_v), "k": key},
                            )
                    await session.commit()
            except Exception:
                logger.exception("Failed to restore rec_config after version context")


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------


@dataclass
class CombinationResult:
    persona_id: str
    version: str
    run_number: int
    seed: int
    feed: FeedResult | None
    proxy: ProxyMetrics | None
    judge: JudgeResult | None
    duration_s: float
    errors: list[str]


# ---------------------------------------------------------------------------
# BenchmarkRunner
# ---------------------------------------------------------------------------


class BenchmarkRunner:
    def __init__(
        self,
        simulator: BehaviorSimulator,
        evaluator: FeedEvaluator,
        metrics: ProxyMetricsComputer,
        judge: LLMJudge | None,
        version_config: dict,
        output_dir: Path,
        count: int = 30,
        base_seed: int = 42,
        skip_existing: bool = False,
        session_factory=None,
        tag: str = "benchmark",
    ) -> None:
        self._simulator = simulator
        self._evaluator = evaluator
        self._metrics = metrics
        self._judge = judge
        self._version_config = version_config
        self._output_dir = output_dir
        self._count = count
        self._base_seed = base_seed
        self._skip_existing = skip_existing
        self._session_factory = session_factory
        self._tag = tag
        self._all_errors: list[str] = []

    async def run(
        self,
        personas: list[Persona],
        versions: list[str],
        runs: int,
    ) -> list[CombinationResult]:
        """Run all (persona × version × run) combinations and save artifacts."""
        self._setup_output_dir()
        start_time = datetime.now(timezone.utc)
        results: list[CombinationResult] = []

        for persona in personas:
            for version in versions:
                for run_num in range(1, runs + 1):
                    result = await self._run_combination(persona, version, run_num)
                    results.append(result)

        end_time = datetime.now(timezone.utc)

        self._save_metrics_csv(results)
        if self._judge:
            self._save_costs_csv(results)
        self._save_errors_log()
        self._save_config_artifacts(personas, versions)
        self._save_summary(results, personas, versions, runs, start_time, end_time)

        return results

    async def _run_combination(
        self, persona: Persona, version: str, run_num: int
    ) -> CombinationResult:
        user_id = uuid.uuid5(uuid.NAMESPACE_DNS, f"{persona.id}:{version}:{run_num}")
        seed = self._base_seed + run_num - 1
        feed_path = (
            self._output_dir / "feeds" / f"{persona.id}_{version}_run{run_num}.json"
        )

        if self._skip_existing and feed_path.exists():
            logger.info(
                "Skipping %s/%s/run%d (feed already exists)", persona.id, version, run_num
            )
            return self._load_skipped_result(persona.id, version, run_num, seed, feed_path)

        t0 = time.monotonic()
        errors: list[str] = []

        try:
            if self._session_factory is not None:
                await reset_eval_user(user_id, self._session_factory)

            version_entry = self._version_config.get(version, {})
            async with apply_version(version_entry, self._session_factory):
                sim_state = await self._simulator.simulate(persona, user_id, seed=seed)
                feed = await self._evaluator.run(
                    sim_state,
                    count=self._count,
                    version=version,
                    run_number=run_num,
                )
                proxy = await self._metrics.compute(feed, sim_state.profile_snapshot)
                judge: JudgeResult | None = None
                if self._judge is not None:
                    judge = await self._judge.judge(persona, feed)

            self._save_feed_json(feed, persona.id, version, run_num)
            if judge is not None:
                self._save_judge_json(judge, persona.id, version, run_num)

            duration_s = time.monotonic() - t0
            return CombinationResult(
                persona_id=persona.id,
                version=version,
                run_number=run_num,
                seed=seed,
                feed=feed,
                proxy=proxy,
                judge=judge,
                duration_s=duration_s,
                errors=errors,
            )

        except Exception as exc:
            duration_s = time.monotonic() - t0
            err_msg = (
                f"{persona.id}/{version}/run{run_num}: "
                f"{type(exc).__name__}: {exc}\n"
                f"{traceback.format_exc()}"
            )
            errors.append(err_msg)
            self._all_errors.append(err_msg)
            logger.error("Combination failed: %s", err_msg[:300])
            return CombinationResult(
                persona_id=persona.id,
                version=version,
                run_number=run_num,
                seed=seed,
                feed=None,
                proxy=None,
                judge=None,
                duration_s=duration_s,
                errors=errors,
            )

    # ------------------------------------------------------------------
    # Artifact helpers
    # ------------------------------------------------------------------

    def _setup_output_dir(self) -> None:
        for subdir in ["feeds", "llm_judgments"]:
            (self._output_dir / subdir).mkdir(parents=True, exist_ok=True)

    def _save_feed_json(
        self, feed: FeedResult, persona_id: str, version: str, run_num: int
    ) -> None:
        path = self._output_dir / "feeds" / f"{persona_id}_{version}_run{run_num}.json"
        path.write_text(feed.to_json(), encoding="utf-8")

    def _save_judge_json(
        self, judge: JudgeResult, persona_id: str, version: str, run_num: int
    ) -> None:
        path = (
            self._output_dir
            / "llm_judgments"
            / f"{persona_id}_{version}_run{run_num}.json"
        )
        data = {
            "persona_id": judge.persona_id,
            "version": judge.version,
            "run_number": judge.run_number,
            "model": judge.model,
            "total_cost_usd": judge.total_cost_usd,
            "input_tokens": judge.input_tokens,
            "output_tokens": judge.output_tokens,
            "mean_relevance": judge.mean_relevance,
            "median_relevance": judge.median_relevance,
            "ndcg_at_10": judge.ndcg_at_10,
            "retry_count": judge.retry_count,
            "errors": judge.errors,
            "judgments": [
                {
                    "position": j.position,
                    "post_id": str(j.post_id),
                    "relevance_score": j.relevance_score,
                    "reasoning": j.reasoning,
                }
                for j in judge.judgments
            ],
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    def _load_skipped_result(
        self, persona_id: str, version: str, run_num: int, seed: int, feed_path: Path
    ) -> CombinationResult:
        try:
            feed = FeedResult.from_json(feed_path.read_text(encoding="utf-8"))
        except Exception as exc:
            logger.warning("Could not load existing feed %s: %s", feed_path, exc)
            feed = None
        return CombinationResult(
            persona_id=persona_id,
            version=version,
            run_number=run_num,
            seed=seed,
            feed=feed,
            proxy=None,
            judge=None,
            duration_s=0.0,
            errors=["skipped: loaded from existing file"],
        )

    def _save_metrics_csv(self, results: list[CombinationResult]) -> None:
        path = self._output_dir / "metrics.csv"
        fieldnames = [
            "persona_id", "version", "run", "seed",
            "topic_entropy", "freshness_median_hours",
            "pairwise_cosine_avg", "topic_coverage",
            "distance_to_profile_avg", "long_tail_ratio",
            "dedup_pair_count", "category_mass_balance",
            "llm_mean", "llm_median", "llm_ndcg_at_10", "duration_s",
        ]
        with path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for r in results:
                row: dict[str, Any] = {
                    "persona_id": r.persona_id,
                    "version": r.version,
                    "run": r.run_number,
                    "seed": r.seed,
                    "duration_s": f"{r.duration_s:.3f}",
                }
                if r.proxy is not None:
                    row.update({
                        "topic_entropy": _fmt(r.proxy.topic_entropy),
                        "freshness_median_hours": _fmt(r.proxy.freshness_median_hours),
                        "pairwise_cosine_avg": _fmt(r.proxy.pairwise_cosine_avg),
                        "topic_coverage": r.proxy.topic_coverage,
                        "distance_to_profile_avg": _fmt(r.proxy.distance_to_profile_avg),
                        "long_tail_ratio": _fmt(r.proxy.long_tail_ratio),
                        "dedup_pair_count": r.proxy.dedup_pair_count,
                        "category_mass_balance": _fmt(r.proxy.category_mass_balance),
                    })
                else:
                    for field in fieldnames[4:12]:
                        row[field] = ""
                if r.judge is not None:
                    row.update({
                        "llm_mean": _fmt(r.judge.mean_relevance),
                        "llm_median": _fmt(r.judge.median_relevance),
                        "llm_ndcg_at_10": _fmt(r.judge.ndcg_at_10),
                    })
                else:
                    row["llm_mean"] = ""
                    row["llm_median"] = ""
                    row["llm_ndcg_at_10"] = ""
                writer.writerow(row)

    def _save_costs_csv(self, results: list[CombinationResult]) -> None:
        path = self._output_dir / "costs.csv"
        fieldnames = [
            "persona_id", "version", "run", "model",
            "input_tokens", "output_tokens", "cost_usd",
        ]
        with path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for r in results:
                if r.judge is not None:
                    writer.writerow({
                        "persona_id": r.persona_id,
                        "version": r.version,
                        "run": r.run_number,
                        "model": r.judge.model,
                        "input_tokens": r.judge.input_tokens,
                        "output_tokens": r.judge.output_tokens,
                        "cost_usd": r.judge.total_cost_usd,
                    })

    def _save_errors_log(self) -> None:
        path = self._output_dir / "errors.log"
        path.write_text("\n\n".join(self._all_errors), encoding="utf-8")

    def _save_config_artifacts(
        self, personas: list[Persona], versions: list[str]
    ) -> None:
        # personas_snapshot.yaml
        snap: dict = {}
        for p in personas:
            snap[p.id] = {
                "id": p.id,
                "display_name": p.display_name,
                "description": p.description,
                "base_interests": p.base_interests,
                "dislikes": p.dislikes,
                "preferred_entities": p.preferred_entities,
            }
        (self._output_dir / "personas_snapshot.yaml").write_text(
            yaml.dump(snap, allow_unicode=True, default_flow_style=False),
            encoding="utf-8",
        )

        # versions_used.yaml
        versions_data = {v: self._version_config.get(v, {}) for v in versions}
        (self._output_dir / "versions_used.yaml").write_text(
            yaml.dump(versions_data, allow_unicode=True, default_flow_style=False),
            encoding="utf-8",
        )

        # config_snapshot.yaml
        config_snap = {
            "env_vars": {
                k: v
                for k, v in os.environ.items()
                if k.startswith("REC_") or k.startswith("DATABASE")
            },
        }
        (self._output_dir / "config_snapshot.yaml").write_text(
            yaml.dump(config_snap, allow_unicode=True, default_flow_style=False),
            encoding="utf-8",
        )

    def _save_summary(
        self,
        results: list[CombinationResult],
        personas: list[Persona],
        versions: list[str],
        runs: int,
        start_time: datetime,
        end_time: datetime,
    ) -> None:
        template_path = _TEMPLATES_DIR / "summary.md.j2"
        if not template_path.exists():
            logger.warning("Summary template not found: %s — skipping summary.md", template_path)
            (self._output_dir / "summary.md").write_text(
                "# Benchmark Summary\n\n*(template not found)*\n", encoding="utf-8"
            )
            return

        persona_ids = [p.id for p in personas]
        duration_minutes = (end_time - start_time).total_seconds() / 60

        total_cost = sum(r.judge.total_cost_usd for r in results if r.judge)
        input_tokens = sum(r.judge.input_tokens for r in results if r.judge)
        output_tokens = sum(r.judge.output_tokens for r in results if r.judge)

        per_version: dict[str, dict[str, dict]] = {v: {} for v in versions}
        per_persona: dict[str, dict] = {}

        for pid in persona_ids:
            per_persona[pid] = _aggregate_metrics(
                [r for r in results if r.persona_id == pid]
            )
        for version in versions:
            for pid in persona_ids:
                per_version[version][pid] = _aggregate_metrics(
                    [r for r in results if r.persona_id == pid and r.version == version]
                )

        ctx = {
            "tag": self._tag,
            "timestamp": start_time.strftime("%Y-%m-%d %H:%M:%S UTC"),
            "duration_minutes": f"{duration_minutes:.1f}",
            "total_cost": total_cost,
            "n_personas": len(personas),
            "n_versions": len(versions),
            "n_runs": runs,
            "total_runs": len(personas) * len(versions) * runs,
            "git_commit": _get_git_commit(),
            "versions": versions,
            "persona_ids": persona_ids,
            "per_version": per_version,
            "per_persona": per_persona,
            "errors": self._all_errors,
            "seed": self._base_seed,
            "judge_model": self._judge._model if self._judge else None,
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
        }

        try:
            rendered = render_summary(template_path, ctx)
        except Exception as exc:
            logger.exception("Failed to render summary template")
            rendered = f"# Benchmark Summary\n\n*(rendering failed: {exc})*\n"

        (self._output_dir / "summary.md").write_text(rendered, encoding="utf-8")


# ---------------------------------------------------------------------------
# render_summary — standalone (testable without a full runner)
# ---------------------------------------------------------------------------


def render_summary(template_path: Path, context: dict) -> str:
    """Render the Jinja2 summary template with the given context dict."""
    from jinja2 import Template, Undefined

    template_text = template_path.read_text(encoding="utf-8")
    tpl = Template(template_text, undefined=Undefined)
    return tpl.render(**context)


# ---------------------------------------------------------------------------
# Private helpers
# ---------------------------------------------------------------------------


def _aggregate_metrics(results: list[CombinationResult]) -> dict:
    """Aggregate proxy + LLM metrics across runs for one persona/version bucket."""
    proxy_results = [r for r in results if r.proxy is not None]
    judge_results = [
        r for r in results
        if r.judge is not None and r.judge.mean_relevance is not None
    ]

    def _mean(vals: list) -> float:
        return statistics.mean(vals) if vals else float("nan")

    return {
        "topic_entropy": _mean(
            [r.proxy.topic_entropy for r in proxy_results]
        ),
        "freshness_h": _mean(
            [r.proxy.freshness_median_hours for r in proxy_results]
        ),
        "topic_coverage": (
            round(_mean([r.proxy.topic_coverage for r in proxy_results]))
            if proxy_results else 0
        ),
        "distance_to_profile_avg": _mean(
            [r.proxy.distance_to_profile_avg for r in proxy_results]
        ),
        "dedup_pair_count": (
            round(_mean([r.proxy.dedup_pair_count for r in proxy_results]))
            if proxy_results else 0
        ),
        "llm_mean": (
            _mean([r.judge.mean_relevance for r in judge_results])
            if judge_results else None
        ),
        "llm_median": (
            _mean([r.judge.median_relevance for r in judge_results])
            if judge_results else None
        ),
        "llm_ndcg_at_10": (
            _mean([r.judge.ndcg_at_10 for r in judge_results])
            if judge_results else None
        ),
    }


def _fmt(v: float | None) -> str:
    """Format a float for CSV; returns '' for None, 'nan' for NaN."""
    if v is None:
        return ""
    return str(v)


def _get_git_commit() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        return result.stdout.strip() or "unknown"
    except Exception:
        return "unknown"
