import pytest


class TestContentHash:
    @pytest.mark.unit
    def test_create_from_valid_hex(self):
        from src.domain.value_objects.content_hash import ContentHash

        h = ContentHash("a" * 64)
        assert h.value == "a" * 64

    @pytest.mark.unit
    def test_rejects_invalid_length(self):
        from src.domain.value_objects.content_hash import ContentHash

        with pytest.raises(ValueError):
            ContentHash("abc")

    @pytest.mark.unit
    def test_rejects_non_hex(self):
        from src.domain.value_objects.content_hash import ContentHash

        with pytest.raises(ValueError):
            ContentHash("z" * 64)

    @pytest.mark.unit
    def test_equality(self):
        from src.domain.value_objects.content_hash import ContentHash

        h1 = ContentHash("a" * 64)
        h2 = ContentHash("a" * 64)
        assert h1 == h2

    @pytest.mark.unit
    def test_inequality(self):
        from src.domain.value_objects.content_hash import ContentHash

        h1 = ContentHash("a" * 64)
        h2 = ContentHash("b" * 64)
        assert h1 != h2

    @pytest.mark.unit
    def test_hashable(self):
        from src.domain.value_objects.content_hash import ContentHash

        h = ContentHash("a" * 64)
        s = {h}
        assert h in s

    @pytest.mark.unit
    def test_str_representation(self):
        from src.domain.value_objects.content_hash import ContentHash

        h = ContentHash("a" * 64)
        assert str(h) == "a" * 64
