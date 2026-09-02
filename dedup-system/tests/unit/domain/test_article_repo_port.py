from unittest.mock import MagicMock

import pytest


class TestArticleRepositoryPort:
    @pytest.mark.unit
    def test_is_abstract(self):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        with pytest.raises(TypeError):
            ArticleRepositoryPort()

    @pytest.mark.unit
    def test_has_save_article(self):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        assert hasattr(ArticleRepositoryPort, "save_article")

    @pytest.mark.unit
    def test_has_find_by_hashes(self):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        assert hasattr(ArticleRepositoryPort, "find_by_hashes")

    @pytest.mark.unit
    def test_has_search_neighbors(self):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        assert hasattr(ArticleRepositoryPort, "search_neighbors")

    @pytest.mark.unit
    def test_has_find_by_id(self):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        assert hasattr(ArticleRepositoryPort, "find_by_id")
