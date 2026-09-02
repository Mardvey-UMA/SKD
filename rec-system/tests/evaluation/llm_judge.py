"""Phase 5: LLM-as-Judge for feed quality evaluation.

Uses the Claude CLI (subprocess) to score each feed item's relevance to a
persona on a 0-5 scale. Returns structured JudgeResult with per-item
ItemJudgment objects, aggregate statistics, and cost tracking.
"""
from __future__ import annotations

import asyncio
import json
import logging
import math
import os
import statistics
import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from uuid import UUID

from tests.evaluation.feed_evaluator import FeedItem, FeedResult
from tests.evaluation.models import Persona

logger = logging.getLogger(__name__)

_PROMPT_TEMPLATE_PATH = Path(__file__).parent / "prompts" / "feed_judge.txt"
_PARSE_SUCCESS_THRESHOLD = 0.8  # at least 80% of items must parse successfully


@dataclass(frozen=True)
class ItemJudgment:
    position: int
    post_id: UUID
    relevance_score: int | None   # 0-5, or None on parse failure
    reasoning: str                # model's one-line justification


@dataclass(frozen=True)
class JudgeResult:
    persona_id: str
    version: str
    run_number: int
    model: str
    total_cost_usd: float
    input_tokens: int
    output_tokens: int
    mean_relevance: float | None   # None if all items failed to parse
    median_relevance: float | None
    ndcg_at_10: float | None       # relevance >= 4 = relevant for binary gain
    judgments: list[ItemJudgment]
    raw_response: str              # full LLM output for debugging
    retry_count: int = 0
    errors: list[str] = field(default_factory=list)


class LLMJudgeError(Exception):
    """Raised when the Claude CLI returns an error or times out."""


