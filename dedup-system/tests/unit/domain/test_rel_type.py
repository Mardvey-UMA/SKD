import pytest


class TestRelationType:
    @pytest.mark.unit
    def test_exact_type_exists(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.EXACT.value == "EXACT"

    @pytest.mark.unit
    def test_duplicate_type_exists(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.DUPLICATE.value == "DUPLICATE"

    @pytest.mark.unit
    def test_related_type_exists(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.RELATED.value == "RELATED"

    @pytest.mark.unit
    def test_from_score_exact(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.from_score(1.0, th_dup=0.85, th_rel=0.70) == RelationType.EXACT

    @pytest.mark.unit
    def test_from_score_duplicate(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.from_score(0.90, th_dup=0.85, th_rel=0.70) == RelationType.DUPLICATE

    @pytest.mark.unit
    def test_from_score_related(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.from_score(0.75, th_dup=0.85, th_rel=0.70) == RelationType.RELATED

    @pytest.mark.unit
    def test_from_score_below_threshold_returns_none(self):
        from src.domain.value_objects.rel_type import RelationType

        assert RelationType.from_score(0.60, th_dup=0.85, th_rel=0.70) is None
