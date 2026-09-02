from unittest.mock import MagicMock
from uuid import uuid4

import pytest


class TestWorkerRunner:
    @pytest.fixture
    def raw_content_repo(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        return MagicMock(spec=RawContentRepositoryPort)

    @pytest.fixture
    def process_batch_use_case(self):
        from src.application.use_cases.process_batch import ProcessBatchUseCase

        return MagicMock(spec=ProcessBatchUseCase)

    @pytest.fixture
    def config_port(self):
        from src.domain.interfaces.config_port import ConfigPort

        mock = MagicMock(spec=ConfigPort)
        mock.load_batch_params.return_value = (64, 32)
        return mock

    @pytest.fixture
    def sut(self, raw_content_repo, process_batch_use_case, config_port):
        from src.presentation.worker_runner import WorkerRunner

        return WorkerRunner(
            raw_content_repo=raw_content_repo,
            process_batch_use_case=process_batch_use_case,
            config_port=config_port,
            poll_interval=0.2,
        )

    @pytest.mark.unit
    def test_process_one_cycle_with_batch(
        self, sut, raw_content_repo, process_batch_use_case
    ):
        from src.application.dto.batch_result_dto import BatchResultDTO
        from src.domain.entities.raw_content import RawContent

        rc = RawContent(
            id=uuid4(),
            title="Some title",
            content_body="Some body",
            source_type="HABR",
            external_id="ext-1",
            published_at=None,
        )
        raw_content_repo.fetch_pending_batch.return_value = [rc]
        process_batch_use_case.execute.return_value = BatchResultDTO(
            total_processed=1, exact_duplicates=0, new_articles=1,
            relationships_created=2, errors=0,
        )

        had_work = sut.process_one_cycle()

        assert had_work is True
        process_batch_use_case.execute.assert_called_once_with([rc])

    @pytest.mark.unit
    def test_process_one_cycle_no_batch(
        self, sut, raw_content_repo, process_batch_use_case
    ):
        raw_content_repo.fetch_pending_batch.return_value = []

        had_work = sut.process_one_cycle()

        assert had_work is False
        process_batch_use_case.execute.assert_not_called()

    @pytest.mark.unit
    def test_uses_batch_size_from_config(self, sut, raw_content_repo, config_port):
        raw_content_repo.fetch_pending_batch.return_value = []
        sut.process_one_cycle()
        raw_content_repo.fetch_pending_batch.assert_called_once_with(batch_size=64)
