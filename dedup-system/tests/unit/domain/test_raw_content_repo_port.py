import pytest


class TestRawContentRepositoryPort:
    @pytest.mark.unit
    def test_is_abstract(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        with pytest.raises(TypeError):
            RawContentRepositoryPort()

    @pytest.mark.unit
    def test_has_fetch_pending_batch(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        assert hasattr(RawContentRepositoryPort, "fetch_pending_batch")

    @pytest.mark.unit
    def test_has_mark_processed(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        assert hasattr(RawContentRepositoryPort, "mark_processed")

    @pytest.mark.unit
    def test_does_not_have_insert_batch(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        assert not hasattr(RawContentRepositoryPort, "insert_batch")

    @pytest.mark.unit
    def test_does_not_have_mark_done(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        assert not hasattr(RawContentRepositoryPort, "mark_done")

    @pytest.mark.unit
    def test_does_not_have_mark_error(self):
        from src.domain.interfaces.raw_content_repo_port import RawContentRepositoryPort

        assert not hasattr(RawContentRepositoryPort, "mark_error")
