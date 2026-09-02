import os

import pytest


class TestMigration001Exists:
    @pytest.mark.unit
    def test_migration_file_exists(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = os.listdir(versions_dir)
        migration_files = [f for f in files if f.endswith(".py") and not f.startswith("__")]
        assert len(migration_files) >= 1, "At least one migration file should exist"

    @pytest.mark.unit
    def test_migration_contains_raw_content(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = sorted(
            [f for f in os.listdir(versions_dir) if f.endswith(".py") and not f.startswith("__")]
        )
        content = open(os.path.join(versions_dir, files[0])).read()
        assert "raw_content" in content
        assert "articles" in content
        assert "vector" in content

    @pytest.mark.unit
    def test_migration_has_downgrade(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = sorted(
            [f for f in os.listdir(versions_dir) if f.endswith(".py") and not f.startswith("__")]
        )
        content = open(os.path.join(versions_dir, files[0])).read()
        assert "def downgrade" in content
