"""
Task 1 — Project Structure Verification Tests (RED phase).

These tests verify that the project scaffold exists:
- pyproject.toml is present and valid TOML
- All src/ subdirectories exist with __init__.py files
- All tests/ subdirectories exist with __init__.py files
- tests/conftest.py contains expected fixture names
"""

import tomllib
from pathlib import Path

import pytest

# Project root is two levels up from this file (tests/test_project_structure.py)
PROJECT_ROOT = Path(__file__).parent.parent


class TestPyprojectToml:
    def test_pyproject_toml_exists(self):
        # Arrange
        toml_path = PROJECT_ROOT / "pyproject.toml"
        # Act / Assert
        assert toml_path.exists(), "pyproject.toml must exist at project root"

    def test_pyproject_toml_is_valid_toml(self):
        # Arrange
        toml_path = PROJECT_ROOT / "pyproject.toml"
        # Act
        with open(toml_path, "rb") as f:
            data = tomllib.load(f)
        # Assert
        assert isinstance(data, dict), "pyproject.toml must parse to a dict"

    def test_pyproject_toml_has_required_production_deps(self):
        # Arrange
        toml_path = PROJECT_ROOT / "pyproject.toml"
        required_deps = [
            "fastapi",
            "uvicorn",
            "sqlalchemy",
            "alembic",
            "pydantic",
            "dependency-injector",
            "asyncpg",
            "pgvector",
            "apscheduler",
        ]
        # Act
        with open(toml_path, "rb") as f:
            data = tomllib.load(f)
        declared_deps = [
            dep.split("[")[0].split(">=")[0].split("==")[0].split("~=")[0].lower().strip()
            for dep in data.get("project", {}).get("dependencies", [])
        ]
        # Assert
        for dep in required_deps:
            assert dep in declared_deps, f"Required production dependency '{dep}' not found in pyproject.toml"

    def test_pyproject_toml_has_required_dev_deps(self):
        # Arrange
        toml_path = PROJECT_ROOT / "pyproject.toml"
        required_dev_deps = [
            "pytest",
            "pytest-asyncio",
            "pytest-cov",
            "testcontainers",
            "httpx",
            "respx",
        ]
        # Act
        with open(toml_path, "rb") as f:
            data = tomllib.load(f)

        # Dev deps may live under [dependency-groups] (uv style) or [project.optional-dependencies.dev]
        dev_group = data.get("dependency-groups", {}).get("dev", [])
        opt_dev = (
            data.get("project", {}).get("optional-dependencies", {}).get("dev", [])
        )
        all_dev_raw = list(dev_group) + list(opt_dev)
        declared_dev = [
            dep.split("[")[0].split(">=")[0].split("==")[0].split("~=")[0].lower().strip()
            for dep in all_dev_raw
        ]
        # Assert
        for dep in required_dev_deps:
            assert dep in declared_dev, f"Required dev dependency '{dep}' not found in pyproject.toml"

    def test_pyproject_toml_has_pytest_markers(self):
        # Arrange
        toml_path = PROJECT_ROOT / "pyproject.toml"
        required_markers = ["unit", "integration", "e2e"]
        # Act
        with open(toml_path, "rb") as f:
            data = tomllib.load(f)
        markers_cfg = (
            data.get("tool", {}).get("pytest", {}).get("ini_options", {}).get("markers", [])
        )
        marker_names = [m.split(":")[0].strip() for m in markers_cfg]
        # Assert
        for marker in required_markers:
            assert marker in marker_names, f"pytest marker '{marker}' not registered in pyproject.toml [tool.pytest.ini_options]"


class TestSrcDirectoryStructure:
    """Verify all src/ layer directories exist with __init__.py files."""

    SRC_PACKAGES = [
        "src",
        "src/domain",
        "src/domain/entities",
        "src/domain/value_objects",
        "src/domain/services",
        "src/domain/interfaces",
        "src/application",
        "src/application/use_cases",
        "src/application/dto",
        "src/application/ports",
        "src/infrastructure",
        "src/infrastructure/persistence",
        "src/infrastructure/nlp",
        "src/infrastructure/http",
        "src/presentation",
        "src/presentation/api",
        "src/presentation/schemas",
    ]

    @pytest.mark.parametrize("package_path", SRC_PACKAGES)
    def test_src_package_has_init_py(self, package_path):
        # Arrange
        init_file = PROJECT_ROOT / package_path / "__init__.py"
        # Assert
        assert init_file.exists(), (
            f"Missing __init__.py at {package_path}/__init__.py — "
            "all src/ packages must be proper Python packages"
        )


class TestTestsDirectoryStructure:
    """Verify all tests/ subdirectories exist with __init__.py files."""

    TESTS_PACKAGES = [
        "tests",
        "tests/unit",
        "tests/integration",
        "tests/e2e",
    ]

    @pytest.mark.parametrize("package_path", TESTS_PACKAGES)
    def test_tests_package_has_init_py(self, package_path):
        # Arrange
        init_file = PROJECT_ROOT / package_path / "__init__.py"
        # Assert
        assert init_file.exists(), (
            f"Missing __init__.py at {package_path}/__init__.py — "
            "all tests/ subdirectories must be proper Python packages"
        )


class TestConftestFixtures:
    """Verify that tests/conftest.py declares the expected shared fixtures."""

    EXPECTED_FIXTURES = [
        "postgres_engine",
        "async_engine",
        "db_session",
    ]

    def test_conftest_exists(self):
        # Arrange
        conftest_path = PROJECT_ROOT / "tests" / "conftest.py"
        # Assert
        assert conftest_path.exists(), "tests/conftest.py must exist"

    @pytest.mark.parametrize("fixture_name", EXPECTED_FIXTURES)
    def test_conftest_contains_fixture(self, fixture_name):
        # Arrange
        conftest_path = PROJECT_ROOT / "tests" / "conftest.py"
        assert conftest_path.exists(), "tests/conftest.py must exist before checking fixtures"
        # Act
        source = conftest_path.read_text()
        # Assert
        assert f"def {fixture_name}" in source, (
            f"Expected fixture '{fixture_name}' not found in tests/conftest.py"
        )
