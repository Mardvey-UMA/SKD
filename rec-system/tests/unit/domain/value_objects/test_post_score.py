import pytest
from uuid import UUID, uuid4
from src.domain.value_objects.post_score import PostScore

POST_ID_A = uuid4()
POST_ID_B = uuid4()
POST_ID_C = uuid4()


class TestPostScore:
    def test_create_with_post_id_and_score(self):
        pid = uuid4()
        ps = PostScore(post_id=pid, score=0.647)
        assert ps.post_id == pid
        assert isinstance(ps.post_id, UUID)
        assert ps.score == 0.647

    def test_comparison_by_score_descending(self):
        high = PostScore(post_id=POST_ID_A, score=0.9)
        low = PostScore(post_id=POST_ID_B, score=0.3)
        # Higher score is "greater"
        assert high > low
        assert low < high
        assert high >= high
        assert low <= low

    def test_equality(self):
        ps1 = PostScore(post_id=POST_ID_A, score=0.5)
        ps2 = PostScore(post_id=POST_ID_B, score=0.5)
        # Equal by score
        assert ps1 == ps2
