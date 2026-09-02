"""Regression tests: InteractionsBatchConsumer accepts v2 InteractionEvent schema.

Schema v2 (P8a) adds batch-level `schema_version` and optional per-event fields:
  feed_request_id, position_in_feed, device_type, app_version, ab_bucket,
  scroll_depth, metadata.

All new fields use @JsonInclude(NON_NULL) — absent when null, never "field": null.
The consumer reads only known keys; unknown keys are silently ignored by dict lookup.

These tests lock in that existing lenient behavior so any future regression is caught.
"""
from __future__ import annotations

import json
import pytest
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

def _make_msg(payload: dict) -> MagicMock:
    msg = MagicMock()
    msg.value = json.dumps(payload).encode("utf-8")
    return msg


def _base_interaction(content_id: str | None = None) -> dict:
    return {
        "content_id": content_id or str(uuid4()),
        "action_type": "view",
        "duration_sec": 30,
        "timestamp": "2026-04-20T10:00:00Z",
    }


def _base_payload(user_id: str | None = None, interactions: list | None = None) -> dict:
    return {
        "event_type": "user.interactions.batch",
        "user_id": user_id or str(uuid4()),
        "batch_ts": "2026-04-20T10:01:00Z",
        "interactions": interactions or [_base_interaction()],
    }


# ---------------------------------------------------------------------------
# fixture
# ---------------------------------------------------------------------------

@pytest.fixture
def use_case_mock():
    return AsyncMock()


@pytest.fixture
def consumer_wrapper_mock():
    return AsyncMock()


@pytest.fixture
def consumer(consumer_wrapper_mock, use_case_mock):
    from src.presentation.consumers.interactions_batch_consumer import InteractionsBatchConsumer
    return InteractionsBatchConsumer(consumer=consumer_wrapper_mock, use_case=use_case_mock)


# ---------------------------------------------------------------------------
# tests
# ---------------------------------------------------------------------------

class TestInteractionsBatchV2SchemaRegression:
    """Consumer must accept all v2 payload variants without crashing."""

    # ── Test 1: full v2 — all 7 new event fields populated ──────────────────

    @pytest.mark.asyncio
    async def test_accepts_v2_event_all_new_fields_populated(self, consumer, use_case_mock):
        """v2 batch with schema_version=2 and every new event field present."""
        user_id = str(uuid4())
        content_id = str(uuid4())
        interaction = {
            **_base_interaction(content_id),
            "feed_request_id": str(uuid4()),
            "position_in_feed": 3,
            "device_type": "android",
            "app_version": "2.1.0",
            "ab_bucket": "variant_a",
            "scroll_depth": 0.87,
            "metadata": {"screen": "feed", "session_id": str(uuid4())},
        }
        payload = {
            **_base_payload(user_id=user_id, interactions=[interaction]),
            "schema_version": 2,
        }

        await consumer._process_message(_make_msg(payload))

        use_case_mock.execute.assert_called_once()
        event = use_case_mock.execute.call_args[0][0]
        assert str(event.user_id) == user_id
        assert len(event.interactions) == 1
        assert str(event.interactions[0].content_id) == content_id

    # ── Test 2: sparse v2 — only some new fields present ────────────────────

    @pytest.mark.asyncio
    async def test_accepts_v2_event_sparse_new_fields(self, consumer, use_case_mock):
        """v2 batch where only feed_request_id + position_in_feed are present."""
        user_id = str(uuid4())
        interaction = {
            **_base_interaction(),
            "feed_request_id": str(uuid4()),
            "position_in_feed": 7,
            # device_type, app_version, ab_bucket, scroll_depth, metadata — absent
        }
        payload = {
            **_base_payload(user_id=user_id, interactions=[interaction]),
            "schema_version": 2,
        }

        await consumer._process_message(_make_msg(payload))

        use_case_mock.execute.assert_called_once()
        event = use_case_mock.execute.call_args[0][0]
        assert str(event.user_id) == user_id

    # ── Test 3: v2 batch-level only — no new event fields ───────────────────

    @pytest.mark.asyncio
    async def test_accepts_v2_batch_schema_version_only_no_new_event_fields(
        self, consumer, use_case_mock
    ):
        """v1 frontend client posting to v2 backend: schema_version=2 but no new event fields.

        This is the most common migration case and must be fully backward-compatible.
        """
        user_id = str(uuid4())
        payload = {
            **_base_payload(user_id=user_id),
            "schema_version": 2,
        }

        await consumer._process_message(_make_msg(payload))

        use_case_mock.execute.assert_called_once()
        assert str(use_case_mock.execute.call_args[0][0].user_id) == user_id

    # ── Test 4: v1 batch — no schema_version at all ──────────────────────────

    @pytest.mark.asyncio
    async def test_accepts_v1_batch_no_schema_version(self, consumer, use_case_mock):
        """Pure v1 payload with no schema_version. Backward compat must not regress."""
        user_id = str(uuid4())
        payload = _base_payload(user_id=user_id)
        assert "schema_version" not in payload  # sanity-check fixture

        await consumer._process_message(_make_msg(payload))

        use_case_mock.execute.assert_called_once()
        assert str(use_case_mock.execute.call_args[0][0].user_id) == user_id

    # ── Test 5: garbage / complex metadata field ──────────────────────────────

    @pytest.mark.asyncio
    async def test_accepts_complex_nested_metadata_does_not_crash(
        self, consumer, use_case_mock
    ):
        """metadata is a free-form dict. Deeply nested or unexpected types must not crash."""
        interaction = {
            **_base_interaction(),
            "metadata": {
                "nested": {"level2": {"level3": [1, 2, None, True]}},
                "unicode_key": "Привет",
                "empty_list": [],
                "null_value": None,
            },
        }
        payload = {
            **_base_payload(interactions=[interaction]),
            "schema_version": 2,
        }

        await consumer._process_message(_make_msg(payload))

        use_case_mock.execute.assert_called_once()

    # ── Test 6: unknown top-level event field (future extension) ─────────────

    @pytest.mark.asyncio
    async def test_unknown_event_field_is_silently_ignored(self, consumer, use_case_mock):
        """A totally unknown field injected by a future client version is silently dropped."""
        interaction = {
            **_base_interaction(),
            "foo_bar": "xyz",
            "future_flag": True,
            "unknown_int": 42,
        }
        payload = {
            **_base_payload(interactions=[interaction]),
            "schema_version": 2,
            "future_batch_field": "ignored",
        }

        await consumer._process_message(_make_msg(payload))

        use_case_mock.execute.assert_called_once()
        # Core fields still parsed correctly despite unknown extras
        event = use_case_mock.execute.call_args[0][0]
        assert len(event.interactions) == 1
        assert event.interactions[0].action_type == "view"
