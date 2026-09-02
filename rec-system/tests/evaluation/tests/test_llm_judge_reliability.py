"""Unit tests for LLMJudge reliability: timeout retry, env config, compact snippets.

Phase P2b RED tests — all imports succeed but behavior is not yet implemented.
"""
from __future__ import annotations

import os
import subprocess
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

import pytest

from tests.evaluation.feed_evaluator import FeedItem, FeedResult
from tests.evaluation.llm_judge import LLMJudge, LLMJudgeError
from tests.evaluation.models import InteractionPattern, Persona

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _make_persona() -> Persona:
    return Persona(
        id="tech-geek",
        display_name="Technology Enthusiast",
        description="Loves AI and science",
        base_interests={"технологии": 0.70, "наука": 0.30},
        dislikes={"политика": 0.90},
        preferred_entities=["OpenAI"],
        pattern=InteractionPattern(
            like_probability_on_interest=0.5,
            bookmark_probability_on_interest=0.2,
            dislike_probability_on_dislike=0.6,
            skip_probability_on_dislike=0.8,
            avg_dwell_seconds=30,
            scroll_depth_p50=0.7,
            interactions_per_session=20,
        ),
    )


def _make_feed(n: int = 3) -> FeedResult:
    items = [
        FeedItem(
            position=i + 1,
            post_id=uuid4(),
            raw_content_id=uuid4(),
            title=f"Article {i + 1}",
            content_snippet="x" * 250,  # longer than both 100 and 200 limits
            topics=[("технологии", 0.85)],
            sentiment=("NEUTRAL", 0.5),
            entities={},
            embedding=[0.1] * 312,
            text_metrics={"word_count": 100},
            source_id=uuid4(),
            age_hours=2.5,
        )
        for i in range(n)
    ]
    return FeedResult(
        user_id=uuid4(),
        persona_id="tech-geek",
        version="baseline",
        seed=42,
        run_number=1,
        generated_at=datetime.now(timezone.utc),
        items=items,
        request_latency_ms=100.0,
        count=n,
    )


def _make_valid_proc(scores: list[dict]) -> MagicMock:
    import json

    payload = {
        "result": json.dumps(scores),
        "is_error": False,
        "total_cost_usd": 0.001,
        "usage": {"input_tokens": 100, "output_tokens": 50},
    }
    mock = MagicMock()
    mock.returncode = 0
    mock.stdout = json.dumps(payload)
    mock.stderr = ""
    return mock


def _default_scores(n: int) -> list[dict]:
    return [{"position": i + 1, "relevance": 4, "reason": "good"} for i in range(n)]


# ---------------------------------------------------------------------------
# Fix 1: Default timeout is 240
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_default_timeout_is_240(monkeypatch):
    """LLMJudge default timeout_s is 240 when REC_LLM_JUDGE_TIMEOUT is not set."""
    monkeypatch.delenv("REC_LLM_JUDGE_TIMEOUT", raising=False)
    judge = LLMJudge()
    assert judge._timeout_s == 240


@pytest.mark.unit
def test_env_var_overrides_timeout(monkeypatch):
    """REC_LLM_JUDGE_TIMEOUT=60 sets timeout_s=60."""
    monkeypatch.setenv("REC_LLM_JUDGE_TIMEOUT", "60")
    judge = LLMJudge()
    assert judge._timeout_s == 60


@pytest.mark.unit
def test_explicit_timeout_kwarg_overrides_env(monkeypatch):
    """Explicit timeout_s=300 takes precedence over REC_LLM_JUDGE_TIMEOUT."""
    monkeypatch.setenv("REC_LLM_JUDGE_TIMEOUT", "60")
    judge = LLMJudge(timeout_s=300)
    assert judge._timeout_s == 300


# ---------------------------------------------------------------------------
# Fix 2: Env var REC_LLM_JUDGE_MAX_RETRIES
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_env_var_overrides_max_retries(monkeypatch):
    """REC_LLM_JUDGE_MAX_RETRIES=3 sets max_retries=3."""
    monkeypatch.setenv("REC_LLM_JUDGE_MAX_RETRIES", "3")
    judge = LLMJudge()
    assert judge._max_retries == 3


@pytest.mark.unit
def test_explicit_max_retries_kwarg_overrides_env(monkeypatch):
    """Explicit max_retries=0 takes precedence over REC_LLM_JUDGE_MAX_RETRIES."""
    monkeypatch.setenv("REC_LLM_JUDGE_MAX_RETRIES", "5")
    judge = LLMJudge(max_retries=0)
    assert judge._max_retries == 0


# ---------------------------------------------------------------------------
# Fix 2: Retry on timeout — success on second attempt
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_timeout_retry_then_success():
    """TimeoutExpired on first attempt → retry → success on second, retry_count=1."""
    judge = LLMJudge(max_retries=1)
    persona = _make_persona()
    feed = _make_feed(3)
    good_proc = _make_valid_proc(_default_scores(3))

    with patch("subprocess.run", side_effect=[
        subprocess.TimeoutExpired("claude", 240),
        good_proc,
    ]):
        with patch("asyncio.sleep", new=AsyncMock(return_value=None)):
            result = await judge.judge(persona, feed)

    assert result.retry_count == 1
    assert all(j.relevance_score == 4 for j in result.judgments)


