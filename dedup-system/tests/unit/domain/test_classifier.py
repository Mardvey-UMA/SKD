import pytest


class TestThresholdClassifier:
    @pytest.mark.unit
    def test_classify_duplicate(self):
        from src.domain.services.classifier import ThresholdClassifier
        from src.domain.value_objects.rel_type import RelationType

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        result = clf.classify(0.90)
        assert result == RelationType.DUPLICATE

    @pytest.mark.unit
    def test_classify_related(self):
        from src.domain.services.classifier import ThresholdClassifier
        from src.domain.value_objects.rel_type import RelationType

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        result = clf.classify(0.75)
        assert result == RelationType.RELATED

    @pytest.mark.unit
    def test_classify_below_threshold(self):
        from src.domain.services.classifier import ThresholdClassifier

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        result = clf.classify(0.60)
        assert result is None

    @pytest.mark.unit
    def test_classify_at_duplicate_boundary(self):
        from src.domain.services.classifier import ThresholdClassifier
        from src.domain.value_objects.rel_type import RelationType

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        result = clf.classify(0.85)
        assert result == RelationType.DUPLICATE

    @pytest.mark.unit
    def test_classify_at_related_boundary(self):
        from src.domain.services.classifier import ThresholdClassifier
        from src.domain.value_objects.rel_type import RelationType

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        result = clf.classify(0.70)
        assert result == RelationType.RELATED

    @pytest.mark.unit
    def test_classify_relationships_batch(self):
        from src.domain.services.classifier import ThresholdClassifier

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        new_article_ids = [100]
        scores = [[0.92, 0.78, 0.60]]
        neighbor_ids = [[1, 2, 3]]
        results = clf.classify_relationships(new_article_ids, scores, neighbor_ids)
        assert len(results) == 2
        assert results[0].article_a < results[0].article_b

    @pytest.mark.unit
    def test_classify_skips_self_references(self):
        from src.domain.services.classifier import ThresholdClassifier

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        new_article_ids = [100]
        scores = [[0.99]]
        neighbor_ids = [[100]]
        results = clf.classify_relationships(new_article_ids, scores, neighbor_ids)
        assert len(results) == 0

    @pytest.mark.unit
    def test_update_thresholds(self):
        from src.domain.services.classifier import ThresholdClassifier
        from src.domain.value_objects.rel_type import RelationType

        clf = ThresholdClassifier(th_dup=0.85, th_rel=0.70)
        clf.update_thresholds(th_dup=0.90, th_rel=0.75)
        result = clf.classify(0.87)
        assert result == RelationType.RELATED
