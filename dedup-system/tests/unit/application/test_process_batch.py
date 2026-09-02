from unittest.mock import MagicMock
from uuid import uuid4

import numpy as np
import pytest


class TestProcessBatchUseCase:
    @pytest.fixture
    def encoder(self):
        from src.domain.interfaces.encoder_port import EncoderPort

        mock = MagicMock(spec=EncoderPort)
        mock.encode_batch.return_value = np.random.randn(1, 1024).astype(np.float32)
        return mock

    @pytest.fixture
    def article_repo(self):
        from src.domain.interfaces.article_repo_port import ArticleRepositoryPort

        mock = MagicMock(spec=ArticleRepositoryPort)
        mock.find_by_hashes.return_value = {}
        mock.save_article.return_value = 100
        mock.search_neighbors.return_value = []
        return mock

    @pytest.fixture
    def similarity_repo(self):
        from src.domain.interfaces.similarity_repo_port import SimilarityRepositoryPort

        return MagicMock(spec=SimilarityRepositoryPort)

    @pytest.fixture
    def raw_content_repo(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        return MagicMock(spec=RawContentRepositoryPort)

    @pytest.fixture
    def config_port(self):
        from src.domain.interfaces.config_port import ConfigPort

        mock = MagicMock(spec=ConfigPort)
        mock.load_thresholds.return_value = (0.85, 0.70)
        mock.load_search_params.return_value = (20, 72)
        mock.load_batch_params.return_value = (64, 32)
        return mock

    @pytest.fixture
    def sut(self, encoder, article_repo, similarity_repo, raw_content_repo, config_port):
        from src.application.use_cases.process_batch import ProcessBatchUseCase

        return ProcessBatchUseCase(
            encoder=encoder,
            article_repo=article_repo,
            similarity_repo=similarity_repo,
            raw_content_repo=raw_content_repo,
            config_port=config_port,
        )

    def _make_rc(self, title="Hello world", content_body="Article body"):
        from src.domain.entities.raw_content import RawContent

        return RawContent(
            id=uuid4(),
            title=title,
            content_body=content_body,
            source_type="REUTERS",
            external_id="ext-1",
            published_at=None,
        )

    @pytest.mark.unit
    def test_process_empty_batch(self, sut):
        from src.application.dto.batch_result_dto import BatchResultDTO

        result = sut.execute([])
        assert isinstance(result, BatchResultDTO)
        assert result.total_processed == 0

    @pytest.mark.unit
    def test_process_new_article(self, sut, encoder, article_repo, raw_content_repo):
        rc = self._make_rc()
        result = sut.execute([rc])
        assert result.total_processed == 1
        assert result.new_articles == 1
        assert result.exact_duplicates == 0
        encoder.encode_batch.assert_called_once()
        article_repo.save_article.assert_called_once()
        raw_content_repo.mark_processed.assert_called_once()

    @pytest.mark.unit
    def test_process_uses_prepend_title_for_encoding(self, sut, encoder, article_repo):
        from src.domain.services.text_window import prepend_title

        rc = self._make_rc(title="My Title", content_body="My Body")
        sut.execute([rc])
        # encode_batch should receive prepend_title(title, content_body) — NOT normalized text
        encoder.encode_batch.assert_called_once()
        texts_passed = encoder.encode_batch.call_args[0][0]
        expected = prepend_title("My Title", "My Body")
        assert texts_passed[0] == expected

    @pytest.mark.unit
    def test_process_uses_source_type(self, sut, article_repo):
        rc = self._make_rc()
        sut.execute([rc])
        saved_article = article_repo.save_article.call_args[0][0]
        assert saved_article.source == rc.source_type

    @pytest.mark.unit
    def test_process_exact_duplicate(
        self, sut, encoder, article_repo, similarity_repo, raw_content_repo
    ):
        def fake_find(hashes):
            return {str(h): 50 for h in hashes}

        article_repo.find_by_hashes.side_effect = fake_find
        rc = self._make_rc()
        result = sut.execute([rc])
        assert result.exact_duplicates >= 1
        encoder.encode_batch.assert_not_called()
        similarity_repo.save_one.assert_called()
        raw_content_repo.mark_processed.assert_called()

    @pytest.mark.unit
    def test_process_with_neighbors(self, sut, article_repo, similarity_repo):
        article_repo.search_neighbors.return_value = [(50, 0.92), (51, 0.75)]
        rc = self._make_rc(title="Some news", content_body="Article text here")
        result = sut.execute([rc])
        assert result.relationships_created >= 1
        similarity_repo.save_batch.assert_called()

    @pytest.mark.unit
    def test_encoding_failure_counts_errors(self, sut, encoder, raw_content_repo):
        encoder.encode_batch.side_effect = RuntimeError("GPU error")
        rc = self._make_rc()
        result = sut.execute([rc])
        assert result.errors >= 1

    @pytest.mark.unit
    def test_encoding_failure_does_not_call_mark_processed_for_failed(
        self, sut, encoder, raw_content_repo
    ):
        encoder.encode_batch.side_effect = RuntimeError("GPU error")
        rc = self._make_rc()
        sut.execute([rc])
        # Row stays is_processed_by_dedup=false — mark_processed NOT called for failed
        raw_content_repo.mark_processed.assert_not_called()

    @pytest.mark.unit
    def test_calls_mark_processed_not_mark_done(self, sut, raw_content_repo):
        rc = self._make_rc()
        sut.execute([rc])
        raw_content_repo.mark_processed.assert_called_once()
        assert not hasattr(raw_content_repo, "mark_done") or not raw_content_repo.mark_done.called

    @pytest.mark.unit
    def test_nan_embedding_skips_post_and_marks_processed(
        self, sut, encoder, article_repo, raw_content_repo
    ):
        nan_embeddings = np.full((1, 1024), np.nan, dtype=np.float32)
        encoder.encode_batch.return_value = nan_embeddings
        rc = self._make_rc()
        result = sut.execute([rc])
        assert result.errors == 1
        raw_content_repo.mark_processed.assert_called_once()
        called_ids = raw_content_repo.mark_processed.call_args[0][0]
        assert rc.id in called_ids
        article_repo.save_article.assert_not_called()

    @pytest.mark.unit
    def test_nan_embedding_does_not_block_other_posts(
        self, sut, encoder, article_repo, raw_content_repo
    ):
        rc1 = self._make_rc(title="Post one", content_body="Body one")
        rc2 = self._make_rc(title="Post two", content_body="Body two")
        rc3 = self._make_rc(title="Post three", content_body="Body three")
        embeddings = np.random.randn(3, 1024).astype(np.float32)
        embeddings[1] = np.nan
        encoder.encode_batch.return_value = embeddings
        result = sut.execute([rc1, rc2, rc3])
        assert result.new_articles == 2
        assert result.errors == 1
        called_ids = raw_content_repo.mark_processed.call_args[0][0]
        assert rc1.id in called_ids
        assert rc2.id in called_ids
        assert rc3.id in called_ids

    @pytest.mark.unit
    def test_self_similarity_skipped(
        self, sut, article_repo, similarity_repo, raw_content_repo
    ):
        rc = self._make_rc()
        article_repo.find_by_hashes.side_effect = lambda hashes: {str(h): 50 for h in hashes}
        article_repo.save_article.return_value = 50  # same as original_id
        result = sut.execute([rc])
        similarity_repo.save_one.assert_not_called()
        called_ids = raw_content_repo.mark_processed.call_args[0][0]
        assert rc.id in called_ids

    @pytest.mark.unit
    def test_exact_dup_error_marks_processed(
        self, sut, article_repo, raw_content_repo
    ):
        rc = self._make_rc()
        article_repo.find_by_hashes.side_effect = lambda hashes: {str(h): 50 for h in hashes}
        article_repo.save_article.side_effect = RuntimeError("DB error")
        result = sut.execute([rc])
        assert result.errors == 1
        raw_content_repo.mark_processed.assert_called_once()
        called_ids = raw_content_repo.mark_processed.call_args[0][0]
        assert rc.id in called_ids