@pytest.mark.unit
@pytest.mark.asyncio
async def test_timeout_retry_increments_retry_count():
    """retry_count reflects the number of retry attempts made due to timeout."""
    judge = LLMJudge(max_retries=1)
    persona = _make_persona()
    feed = _make_feed(2)
    good_proc = _make_valid_proc(_default_scores(2))

    with patch("subprocess.run", side_effect=[
        subprocess.TimeoutExpired("claude", 240),
        good_proc,
    ]):
        with patch("asyncio.sleep", new=AsyncMock(return_value=None)):
            result = await judge.judge(persona, feed)

    assert result.retry_count >= 1


# ---------------------------------------------------------------------------
# Fix 2: All retries exhaust on timeout → LLMJudgeError raised
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_all_retries_timeout_raises_llm_judge_error():
    """All attempts time out → LLMJudgeError with 'timeout' in message."""
    judge = LLMJudge(max_retries=1)
    persona = _make_persona()
    feed = _make_feed(2)

    with patch("subprocess.run", side_effect=subprocess.TimeoutExpired("claude", 240)):
        with patch("asyncio.sleep", new=AsyncMock(return_value=None)):
            with pytest.raises(LLMJudgeError, match="timeout"):
                await judge.judge(persona, feed)


@pytest.mark.unit
@pytest.mark.asyncio
async def test_zero_retries_timeout_raises_immediately():
    """With max_retries=0, a single timeout raises LLMJudgeError immediately."""
    judge = LLMJudge(max_retries=0)
    persona = _make_persona()
    feed = _make_feed(2)

    with patch("subprocess.run", side_effect=subprocess.TimeoutExpired("claude", 240)):
        with pytest.raises(LLMJudgeError, match="timeout"):
            await judge.judge(persona, feed)


# ---------------------------------------------------------------------------
# Fix 2: Backoff sleep is called on timeout retries
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_backoff_sleep_called_on_timeout_retry():
    """asyncio.sleep is called with a positive delay when retrying after timeout."""
    judge = LLMJudge(max_retries=1)
    persona = _make_persona()
    feed = _make_feed(2)
    good_proc = _make_valid_proc(_default_scores(2))

    sleep_mock = AsyncMock(return_value=None)
    with patch("subprocess.run", side_effect=[
        subprocess.TimeoutExpired("claude", 240),
        good_proc,
    ]):
        with patch("asyncio.sleep", new=sleep_mock):
            await judge.judge(persona, feed)

    sleep_mock.assert_called_once()
    delay_arg = sleep_mock.call_args[0][0]
    assert delay_arg > 0, f"Expected positive backoff delay, got {delay_arg}"


# ---------------------------------------------------------------------------
# Fix 2: Warning logged on retry
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_warning_logged_on_timeout_retry():
    """logger.warning is called when retrying after timeout."""
    import tests.evaluation.llm_judge as llm_module

    judge = LLMJudge(max_retries=1)
    persona = _make_persona()
    feed = _make_feed(2)
    good_proc = _make_valid_proc(_default_scores(2))

    warning_calls: list[str] = []

    def _capture_warning(msg, *args, **kwargs):
        warning_calls.append(msg % args if args else msg)

    with patch.object(llm_module.logger, "warning", side_effect=_capture_warning):
        with patch("subprocess.run", side_effect=[
            subprocess.TimeoutExpired("claude", 240),
            good_proc,
        ]):
            with patch("asyncio.sleep", new=AsyncMock(return_value=None)):
                await judge.judge(persona, feed)

    assert warning_calls, "Expected at least one logger.warning call on timeout retry"
    combined = " ".join(warning_calls).lower()
    assert "retry" in combined or "timeout" in combined


# ---------------------------------------------------------------------------
# Fix 3: snippet_max_len=100 truncates to 100 chars
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_compact_snippets_truncates_to_100():
    """snippet_max_len=100 causes each item snippet to be at most 100 chars in prompt."""
    judge = LLMJudge(snippet_max_len=100)
    persona = _make_persona()
    feed = _make_feed(1)  # item has content_snippet = "x" * 250

    prompt = judge._build_prompt(persona, feed)

    assert "x" * 101 not in prompt, "Snippet should not exceed 100 chars"
    assert "x" * 100 in prompt, "Exactly 100 'x' chars should appear in prompt"


@pytest.mark.unit
def test_default_snippet_length_is_200():
    """Default snippet_max_len=200 preserves up to 200 chars of snippet."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(1)  # item has content_snippet = "x" * 250

    prompt = judge._build_prompt(persona, feed)

    assert "x" * 200 in prompt, "200 'x' chars should appear with default snippet_max_len"
    assert "x" * 201 not in prompt, "More than 200 'x' chars should not appear"
