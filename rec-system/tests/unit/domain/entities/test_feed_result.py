import pytest
from uuid import UUID, uuid4

from src.domain.entities.feed_result import FeedResult
from src.domain.value_objects.post_score import PostScore
from src.domain.value_objects.feed_meta import FeedMeta

# Fixed UUIDs for deterministic assertions
PID_1 = uuid4()
PID_2 = uuid4()
PID_3 = uuid4()


class TestFeedResult:
    @pytest.fixture
    def meta(self) -> FeedMeta:
        return FeedMeta(
            total=3,
            candidates_scored=100,
            profile_events_processed=5,
            generation_time_ms=42,
        )

    @pytest.fixture
    def scores(self) -> list:
        return [
            PostScore(post_id=PID_1, score=0.9),
            PostScore(post_id=PID_2, score=0.8),
            PostScore(post_id=PID_3, score=0.7),
        ]

    def test_create_with_scores_and_meta(
        self, scores: list, meta: FeedMeta
    ) -> None:
        result = FeedResult(scores=scores, meta=meta)
        assert result.scores == scores
        assert result.meta is meta

    def test_post_ids(self, scores: list, meta: FeedMeta) -> None:
        result = FeedResult(scores=scores, meta=meta)
        post_ids = result.post_ids
        assert isinstance(post_ids[0], UUID)
        assert post_ids == [PID_1, PID_2, PID_3]

    def test_total(self, scores: list, meta: FeedMeta) -> None:
        result = FeedResult(scores=scores, meta=meta)
        assert result.total == 3

    def test_empty_feed(self) -> None:
        meta = FeedMeta(
            total=0,
            candidates_scored=0,
            profile_events_processed=0,
            generation_time_ms=10,
        )
        result = FeedResult(scores=[], meta=meta)
        assert result.scores == []
        assert result.post_ids == []
        assert result.total == 0
