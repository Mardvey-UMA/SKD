import pytest
from datetime import datetime, timezone
from uuid import UUID

from src.domain.entities.user_interaction import UserInteraction


class TestUserInteraction:
    def test_create_impression(self) -> None:
        now = datetime.now(timezone.utc)
        interaction = UserInteraction(
            id=1,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440000"),
            user_id="user-1",
            post_id=100,
            event_type="IMPRESSION",
            duration_ms=3500,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=now,
            processed=False,
        )
        assert interaction.id == 1
        assert interaction.event_id == UUID("550e8400-e29b-41d4-a716-446655440000")
        assert interaction.user_id == "user-1"
        assert interaction.post_id == 100
        assert interaction.event_type == "IMPRESSION"
        assert interaction.duration_ms == 3500
        assert interaction.scroll_pct is None
        assert interaction.max_scroll_pct is None
        assert interaction.processed is False

    def test_create_close(self) -> None:
        now = datetime.now(timezone.utc)
        interaction = UserInteraction(
            id=2,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440001"),
            user_id="user-1",
            post_id=102,
            event_type="CLOSE",
            duration_ms=165000,
            scroll_pct=0.87,
            max_scroll_pct=0.92,
            created_at=now,
            processed=False,
        )
        assert interaction.event_type == "CLOSE"
        assert interaction.duration_ms == 165000
        assert interaction.scroll_pct == pytest.approx(0.87)
        assert interaction.max_scroll_pct == pytest.approx(0.92)

    def test_create_like(self) -> None:
        now = datetime.now(timezone.utc)
        interaction = UserInteraction(
            id=3,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440002"),
            user_id="user-2",
            post_id=200,
            event_type="LIKE",
            duration_ms=None,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=now,
            processed=False,
        )
        assert interaction.event_type == "LIKE"
        assert interaction.duration_ms is None
        assert interaction.scroll_pct is None
        assert interaction.max_scroll_pct is None

    def test_is_processed(self) -> None:
        now = datetime.now(timezone.utc)
        unprocessed = UserInteraction(
            id=1,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440000"),
            user_id="user-1",
            post_id=100,
            event_type="LIKE",
            duration_ms=None,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=now,
            processed=False,
        )
        processed = UserInteraction(
            id=2,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440001"),
            user_id="user-1",
            post_id=100,
            event_type="LIKE",
            duration_ms=None,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=now,
            processed=True,
        )
        assert unprocessed.is_processed is False
        assert processed.is_processed is True

    def test_has_paired_close(self) -> None:
        now = datetime.now(timezone.utc)
        open_event = UserInteraction(
            id=1,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440000"),
            user_id="user-1",
            post_id=102,
            event_type="OPEN",
            duration_ms=None,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=now,
            processed=False,
        )
        like_event = UserInteraction(
            id=2,
            event_id=UUID("550e8400-e29b-41d4-a716-446655440001"),
            user_id="user-1",
            post_id=100,
            event_type="LIKE",
            duration_ms=None,
            scroll_pct=None,
            max_scroll_pct=None,
            created_at=now,
            processed=False,
        )
        # OPEN event type means it could have a paired CLOSE
        assert open_event.has_paired_close() is True
        # non-OPEN event types never have a paired CLOSE
        assert like_event.has_paired_close() is False
