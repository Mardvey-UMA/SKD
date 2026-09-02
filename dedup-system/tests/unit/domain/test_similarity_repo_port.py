import pytest


class TestSimilarityRepositoryPort:
    @pytest.mark.unit
    def test_is_abstract(self):
        from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort

        with pytest.raises(TypeError):
            SimilarityRepositoryPort()

    @pytest.mark.unit
    def test_has_save_batch(self):
        from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort

        assert hasattr(SimilarityRepositoryPort, "save_batch")

    @pytest.mark.unit
    def test_has_save_one(self):
        from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort

        assert hasattr(SimilarityRepositoryPort, "save_one")
