"""Unit tests for HotContentEncoder — RED phase."""
from __future__ import annotations

import pytest
from unittest.mock import AsyncMock, patch
from uuid import uuid4


@pytest.mark.unit
class TestHotContentEncoder:
    """Tests for HotContentEncoder application service."""

    @pytest.fixture
    def content_id(self):
        return uuid4()

    @pytest.fixture
    def process_content_uc_mock(self):
        mock = AsyncMock()
        mock.process_single_by_id = AsyncMock(return_value=True)
        return mock

    @pytest.fixture
    def encoder(self, process_content_uc_mock):
        from src.application.services.hot_content_encoder import HotContentEncoder
        return HotContentEncoder(process_content_use_case=process_content_uc_mock)

    @pytest.mark.asyncio
    async def test_process_delegates_to_use_case(self, encoder, process_content_uc_mock, content_id):
        """process() delegates to process_content_use_case.process_single_by_id."""
        await encoder.process(content_id)
        process_content_uc_mock.process_single_by_id.assert_called_once_with(content_id)

    @pytest.mark.asyncio
    async def test_process_logs_info_on_success(self, process_content_uc_mock, content_id):
        """process() logs INFO with content_id and latency_ms when post found."""
        from src.application.services.hot_content_encoder import HotContentEncoder
        process_content_uc_mock.process_single_by_id.return_value = True
        encoder = HotContentEncoder(process_content_use_case=process_content_uc_mock)
        with patch("src.application.services.hot_content_encoder.logger") as mock_logger:
            await encoder.process(content_id)
        mock_logger.info.assert_called_once()
        log_msg = mock_logger.info.call_args[0][0]
        assert "hot_content_processed" in log_msg

    @pytest.mark.asyncio
    async def test_process_logs_warning_when_not_found(self, process_content_uc_mock, content_id):
        """process() logs WARNING and returns early when post not found in raw_content."""
        from src.application.services.hot_content_encoder import HotContentEncoder
        process_content_uc_mock.process_single_by_id.return_value = False
        encoder = HotContentEncoder(process_content_use_case=process_content_uc_mock)
        with patch("src.application.services.hot_content_encoder.logger") as mock_logger:
            await encoder.process(content_id)
        mock_logger.warning.assert_called_once()
        process_content_uc_mock.process_single_by_id.assert_called_once_with(content_id)

    @pytest.mark.asyncio
    async def test_process_propagates_nlp_exception(self, process_content_uc_mock, content_id):
        """process() lets exceptions from the use case propagate to the caller."""
        from src.application.services.hot_content_encoder import HotContentEncoder
        process_content_uc_mock.process_single_by_id.side_effect = RuntimeError("NLP failed")
        encoder = HotContentEncoder(process_content_use_case=process_content_uc_mock)
        with pytest.raises(RuntimeError, match="NLP failed"):
            await encoder.process(content_id)

    @pytest.mark.asyncio
    async def test_info_log_includes_latency_ms(self, process_content_uc_mock, content_id):
        """The INFO log records latency_ms as a structured extra field."""
        from src.application.services.hot_content_encoder import HotContentEncoder
        process_content_uc_mock.process_single_by_id.return_value = True
        encoder = HotContentEncoder(process_content_use_case=process_content_uc_mock)
        with patch("src.application.services.hot_content_encoder.logger") as mock_logger:
            await encoder.process(content_id)
        # extra kwarg should contain latency_ms
        call_kwargs = mock_logger.info.call_args
        extra = call_kwargs[1].get("extra", {})
        assert "latency_ms" in extra
