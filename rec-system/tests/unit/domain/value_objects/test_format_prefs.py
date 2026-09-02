import pytest
from src.domain.value_objects.format_prefs import FormatPrefs


class TestFormatPrefs:
    def test_create_with_defaults(self):
        fp = FormatPrefs.create_default()
        assert fp.avg_length == 200.0
        assert fp.avg_complexity == 0.5

    def test_create_with_values(self):
        fp = FormatPrefs(avg_length=350.0, avg_complexity=0.7)
        assert fp.avg_length == 350.0
        assert fp.avg_complexity == 0.7

    def test_apply_ema_update(self):
        fp = FormatPrefs(avg_length=200.0, avg_complexity=0.5)
        lr = 0.08
        updated = fp.apply_ema_update(lr=lr, post_length=500.0, post_complexity=0.9)
        # Returns new instance (immutable)
        assert updated is not fp
        # EMA: new = (1-lr)*old + lr*new_value
        expected_length = (1 - lr) * 200.0 + lr * 500.0
        expected_complexity = (1 - lr) * 0.5 + lr * 0.9
        assert abs(updated.avg_length - expected_length) < 1e-9
        assert abs(updated.avg_complexity - expected_complexity) < 1e-9

    def test_to_dict(self):
        fp = FormatPrefs(avg_length=300.0, avg_complexity=0.6)
        d = fp.to_dict()
        assert isinstance(d, dict)
        assert d["avg_length"] == 300.0
        assert d["avg_complexity"] == 0.6

    def test_from_dict(self):
        d = {"avg_length": 300.0, "avg_complexity": 0.6}
        fp = FormatPrefs.from_dict(d)
        assert fp.avg_length == 300.0
        assert fp.avg_complexity == 0.6
