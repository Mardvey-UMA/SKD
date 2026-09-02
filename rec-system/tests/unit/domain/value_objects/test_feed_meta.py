import pytest
from src.domain.value_objects.feed_meta import FeedMeta


class TestFeedMeta:
    def test_create_with_all_fields(self):
        fm = FeedMeta(
            total=200,
            candidates_scored=487,
            profile_events_processed=12,
            generation_time_ms=280,
        )
        assert fm.total == 200
        assert fm.candidates_scored == 487
        assert fm.profile_events_processed == 12
        assert fm.generation_time_ms == 280

    def test_to_dict(self):
        fm = FeedMeta(
            total=200,
            candidates_scored=487,
            profile_events_processed=12,
            generation_time_ms=280,
        )
        d = fm.to_dict()
        assert d["total"] == 200
        assert d["candidates_scored"] == 487
        assert d["profile_events_processed"] == 12
        assert d["generation_time_ms"] == 280
