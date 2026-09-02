from datetime import datetime, timezone
from uuid import UUID, uuid4

import numpy as np
import pytest


class TestArticle:
    @pytest.mark.unit
    def test_create_article_with_embedding(self):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding

        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=1,
            raw_content_id=uuid4(),
            content_hash=ContentHash("a" * 64),
            normalized_text="some normalized text",
            embedding=emb,
            source="reuters",
            created_at=datetime(2026, 4, 1, tzinfo=timezone.utc),
        )
        assert article.id == 1
        assert article.embedding is not None

    @pytest.mark.unit
    def test_create_article_without_embedding(self):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash

        article = Article(
            id=2,
            raw_content_id=uuid4(),
            content_hash=ContentHash("b" * 64),
            normalized_text="exact duplicate text",
            embedding=None,
            source="tass",
            created_at=datetime(2026, 4, 1, tzinfo=timezone.utc),
        )
        assert article.embedding is None

    @pytest.mark.unit
    def test_raw_content_id_is_uuid(self):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash

        uid = uuid4()
        article = Article(
            id=1,
            raw_content_id=uid,
            content_hash=ContentHash("a" * 64),
            normalized_text="text",
            embedding=None,
            source=None,
            created_at=None,
        )
        assert isinstance(article.raw_content_id, UUID)
        assert article.raw_content_id == uid

    @pytest.mark.unit
    def test_has_embedding_property(self):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash
        from src.domain.value_objects.embedding import Embedding

        emb = Embedding(np.random.randn(1024).astype(np.float32))
        article = Article(
            id=1, raw_content_id=uuid4(),
            content_hash=ContentHash("a" * 64),
            normalized_text="text", embedding=emb,
            source=None, created_at=None,
        )
        assert article.has_embedding is True

    @pytest.mark.unit
    def test_has_embedding_false_when_none(self):
        from src.domain.entities.article import Article
        from src.domain.value_objects.content_hash import ContentHash

        article = Article(
            id=2, raw_content_id=uuid4(),
            content_hash=ContentHash("b" * 64),
            normalized_text="text", embedding=None,
            source=None, created_at=None,
        )
        assert article.has_embedding is False
