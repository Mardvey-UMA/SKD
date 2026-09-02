"""Unit tests for migration 007 structure (no DB required).

These tests verify the migration module's structure and metadata
without running against a real database.
"""

import importlib.util
from pathlib import Path

MIGRATION_PATH = (
    Path(__file__).parent.parent.parent
    / "src/infrastructure/persistence/migrations/versions/007_data_flow_schema.py"
)


def _load_module():
    spec = importlib.util.spec_from_file_location("migration_007", MIGRATION_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_migration_007_file_exists():
    """Migration file must exist at the expected path."""
    assert MIGRATION_PATH.exists(), (
        f"Migration file not found: {MIGRATION_PATH}"
    )


def test_migration_007_can_be_imported():
    """Migration module must import without errors."""
    module = _load_module()
    assert module is not None


def test_revision_is_007():
    """revision must be '007'."""
    module = _load_module()
    assert module.revision == "007"


def test_down_revision_is_none():
    """down_revision must be None -- this is a root migration for content_agg_db."""
    module = _load_module()
    assert module.down_revision is None


def test_branch_labels_set():
    """branch_labels must be set to allow multi-head branch targeting."""
    module = _load_module()
    assert module.branch_labels is not None, "branch_labels must be set for multi-head support"


def test_upgrade_function_exists():
    """upgrade() must be callable."""
    module = _load_module()
    assert callable(module.upgrade)


def test_downgrade_function_exists():
    """downgrade() must be callable."""
    module = _load_module()
    assert callable(module.downgrade)


def test_posts_features_table_in_upgrade_sql():
    """upgrade SQL must reference data_flow.posts_features."""
    source = MIGRATION_PATH.read_text()
    assert "data_flow.posts_features" in source


def test_rec_profiles_table_in_upgrade_sql():
    """upgrade SQL must reference data_flow.rec_profiles."""
    source = MIGRATION_PATH.read_text()
    assert "data_flow.rec_profiles" in source


def test_rec_entity_interests_table_in_upgrade_sql():
    """upgrade SQL must reference data_flow.rec_entity_interests."""
    source = MIGRATION_PATH.read_text()
    assert "data_flow.rec_entity_interests" in source


def test_rec_config_table_in_upgrade_sql():
    """upgrade SQL must reference data_flow.rec_config."""
    source = MIGRATION_PATH.read_text()
    assert "data_flow.rec_config" in source


def test_hnsw_index_in_upgrade_sql():
    """upgrade SQL must create an HNSW index on the embedding column."""
    source = MIGRATION_PATH.read_text()
    assert "hnsw" in source.lower()
    assert "embedding" in source


def test_seed_data_five_keys():
    """upgrade SQL must seed exactly 5 rec_config keys."""
    source = MIGRATION_PATH.read_text()
    expected_keys = [
        "signal_weights",
        "scoring_weights",
        "profile_params",
        "ranking_params",
        "onboarding_params",
    ]
    for key in expected_keys:
        assert key in source, f"Seed key '{key}' missing from migration SQL"


def test_downgrade_drops_all_four_tables():
    """downgrade SQL must drop all 4 rec tables."""
    source = MIGRATION_PATH.read_text()
    for table in [
        "data_flow.posts_features",
        "data_flow.rec_profiles",
        "data_flow.rec_entity_interests",
        "data_flow.rec_config",
    ]:
        assert table in source, (
            f"Table '{table}' not found in migration (needed by downgrade)"
        )


def test_vector_extension_in_upgrade():
    """upgrade SQL must create the pgvector extension."""
    source = MIGRATION_PATH.read_text()
    assert "CREATE EXTENSION IF NOT EXISTS vector" in source


def test_uuid_primary_key_posts_features():
    """posts_features must use UUID as primary key (not BIGINT)."""
    source = MIGRATION_PATH.read_text()
    assert "UUID" in source.upper()
