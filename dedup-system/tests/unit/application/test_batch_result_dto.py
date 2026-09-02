import pytest


class TestBatchResultDTO:
    @pytest.mark.unit
    def test_create_dto(self):
        from src.application.dto.batch_result_dto import BatchResultDTO

        dto = BatchResultDTO(
            total_processed=64,
            exact_duplicates=3,
            new_articles=61,
            relationships_created=120,
            errors=0,
        )
        assert dto.total_processed == 64
        assert dto.new_articles == 61

    @pytest.mark.unit
    def test_empty_batch(self):
        from src.application.dto.batch_result_dto import BatchResultDTO

        dto = BatchResultDTO(
            total_processed=0,
            exact_duplicates=0,
            new_articles=0,
            relationships_created=0,
            errors=0,
        )
        assert dto.total_processed == 0

    @pytest.mark.unit
    def test_dto_is_immutable(self):
        from src.application.dto.batch_result_dto import BatchResultDTO

        dto = BatchResultDTO(
            total_processed=10,
            exact_duplicates=1,
            new_articles=9,
            relationships_created=18,
            errors=0,
        )
        with pytest.raises((AttributeError, TypeError, Exception)):
            dto.total_processed = 20

    @pytest.mark.unit
    def test_summary_string(self):
        from src.application.dto.batch_result_dto import BatchResultDTO

        dto = BatchResultDTO(
            total_processed=64,
            exact_duplicates=3,
            new_articles=61,
            relationships_created=120,
            errors=0,
        )
        summary = dto.summary()
        assert "64" in summary
        assert "3" in summary
        assert "61" in summary
        assert "120" in summary
