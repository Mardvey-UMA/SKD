import pytest


class TestConfigPort:
    @pytest.mark.unit
    def test_is_abstract(self):
        from src.domain.interfaces.config_port import ConfigPort

        with pytest.raises(TypeError):
            ConfigPort()

    @pytest.mark.unit
    def test_has_load_thresholds(self):
        from src.domain.interfaces.config_port import ConfigPort

        assert hasattr(ConfigPort, "load_thresholds")

    @pytest.mark.unit
    def test_has_load_search_params(self):
        from src.domain.interfaces.config_port import ConfigPort

        assert hasattr(ConfigPort, "load_search_params")

    @pytest.mark.unit
    def test_has_load_batch_params(self):
        from src.domain.interfaces.config_port import ConfigPort

        assert hasattr(ConfigPort, "load_batch_params")
