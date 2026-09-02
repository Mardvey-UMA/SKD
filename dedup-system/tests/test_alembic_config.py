import os

import pytest


class TestAlembicConfig:
    @pytest.mark.unit
    def test_alembic_ini_exists(self):
        assert os.path.isfile("alembic.ini")

    @pytest.mark.unit
    def test_alembic_env_py_exists(self):
        assert os.path.isfile("src/infrastructure/persistence/migrations/env.py")

    @pytest.mark.unit
    def test_alembic_versions_dir_exists(self):
        assert os.path.isdir("src/infrastructure/persistence/migrations/versions")
