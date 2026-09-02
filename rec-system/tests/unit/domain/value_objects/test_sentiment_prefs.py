import pytest
from src.domain.value_objects.sentiment_prefs import SentimentPrefs


class TestSentimentPrefs:
    def test_create_with_three_values(self):
        sp = SentimentPrefs({"POSITIVE": 0.5, "NEGATIVE": 0.3, "NEUTRAL": 0.2})
        assert abs(sp.weights["POSITIVE"] + sp.weights["NEGATIVE"] + sp.weights["NEUTRAL"] - 1.0) < 1e-6

    def test_normalization_sums_to_one(self):
        sp = SentimentPrefs({"POSITIVE": 2.0, "NEGATIVE": 1.0, "NEUTRAL": 1.0})
        total = sum(sp.weights.values())
        assert abs(total - 1.0) < 1e-6

    def test_create_uniform(self):
        sp = SentimentPrefs.create_uniform()
        assert abs(sp.weights["POSITIVE"] - 1 / 3) < 1e-6
        assert abs(sp.weights["NEGATIVE"] - 1 / 3) < 1e-6
        assert abs(sp.weights["NEUTRAL"] - 1 / 3) < 1e-6

    def test_apply_update(self):
        sp = SentimentPrefs.create_uniform()
        lr = 0.08
        weight = 0.6
        updated = sp.apply_update(sentiment="POSITIVE", lr=lr, weight=weight)
        # Returns a new instance
        assert updated is not sp
        # POSITIVE should have increased
        assert updated.weights["POSITIVE"] > sp.weights["POSITIVE"]
        # Still normalized
        assert abs(sum(updated.weights.values()) - 1.0) < 1e-6

    def test_clamp_negatives(self):
        sp = SentimentPrefs({"POSITIVE": -1.0, "NEGATIVE": 1.0, "NEUTRAL": 0.5})
        assert sp.weights["POSITIVE"] == 0.0
        assert abs(sum(sp.weights.values()) - 1.0) < 1e-6

    def test_get_preference(self):
        sp = SentimentPrefs({"POSITIVE": 0.6, "NEGATIVE": 0.2, "NEUTRAL": 0.2})
        assert sp.get("POSITIVE") > sp.get("NEGATIVE")
        assert sp.get("NEUTRAL") > 0.0

    def test_to_dict(self):
        sp = SentimentPrefs.create_uniform()
        d = sp.to_dict()
        assert isinstance(d, dict)
        assert set(d.keys()) == {"POSITIVE", "NEGATIVE", "NEUTRAL"}
        assert all(isinstance(v, float) for v in d.values())
