import os

import pytest


class TestMigration002Exists:
    @pytest.mark.unit
    def test_second_migration_exists(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = [f for f in os.listdir(versions_dir) if f.endswith(".py") and not f.startswith("__")]
        assert len(files) >= 2, "At least two migration files should exist"

    @pytest.mark.unit
    def test_migration_contains_similarities(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = sorted(
            [f for f in os.listdir(versions_dir) if f.endswith(".py") and not f.startswith("__")]
        )
        content = open(os.path.join(versions_dir, files[1])).read()
        assert "similarities" in content
        assert "config" in content

    @pytest.mark.unit
    def test_migration_has_seed_data(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = sorted(
            [f for f in os.listdir(versions_dir) if f.endswith(".py") and not f.startswith("__")]
        )
        content = open(os.path.join(versions_dir, files[1])).read()
        assert "threshold_duplicate" in content
        assert "threshold_related" in content
        assert "0.85" in content
        assert "0.70" in content

    @pytest.mark.unit
    def test_migration_has_downgrade(self):
        versions_dir = "src/infrastructure/persistence/migrations/versions"
        files = sorted(
            [f for f in os.listdir(versions_dir) if f.endswith(".py") and not f.startswith("__")]
        )
        content = open(os.path.join(versions_dir, files[1])).read()
        assert "def downgrade" in content
