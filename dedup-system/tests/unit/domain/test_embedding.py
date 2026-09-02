import numpy as np
import pytest


class TestEmbedding:
    @pytest.mark.unit
    def test_create_from_numpy_array(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.random.randn(1024).astype(np.float32)
        emb = Embedding(arr)
        assert emb.dimension == 1024

    @pytest.mark.unit
    def test_rejects_wrong_dimension(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.random.randn(512).astype(np.float32)
        with pytest.raises(ValueError):
            Embedding(arr)

    @pytest.mark.unit
    def test_to_list(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.ones(1024, dtype=np.float32)
        emb = Embedding(arr)
        result = emb.to_list()
        assert isinstance(result, list)
        assert len(result) == 1024

    @pytest.mark.unit
    def test_vector_property(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.random.randn(1024).astype(np.float32)
        emb = Embedding(arr)
        assert isinstance(emb.vector, np.ndarray)
        assert emb.vector.shape == (1024,)

    @pytest.mark.unit
    def test_custom_dimension(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.random.randn(768).astype(np.float32)
        emb = Embedding(arr, expected_dim=768)
        assert emb.dimension == 768

    @pytest.mark.unit
    def test_embedding_rejects_nan(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.full(1024, np.nan, dtype=np.float32)
        with pytest.raises(ValueError, match="NaN|Inf"):
            Embedding(arr)

    @pytest.mark.unit
    def test_embedding_rejects_inf(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.full(1024, np.inf, dtype=np.float32)
        with pytest.raises(ValueError):
            Embedding(arr)

    @pytest.mark.unit
    def test_embedding_rejects_negative_inf(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.full(1024, -np.inf, dtype=np.float32)
        with pytest.raises(ValueError):
            Embedding(arr)

    @pytest.mark.unit
    def test_embedding_rejects_mixed_nan(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.random.randn(1024).astype(np.float32)
        arr[500] = np.nan
        with pytest.raises(ValueError):
            Embedding(arr)

    @pytest.mark.unit
    def test_embedding_accepts_valid_zeros(self):
        from src.domain.value_objects.embedding import Embedding

        emb = Embedding(np.zeros(1024, dtype=np.float32))
        assert emb.dimension == 1024

    @pytest.mark.unit
    def test_embedding_accepts_valid_normal(self):
        from src.domain.value_objects.embedding import Embedding

        arr = np.random.randn(1024).astype(np.float32)
        emb = Embedding(arr)
        assert emb.dimension == 1024
