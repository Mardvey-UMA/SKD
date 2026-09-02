import os

import pytest


VERSIONS_DIR = "src/infrastructure/persistence/migrations/versions"


def _get_migration_003_content() -> str:
    path = os.path.join(VERSIONS_DIR, "003_data_flow_schema.py")
    with open(path) as f:
        return f.read()


class TestMigration003Exists:
    @pytest.mark.unit
    def test_migration_file_exists(self):
        path = os.path.join(VERSIONS_DIR, "003_data_flow_schema.py")
        assert os.path.exists(path), "003_data_flow_schema.py must exist"

    @pytest.mark.unit
    def test_revision_is_003(self):
        content = _get_migration_003_content()
        assert 'revision = "003"' in content

    @pytest.mark.unit
    def test_down_revision_is_002(self):
        content = _get_migration_003_content()
        assert 'down_revision = "002"' in content

    @pytest.mark.unit
    def test_upgrade_creates_articles_in_data_flow(self):
        content = _get_migration_003_content()
        assert "data_flow.articles" in content

    @pytest.mark.unit
    def test_upgrade_creates_similarities_in_data_flow(self):
        content = _get_migration_003_content()
        assert "data_flow.similarities" in content

    @pytest.mark.unit
    def test_upgrade_creates_dedup_config_in_data_flow(self):
        content = _get_migration_003_content()
        assert "data_flow.dedup_config" in content

    @pytest.mark.unit
    def test_table_is_dedup_config_not_config(self):
        content = _get_migration_003_content()
        assert "dedup_config" in content
        # Must NOT create a bare 'config' table
        assert "CREATE TABLE config" not in content
        assert "CREATE TABLE data_flow.config " not in content

    @pytest.mark.unit
    def test_articles_raw_content_id_is_uuid(self):
        content = _get_migration_003_content()
        assert "UUID" in content, "raw_content_id must be UUID type"

    @pytest.mark.unit
    def test_batch_seq_in_data_flow(self):
        content = _get_migration_003_content()
        assert "data_flow.batch_seq" in content

    @pytest.mark.unit
    def test_hnsw_index_on_embedding(self):
        content = _get_migration_003_content()
        assert "hnsw" in content
        assert "vector_cosine_ops" in content

    @pytest.mark.unit
    def test_has_downgrade(self):
        content = _get_migration_003_content()
        assert "def downgrade" in content

    @pytest.mark.unit
    def test_seed_data_in_dedup_config(self):
        content = _get_migration_003_content()
        assert "threshold_duplicate" in content
        assert "threshold_related" in content
        assert "0.85" in content
        assert "0.70" in content
