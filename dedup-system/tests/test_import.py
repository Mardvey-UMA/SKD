import pytest


class TestProjectStructure:
    @pytest.mark.unit
    def test_src_package_importable(self):
        import src

    @pytest.mark.unit
    def test_domain_layer_importable(self):
        import src.domain
        import src.domain.entities
        import src.domain.value_objects
        import src.domain.services
        import src.domain.interfaces

    @pytest.mark.unit
    def test_application_layer_importable(self):
        import src.application
        import src.application.use_cases
        import src.application.dto

    @pytest.mark.unit
    def test_infrastructure_layer_importable(self):
        import src.infrastructure
        import src.infrastructure.persistence
        import src.infrastructure.nlp
        import src.infrastructure.messaging

    @pytest.mark.unit
    def test_presentation_layer_importable(self):
        import src.presentation
