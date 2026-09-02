from datetime import datetime, timezone

import pytest


class TestSimilarity:
    @pytest.mark.unit
    def test_create_similarity(self):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        sim = Similarity(
            article_a=1,
            article_b=2,
            score=0.92,
            rel_type=RelationType.DUPLICATE,
            created_at=datetime(2026, 4, 1, tzinfo=timezone.utc),
        )
        assert sim.article_a == 1
        assert sim.article_b == 2
        assert sim.score == 0.92

    @pytest.mark.unit
    def test_canonical_ordering_enforced(self):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        sim = Similarity.create(
            article_id_1=5,
            article_id_2=3,
            score=0.88,
            rel_type=RelationType.DUPLICATE,
        )
        assert sim.article_a == 3
        assert sim.article_b == 5

    @pytest.mark.unit
    def test_rejects_same_article(self):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        with pytest.raises(ValueError):
            Similarity.create(
                article_id_1=1,
                article_id_2=1,
                score=1.0,
                rel_type=RelationType.EXACT,
            )

    @pytest.mark.unit
    def test_rejects_invalid_score(self):
        from src.domain.entities.similarity import Similarity
        from src.domain.value_objects.rel_type import RelationType

        with pytest.raises(ValueError):
            Similarity.create(
                article_id_1=1,
                article_id_2=2,
                score=1.5,
                rel_type=RelationType.DUPLICATE,
            )

    @pytest.mark.unit
    def test_create_exact(self):
        from src.domain.entities.similarity import Similarity

        sim = Similarity.create_exact(article_id_1=10, article_id_2=20)
        assert sim.score == 1.0
        assert sim.rel_type.value == "EXACT"
        assert sim.article_a == 10
        assert sim.article_b == 20
