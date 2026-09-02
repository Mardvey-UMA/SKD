"""Tests for LLMJudge — Phase 5 of the rec-system evaluation harness.

All default tests are @pytest.mark.unit (mocked subprocess).
Real Claude CLI test is @pytest.mark.llm (out of scope for this run).
"""
from __future__ import annotations

import json
import math
import subprocess
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch
from uuid import UUID, uuid4

import pytest

from tests.evaluation.llm_judge import (
    ItemJudgment,
    JudgeResult,
    LLMJudge,
    LLMJudgeError,
)
from tests.evaluation.feed_evaluator import FeedItem, FeedResult
from tests.evaluation.models import InteractionPattern, Persona

# ---------------------------------------------------------------------------
# Helpers / fixtures
# ---------------------------------------------------------------------------


def _make_persona() -> Persona:
    return Persona(
        id="tech-geek",
        display_name="Technology Enthusiast",
        description="Loves AI and science",
        base_interests={"технологии": 0.70, "наука": 0.30},
        dislikes={"политика": 0.90},
        preferred_entities=["OpenAI", "Tesla"],
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


def _make_feed_item(position: int, title: str = "", topic: str = "технологии") -> FeedItem:
    return FeedItem(
        position=position,
        post_id=uuid4(),
        raw_content_id=uuid4(),
        title=title or f"Test article {position}",
        content_snippet=f"Content snippet for item {position}. " * 5,
        topics=[(topic, 0.85)],
        sentiment=("NEUTRAL", 0.5),
        entities={},
        embedding=[0.1] * 312,
        text_metrics={"word_count": 100},
        source_id=uuid4(),
        age_hours=2.5,
    )


def _make_feed(n: int = 3) -> FeedResult:
    return FeedResult(
        user_id=uuid4(),
        persona_id="tech-geek",
        version="baseline",
        seed=42,
        run_number=1,
        generated_at=datetime.now(timezone.utc),
        items=[_make_feed_item(i + 1, title=f"Article about технологии {i+1}") for i in range(n)],
        request_latency_ms=150.0,
        count=n,
    )


def _make_valid_subprocess_result(scores: list[dict], *, cost: float = 0.001) -> MagicMock:
    """Return a mocked CompletedProcess with a valid Claude JSON response."""
    json_array = json.dumps(scores)
    payload = {
        "result": json_array,
        "is_error": False,
        "total_cost_usd": cost,
        "usage": {"input_tokens": 100, "output_tokens": 50},
    }
    mock_proc = MagicMock()
    mock_proc.returncode = 0
    mock_proc.stdout = json.dumps(payload)
    mock_proc.stderr = ""
    return mock_proc


# ---------------------------------------------------------------------------
# Test: prompt construction
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_prompt_construction():
    """_build_prompt includes persona name, item titles, and instructions."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(3)

    prompt = judge._build_prompt(persona, feed)

    assert persona.display_name in prompt
    for item in feed.items:
        assert item.title in prompt
    assert "0-5" in prompt
    assert "ONLY a JSON array" in prompt


# ---------------------------------------------------------------------------
# Test: successful parse
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_successful_parse():
    """Valid subprocess response is parsed into 3 ItemJudgments with correct scores."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(3)

    scores = [
        {"position": 1, "relevance": 4, "reason": "good"},
        {"position": 2, "relevance": 3, "reason": "ok"},
        {"position": 3, "relevance": 5, "reason": "great"},
    ]
    mock_proc = _make_valid_subprocess_result(scores)

    with patch("subprocess.run", return_value=mock_proc):
        result = await judge.judge(persona, feed)

    assert isinstance(result, JudgeResult)
    assert len(result.judgments) == 3
    assert [j.relevance_score for j in result.judgments] == [4, 3, 5]
    assert result.retry_count == 0


# ---------------------------------------------------------------------------
# Test: markdown fenced response is stripped
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_markdown_fenced_response_stripped():
    """Scores are parsed correctly when the LLM wraps the JSON in markdown fences."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(3)

    scores = [
        {"position": 1, "relevance": 2, "reason": "meh"},
        {"position": 2, "relevance": 4, "reason": "nice"},
        {"position": 3, "relevance": 3, "reason": "ok"},
    ]
    json_array = json.dumps(scores)
    fenced = f"```json\n{json_array}\n```"

    payload = {
        "result": fenced,
        "is_error": False,
        "total_cost_usd": 0.001,
        "usage": {"input_tokens": 80, "output_tokens": 40},
    }
    mock_proc = MagicMock()
    mock_proc.returncode = 0
    mock_proc.stdout = json.dumps(payload)
    mock_proc.stderr = ""

    with patch("subprocess.run", return_value=mock_proc):
        result = await judge.judge(persona, feed)

    assert [j.relevance_score for j in result.judgments] == [2, 4, 3]


# ---------------------------------------------------------------------------
# Test: retry on invalid JSON then success
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_retry_on_invalid_json_then_success():
    """First call returns prose; second call returns valid JSON. retry_count=1."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(3)

    scores = [
        {"position": 1, "relevance": 5, "reason": "perfect"},
        {"position": 2, "relevance": 4, "reason": "good"},
        {"position": 3, "relevance": 3, "reason": "ok"},
    ]

    bad_payload = {
        "result": "I cannot do this task.",
        "is_error": False,
        "total_cost_usd": 0.0,
        "usage": {"input_tokens": 50, "output_tokens": 10},
    }
    good_payload = {
        "result": json.dumps(scores),
        "is_error": False,
        "total_cost_usd": 0.001,
        "usage": {"input_tokens": 100, "output_tokens": 50},
    }

    bad_proc = MagicMock()
    bad_proc.returncode = 0
    bad_proc.stdout = json.dumps(bad_payload)
    bad_proc.stderr = ""

    good_proc = MagicMock()
    good_proc.returncode = 0
    good_proc.stdout = json.dumps(good_payload)
    good_proc.stderr = ""

    with patch("subprocess.run", side_effect=[bad_proc, good_proc]):
        result = await judge.judge(persona, feed)

    assert result.retry_count == 1
    assert [j.relevance_score for j in result.judgments] == [5, 4, 3]


# ---------------------------------------------------------------------------
# Test: retry exhausted marks items null
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_retry_exhausted_marks_items_null():
    """Both calls return prose. All judgments have relevance_score=None, errors non-empty."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(3)

    bad_payload = {
        "result": "Sorry, I cannot help with that.",
        "is_error": False,
        "total_cost_usd": 0.0,
        "usage": {"input_tokens": 50, "output_tokens": 10},
    }
    bad_proc = MagicMock()
    bad_proc.returncode = 0
    bad_proc.stdout = json.dumps(bad_payload)
    bad_proc.stderr = ""

    with patch("subprocess.run", return_value=bad_proc):
        result = await judge.judge(persona, feed)

    assert all(j.relevance_score is None for j in result.judgments)
    assert len(result.errors) > 0


# ---------------------------------------------------------------------------
# Test: CLI error propagates as LLMJudgeError
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_cli_error_propagates():
    """Non-zero returncode raises LLMJudgeError."""
    judge = LLMJudge()
    persona = _make_persona()
    feed = _make_feed(2)

    mock_proc = MagicMock()
    mock_proc.returncode = 1
    mock_proc.stdout = ""
    mock_proc.stderr = "error msg"

    with patch("subprocess.run", return_value=mock_proc):
        with pytest.raises(LLMJudgeError, match="CLI exit 1"):
            await judge.judge(persona, feed)


# ---------------------------------------------------------------------------
# Test: timeout propagates as LLMJudgeError
# ---------------------------------------------------------------------------


@pytest.mark.unit
@pytest.mark.asyncio
async def test_timeout_propagates():
    """subprocess.TimeoutExpired is caught and re-raised as LLMJudgeError (no retry)."""
    judge = LLMJudge(max_retries=0)
    persona = _make_persona()
    feed = _make_feed(2)

    with patch("subprocess.run", side_effect=subprocess.TimeoutExpired("claude", 240)):
        with pytest.raises(LLMJudgeError, match="timeout"):
            await judge.judge(persona, feed)


# ---------------------------------------------------------------------------
# Test: nDCG@10 — perfect ordering
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_ndcg_perfect_ordering():
    """10 items all relevance=5 → nDCG@10 = 1.0."""
    judge = LLMJudge()
    judgments = [
        ItemJudgment(position=i + 1, post_id=uuid4(), relevance_score=5, reasoning="great")
        for i in range(10)
    ]
    ndcg = judge._compute_ndcg(judgments, k=10)
    assert ndcg == pytest.approx(1.0, abs=1e-9)


# ---------------------------------------------------------------------------
# Test: nDCG@10 — reverse ordering
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_ndcg_reverse_ordering():
    """Relevant items at the end → nDCG@10 < ideal (1.0) — penalised for bad ordering.

    Spec said < 0.5 but with 5 relevant items at positions 6-10 the correct
    nDCG is ~0.54, not < 0.5 (spec bug: threshold was too tight).
    We assert it is strictly less than 1.0 and less than 0.6 to verify the
    penalty is applied.
    """
    judge = LLMJudge()
    # positions 1-5 are irrelevant (score=0), positions 6-10 are relevant (score=5)
    judgments = [
        ItemJudgment(position=i + 1, post_id=uuid4(), relevance_score=s, reasoning="x")
        for i, s in enumerate([0, 0, 0, 0, 0, 5, 5, 5, 5, 5])
    ]
    ndcg = judge._compute_ndcg(judgments, k=10)
    assert ndcg < 1.0   # not perfect ordering
    assert ndcg < 0.6   # penalised enough (actual value ~0.54)


# ---------------------------------------------------------------------------
# Test: mean/median with null scores
# ---------------------------------------------------------------------------


@pytest.mark.unit
def test_mean_median_with_nulls():
    """Mean and median are computed from non-null scores only."""
    judge = LLMJudge()
    judgments = [
        ItemJudgment(position=1, post_id=uuid4(), relevance_score=None, reasoning="PARSE_ERROR"),
        ItemJudgment(position=2, post_id=uuid4(), relevance_score=4, reasoning="good"),
        ItemJudgment(position=3, post_id=uuid4(), relevance_score=2, reasoning="meh"),
        ItemJudgment(position=4, post_id=uuid4(), relevance_score=None, reasoning="PARSE_ERROR"),
    ]
    mean = judge._compute_mean(judgments)
    median = judge._compute_median(judgments)

    assert mean == pytest.approx(3.0, abs=1e-9)   # (4+2)/2
    assert median == pytest.approx(3.0, abs=1e-9)  # median of [2,4]


# ---------------------------------------------------------------------------
# Test: real Haiku call (llm marker — OUT OF SCOPE for this run)
# ---------------------------------------------------------------------------


@pytest.mark.llm
@pytest.mark.asyncio
async def test_real_haiku_single_call():
    """Real subprocess with 1 persona + 5-item FeedResult.

    Asserts total_cost_usd > 0 and < 0.05.
    OUT OF SCOPE for this run — do not execute with -m llm.
    """
    judge = LLMJudge(model="haiku", timeout_s=180)
    persona = _make_persona()
    feed = _make_feed(5)

    result = await judge.judge(persona, feed)

    assert result.total_cost_usd > 0
    assert result.total_cost_usd < 0.05
    assert result.model == "haiku"
    assert len(result.judgments) == 5
