import pytest


def pytest_configure(config):
    config.addinivalue_line("markers", "unit: Unit tests (mocked dependencies)")
    config.addinivalue_line("markers", "integration: Integration tests (real DB via testcontainers)")
    config.addinivalue_line("markers", "e2e: End-to-end tests (full pipeline)")