class LLMJudge:
    """Evaluates feed relevance for a persona using the Claude CLI.

    Uses subprocess.run to call the Claude CLI synchronously, then wraps
    it via asyncio.to_thread to avoid blocking the event loop.

    Env vars:
      REC_LLM_JUDGE_TIMEOUT    — default timeout_s (int, seconds, default 240)
      REC_LLM_JUDGE_MAX_RETRIES — default max_retries (int, default 1)
    """

    def __init__(
        self,
        claude_cli_path: str = "claude",
        model: str = "haiku",
        timeout_s: int | None = None,
        max_retries: int | None = None,
        snippet_max_len: int = 200,
    ) -> None:
        self._claude_cli_path = claude_cli_path
        self._model = model
        self._timeout_s = (
            timeout_s if timeout_s is not None
            else int(os.environ.get("REC_LLM_JUDGE_TIMEOUT", "240"))
        )
        self._max_retries = (
            max_retries if max_retries is not None
            else int(os.environ.get("REC_LLM_JUDGE_MAX_RETRIES", "1"))
        )
        self._snippet_max_len = snippet_max_len
        self._prompt_template = _PROMPT_TEMPLATE_PATH.read_text(encoding="utf-8")

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def judge(
        self,
        persona: Persona,
        feed: FeedResult,
    ) -> JudgeResult:
        """Evaluate feed relevance for the given persona.

        Retries up to max_retries times on timeout (with exponential backoff)
        or on JSON parse failure. Both failure types share the retry counter.
        """
        n = len(feed.items)
        base_prompt = self._build_prompt(persona, feed)

        raw_response = ""
        total_cost = 0.0
        input_tokens = 0
        output_tokens = 0
        retry_count = 0
        errors: list[str] = []
        judgments: list[ItemJudgment] = []

        current_prompt = base_prompt
        last_was_timeout = False

        for attempt in range(self._max_retries + 1):
            # Exponential backoff only for timeout retries (not parse retries).
            if attempt > 0 and last_was_timeout:
                delay = min(5 * (2 ** (retry_count - 1)), 20)
                logger.warning(
                    "LLMJudge timeout retry %d/%d, backoff %ds, post_count=%d",
                    attempt, self._max_retries, delay, n,
                )
                await asyncio.sleep(delay)

            try:
                result_text, payload = await asyncio.to_thread(
                    self._call_claude, current_prompt
                )
                last_was_timeout = False
            except LLMJudgeError as exc:
                if "timeout" in str(exc).lower() and attempt < self._max_retries:
                    last_was_timeout = True
                    retry_count += 1
                    continue  # retry same prompt; backoff applied next iteration
                raise  # non-timeout, or timeout with no retries left

            # Successful call — accumulate token/cost data.
            raw_response = result_text
            total_cost += payload.get("total_cost_usd", 0.0) or 0.0
            input_tokens += (payload.get("usage") or {}).get("input_tokens", 0)
            output_tokens += (payload.get("usage") or {}).get("output_tokens", 0)

            new_judgments, parse_errors = self._parse_response(result_text, feed)
            errors.extend(parse_errors)

            success_count = sum(1 for j in new_judgments if j.relevance_score is not None)
            parse_ok = (success_count / n) >= _PARSE_SUCCESS_THRESHOLD if n > 0 else True

            # Keep the best parse so far.
            existing_success = sum(1 for j in judgments if j.relevance_score is not None)
            if success_count > existing_success or not judgments:
                judgments = new_judgments

            if parse_ok:
                break

            if attempt < self._max_retries:
                retry_count += 1
                last_was_timeout = False
                current_prompt = (
                    f"RETRY: your previous output was not valid JSON. "
                    f"Return ONLY a valid JSON array, exactly {n} entries, no prose.\n\n"
                    + base_prompt
                )
            else:
                errors.append(
                    f"Parse failed after {retry_count} retries: "
                    f"only {success_count}/{n} items parsed"
                )

        # Fill any missing positions with None judgments.
        if len(judgments) < n:
            existing_positions = {j.position for j in judgments}
            for item in feed.items:
                if item.position not in existing_positions:
                    judgments.append(
                        ItemJudgment(
                            position=item.position,
                            post_id=item.post_id,
                            relevance_score=None,
                            reasoning="PARSE_FAILED_2X",
                        )
                    )
            judgments.sort(key=lambda j: j.position)

        mean_rel = self._compute_mean(judgments)
        median_rel = self._compute_median(judgments)
        ndcg = self._compute_ndcg(judgments, k=10)

        return JudgeResult(
            persona_id=persona.id,
            version=feed.version,
            run_number=feed.run_number,
            model=self._model,
            total_cost_usd=total_cost,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            mean_relevance=mean_rel,
            median_relevance=median_rel,
            ndcg_at_10=ndcg,
            judgments=judgments,
            raw_response=raw_response,
            retry_count=retry_count,
            errors=errors,
        )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _build_prompt(self, persona: Persona, feed: FeedResult) -> str:
        """Fill in the prompt template with persona and feed data."""
        n = len(feed.items)

        formatted_interests = ", ".join(
            f"{t}:{w:.2f}"
            for t, w in sorted(persona.base_interests.items(), key=lambda x: -x[1])
        )
        formatted_dislikes = ", ".join(
            f"{t}:{w:.2f}"
            for t, w in sorted(persona.dislikes.items(), key=lambda x: -x[1])
        )
        entities_str = (
            ", ".join(persona.preferred_entities) if persona.preferred_entities else "—"
        )

        item_parts: list[str] = []
        for item in feed.items:
            top_topic = item.topics[0][0] if item.topics else "unknown"
            title = item.title if item.title else item.content_snippet[:80]
            snippet = item.content_snippet[:self._snippet_max_len]
            age_hours = item.age_hours if item.age_hours is not None else 0.0
            sentiment_label = item.sentiment[0] if item.sentiment else "neutral"

            item_str = (
                f"{item.position}. [{top_topic}] \"{title}\"\n"
                f"   {snippet}\n"
                f"   Age: {age_hours:.1f}h | Sentiment: {sentiment_label}"
            )
            item_parts.append(item_str)

        formatted_items = "\n\n".join(item_parts)

        prompt = self._prompt_template.format(
            display_name=persona.display_name,
            description=persona.description.strip(),
            formatted_interests=formatted_interests,
            formatted_dislikes=formatted_dislikes,
            entities=entities_str,
            N=n,
            formatted_items=formatted_items,
        )
        return prompt

    def _call_claude(self, prompt: str) -> tuple[str, dict]:
        """Call the Claude CLI synchronously. Raises LLMJudgeError on failure."""
        try:
            proc = subprocess.run(
                [
                    self._claude_cli_path,
                    "-p", prompt,
                    "--model", self._model,
                    "--output-format", "json",
                    "--max-turns", "1",
                    "--dangerously-skip-permissions",
                ],
                capture_output=True,
                text=True,
                timeout=self._timeout_s,
                env=os.environ.copy(),
            )
        except subprocess.TimeoutExpired as exc:
            raise LLMJudgeError(f"timeout: Claude CLI timed out after {self._timeout_s}s") from exc

        if proc.returncode != 0:
            raise LLMJudgeError(f"CLI exit {proc.returncode}: {proc.stderr[:500]}")

        try:
            payload = json.loads(proc.stdout)
        except json.JSONDecodeError as exc:
            raise LLMJudgeError(f"Could not parse CLI JSON output: {exc}") from exc

        if payload.get("is_error"):
            raise LLMJudgeError(f"Claude error: {payload.get('result', '<no result>')}")

        return payload["result"], payload

    def _strip_markdown(self, text: str) -> str:
        """Strip markdown code fences if present."""
        text = text.strip()
        if text.startswith("```"):
            # Remove opening fence (e.g., ```json or ```)
            first_newline = text.find("\n")
            if first_newline != -1:
                text = text[first_newline + 1:]
            if text.endswith("```"):
                text = text[: -len("```")]
            text = text.strip()
        return text

    def _parse_response(
        self, result_text: str, feed: FeedResult
    ) -> tuple[list[ItemJudgment], list[str]]:
        """Parse the LLM output into ItemJudgment objects.

        Returns (judgments, errors). Missing/wrong-type entries get relevance_score=None.
        """
        n = len(feed.items)
        post_id_map = {item.position: item.post_id for item in feed.items}
        errors: list[str] = []

        cleaned = self._strip_markdown(result_text)

        try:
            raw = json.loads(cleaned)
        except json.JSONDecodeError as exc:
            errors.append(f"JSON parse error: {exc}")
            return [], errors

        if not isinstance(raw, list):
            errors.append(f"Expected JSON array, got {type(raw).__name__}")
            return [], errors

        judgments: list[ItemJudgment] = []
        for entry in raw:
            if not isinstance(entry, dict):
                errors.append(f"Expected dict entry, got {type(entry).__name__}")
                continue

            # Validate position
            pos = entry.get("position")
            if not isinstance(pos, int) or pos < 1 or pos > n:
                errors.append(f"Invalid position: {pos!r}")
                continue

            post_id = post_id_map.get(pos, UUID(int=0))

            # Validate relevance
            rel = entry.get("relevance")
            if not isinstance(rel, int) or rel < 0 or rel > 5:
                judgments.append(
                    ItemJudgment(
                        position=pos,
                        post_id=post_id,
                        relevance_score=None,
                        reasoning=f"PARSE_ERROR: invalid relevance {rel!r}",
                    )
                )
                continue

            reason = str(entry.get("reason", ""))

            judgments.append(
                ItemJudgment(
                    position=pos,
                    post_id=post_id,
                    relevance_score=rel,
                    reasoning=reason,
                )
            )

        judgments.sort(key=lambda j: j.position)
        return judgments, errors

    # ------------------------------------------------------------------
    # Statistics helpers (also used in tests directly)
    # ------------------------------------------------------------------

    def _compute_mean(self, judgments: list[ItemJudgment]) -> float | None:
        """Mean relevance score, ignoring None entries."""
        scored = [j.relevance_score for j in judgments if j.relevance_score is not None]
        if not scored:
            return None
        return statistics.mean(scored)

    def _compute_median(self, judgments: list[ItemJudgment]) -> float | None:
        """Median relevance score, ignoring None entries."""
        scored = [j.relevance_score for j in judgments if j.relevance_score is not None]
        if not scored:
            return None
        return statistics.median(scored)

    def _compute_ndcg(self, judgments: list[ItemJudgment], k: int = 10) -> float | None:
        """nDCG@k using binary gain: 1 if score >= 4 else 0.

        Returns None if no judgments have relevance scores.
        """
        scored = [j for j in judgments if j.relevance_score is not None]
        if not scored:
            return None

        # Sort by position (should already be sorted)
        scored_sorted = sorted(scored, key=lambda j: j.position)
        top_k = scored_sorted[:k]

        def gain(score: int) -> int:
            return 1 if score >= 4 else 0

        # DCG: sum over top-k positions (1-indexed)
        dcg = sum(
            gain(j.relevance_score) / math.log2(rank + 1)
            for rank, j in enumerate(top_k, start=1)
        )

        # IDCG: ideal ordering — all relevant items first
        all_gains = sorted([gain(j.relevance_score) for j in scored], reverse=True)
        ideal_top_k = all_gains[:k]
        idcg = sum(
            g / math.log2(rank + 1)
            for rank, g in enumerate(ideal_top_k, start=1)
            if g > 0
        )

        if idcg == 0.0:
            return 0.0

        return dcg / idcg
