import pytest
from uuid import UUID, uuid4
from src.domain.value_objects.event_type import EventType
from src.domain.value_objects.signal import Signal


class TestEventType:
    def test_all_event_types_defined(self):
        assert EventType.IMPRESSION is not None
        assert EventType.OPEN is not None
        assert EventType.CLOSE is not None
        assert EventType.LIKE is not None
        assert EventType.DISLIKE is not None
        assert EventType.BOOKMARK is not None
        assert len(EventType) == 6


class TestSignal:
    def test_create_positive_signal(self):
        pid = uuid4()
        signal = Signal(post_id=pid, user_id="user-1", weight=0.6, event_type=EventType.LIKE)
        assert signal.post_id == pid
        assert isinstance(signal.post_id, UUID)
        assert signal.user_id == "user-1"
        assert signal.weight == 0.6
        assert signal.event_type == EventType.LIKE

    def test_create_negative_signal(self):
        signal = Signal(post_id=uuid4(), user_id="user-2", weight=-0.7, event_type=EventType.DISLIKE)
        assert signal.weight == -0.7
        assert signal.event_type == EventType.DISLIKE

    def test_create_zero_signal(self):
        signal = Signal(post_id=uuid4(), user_id="user-3", weight=0.0, event_type=EventType.OPEN)
        assert signal.weight == 0.0

    def test_is_positive(self):
        positive_signal = Signal(post_id=uuid4(), user_id="u", weight=0.15, event_type=EventType.IMPRESSION)
        negative_signal = Signal(post_id=uuid4(), user_id="u", weight=-0.05, event_type=EventType.IMPRESSION)
        zero_signal = Signal(post_id=uuid4(), user_id="u", weight=0.0, event_type=EventType.OPEN)
        assert positive_signal.is_positive is True
        assert negative_signal.is_positive is False
        assert zero_signal.is_positive is False

    def test_is_strong_enough_for_entities(self):
        # From design/04-user-profile.md section 3.5: threshold weight >= 0.4
        strong_signal = Signal(post_id=uuid4(), user_id="u", weight=0.5, event_type=EventType.CLOSE)
        threshold_signal = Signal(post_id=uuid4(), user_id="u", weight=0.4, event_type=EventType.CLOSE)
        weak_signal = Signal(post_id=uuid4(), user_id="u", weight=0.15, event_type=EventType.IMPRESSION)
        assert strong_signal.is_strong_enough_for_entities(threshold=0.4) is True
        assert threshold_signal.is_strong_enough_for_entities(threshold=0.4) is True
        assert weak_signal.is_strong_enough_for_entities(threshold=0.4) is False
