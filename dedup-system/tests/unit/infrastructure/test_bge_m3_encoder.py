from unittest.mock import MagicMock, patch

import numpy as np
import pytest


class TestBgeM3Encoder:
    @pytest.mark.unit
    def test_implements_port(self):
        from src.domain.interfaces.encoder_port import EncoderPort
        from src.infrastructure.nlp.bge_m3_encoder import BgeM3Encoder

        assert issubclass(BgeM3Encoder, EncoderPort)

    @pytest.mark.unit
    @patch("src.infrastructure.nlp.bge_m3_encoder.SentenceTransformer")
    @patch("src.infrastructure.nlp.bge_m3_encoder.torch")
    def test_encode_batch_returns_correct_shape(self, mock_torch, mock_st_class):
        from src.infrastructure.nlp.bge_m3_encoder import BgeM3Encoder

        mock_torch.cuda.is_available.return_value = False
        mock_model = MagicMock()
        mock_st_class.return_value = mock_model
        mock_model.encode.return_value = np.random.randn(3, 1024).astype(np.float32)

        encoder = BgeM3Encoder(model_name="BAAI/bge-m3", device="cpu", use_fp16=False)
        result = encoder.encode_batch(["text1", "text2", "text3"])
        assert result.shape == (3, 1024)

    @pytest.mark.unit
    @patch("src.infrastructure.nlp.bge_m3_encoder.SentenceTransformer")
    @patch("src.infrastructure.nlp.bge_m3_encoder.torch")
    def test_encode_calls_model_with_correct_params(self, mock_torch, mock_st_class):
        from src.infrastructure.nlp.bge_m3_encoder import BgeM3Encoder

        mock_torch.cuda.is_available.return_value = False
        mock_model = MagicMock()
        mock_st_class.return_value = mock_model
        mock_model.encode.return_value = np.random.randn(2, 1024).astype(np.float32)

        encoder = BgeM3Encoder(model_name="BAAI/bge-m3", device="cpu", use_fp16=False)
        encoder.encode_batch(["a", "b"], batch_size=16)
        mock_model.encode.assert_called_once()
        call_kwargs = mock_model.encode.call_args
        assert call_kwargs[1]["batch_size"] == 16
        assert call_kwargs[1]["normalize_embeddings"] is True
        assert call_kwargs[1]["convert_to_numpy"] is True

    @pytest.mark.unit
    @patch("src.infrastructure.nlp.bge_m3_encoder.SentenceTransformer")
    @patch("src.infrastructure.nlp.bge_m3_encoder.torch")
    def test_fp16_on_cuda(self, mock_torch, mock_st_class):
        from src.infrastructure.nlp.bge_m3_encoder import BgeM3Encoder

        mock_torch.cuda.is_available.return_value = True
        mock_model = MagicMock()
        mock_st_class.return_value = mock_model

        BgeM3Encoder(model_name="BAAI/bge-m3", device="cuda", use_fp16=True)
        mock_model.half.assert_called_once()

    @pytest.mark.unit
    @patch("src.infrastructure.nlp.bge_m3_encoder.SentenceTransformer")
    @patch("src.infrastructure.nlp.bge_m3_encoder.torch")
    def test_no_fp16_on_cpu(self, mock_torch, mock_st_class):
        from src.infrastructure.nlp.bge_m3_encoder import BgeM3Encoder

        mock_torch.cuda.is_available.return_value = False
        mock_model = MagicMock()
        mock_st_class.return_value = mock_model

        BgeM3Encoder(model_name="BAAI/bge-m3", device="cpu", use_fp16=True)
        mock_model.half.assert_not_called()
