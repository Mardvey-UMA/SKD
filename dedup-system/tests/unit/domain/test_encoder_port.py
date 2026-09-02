from unittest.mock import MagicMock

import numpy as np
import pytest


class TestEncoderPort:
    @pytest.mark.unit
    def test_encoder_port_is_abstract(self):
        from src.domain.interfaces.encoder_port import EncoderPort

        with pytest.raises(TypeError):
            EncoderPort()

    @pytest.mark.unit
    def test_encoder_port_has_encode_batch(self):
        from src.domain.interfaces.encoder_port import EncoderPort

        assert hasattr(EncoderPort, "encode_batch")

    @pytest.mark.unit
    def test_mock_encoder_works(self):
        from src.domain.interfaces.encoder_port import EncoderPort

        mock = MagicMock(spec=EncoderPort)
        mock.encode_batch.return_value = np.random.randn(3, 1024).astype(np.float32)
        result = mock.encode_batch(["text1", "text2", "text3"])
        assert result.shape == (3, 1024)
