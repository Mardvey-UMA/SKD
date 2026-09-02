"""Integration tests for migration 007 -- data_flow schema rec tables.

These tests run the Alembic migration against a real PostgreSQL+pgvector
testcontainer and verify all tables, indexes, and seed data are created
correctly. Migration 007 is run independently (not via upgrade head) because
it is a separate root node from the 001-006 rec_db chain.
"""

from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent
ALEMBIC_INI = PROJECT_ROOT / "alembic.ini"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(scope="module")
def pgvector_container_007():
    """Start a dedicated pgvector container for migration 007 tests."""
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="module")
def db_url_007(pgvector_container_007):
    """Return psycopg2-compatible URL for migration 007 tests."""
    url = pgvector_container_007.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


@pytest.fixture(scope="module")
def db_with_schema(db_url_007):
    """Create the data_flow schema before running rec migrations."""
    from sqlalchemy import create_engine, text

    engine = create_engine(db_url_007)
    with engine.connect() as conn:
        conn.execute(text("CREATE SCHEMA IF NOT EXISTS data_flow"))
        conn.commit()
    engine.dispose()
    return db_url_007


MIGRATIONS_DIR = PROJECT_ROOT / "src/infrastructure/persistence/migrations"
MIGRATION_007_PATH = MIGRATIONS_DIR / "versions/007_data_flow_schema.py"


def _run_migration_007_upgrade(db_url: str) -> None:
    """Run upgrade() of migration 007 directly via Alembic MigrationContext.

    Uses MigrationContext directly to avoid multi-head branch issues when
    running 007 in isolation (without the 001-006 rec_db chain).
    """
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    # Load migration module
    spec = importlib.util.spec_from_file_location("migration_007", MIGRATION_007_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url)
    with engine.connect() as conn:
        ctx = MigrationContext.configure(conn)
        with Operations.context(ctx):
            with conn.begin():
                module.upgrade()
                # Record migration in alembic_version
                conn.execute(
                    text(
                        "CREATE TABLE IF NOT EXISTS alembic_version "
                        "(version_num VARCHAR(32) NOT NULL, "
                        "CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num))"
                    )
                )
                conn.execute(
                    text("INSERT INTO alembic_version (version_num) VALUES ('007') "
                         "ON CONFLICT DO NOTHING")
                )
    engine.dispose()


def _run_migration_007_downgrade(db_url: str) -> None:
    """Run downgrade() of migration 007 directly via Alembic MigrationContext."""
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location("migration_007", MIGRATION_007_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url)
    with engine.connect() as conn:
        ctx = MigrationContext.configure(conn)
        with Operations.context(ctx):
            with conn.begin():
                module.downgrade()
                conn.execute(
                    text("DELETE FROM alembic_version WHERE version_num = '007'")
                )
    engine.dispose()


# Aliases for use in fixtures
_upgrade_007 = _run_migration_007_upgrade
_downgrade_007 = _run_migration_007_downgrade


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestMigration007Upgrade:
    """Verify migration 007 upgrade creates all tables and seed data."""

    @pytest.fixture(autouse=True)
    def run_migration(self, db_with_schema):
        """Run upgrade 007 before each test, downgrade after."""
        _upgrade_007(db_with_schema)
        yield
        _downgrade_007(db_with_schema)

    def _get_tables(self, db_url: str) -> set:
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_url)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names(schema="data_flow"))
        engine.dispose()
        return tables

    def _get_columns(self, db_url: str, table: str) -> dict:
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_url)
        inspector = inspect(engine)
        columns = {
            col["name"]: col
            for col in inspector.get_columns(table, schema="data_flow")
        }
        engine.dispose()
        return columns

    def _get_indexes(self, db_url: str, table: str) -> set:
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_url)
        inspector = inspect(engine)
        indexes = {idx["name"] for idx in inspector.get_indexes(table, schema="data_flow")}
        engine.dispose()
        return indexes

    def test_posts_features_table_created(self, db_with_schema):
        """data_flow.posts_features must exist after upgrade 007."""
        tables = self._get_tables(db_with_schema)
        assert "posts_features" in tables, (
            "posts_features not found in data_flow schema after migration 007"
        )

    def test_posts_features_columns(self, db_with_schema):
        """posts_features must have all required NLP feature columns."""
        columns = self._get_columns(db_with_schema, "posts_features")
        expected = [
            "post_id",
            "source_id",
            "source_type",
            "text_length",
            "word_count",
            "reading_time",
            "complexity",
            "is_short_form",
            "is_long_form",
            "topic_1",
            "topic_1_score",
            "topic_2",
            "topic_2_score",
            "topic_3",
            "topic_3_score",
            "sentiment",
            "sentiment_score",
            "entities_persons",
            "entities_organizations",
            "entities_locations",
            "embedding",
            "processed_at",
        ]
        for col in expected:
            assert col in columns, f"Column '{col}' missing from posts_features"

    def test_posts_features_source_id_not_null(self, db_with_schema):
        """posts_features.source_id must be NOT NULL (Phase 1 user-sources)."""
        columns = self._get_columns(db_with_schema, "posts_features")
        assert "source_id" in columns, "source_id column missing from posts_features"
        assert columns["source_id"]["nullable"] is False, (
            "source_id must be NOT NULL"
        )

    def test_posts_features_source_type_not_null(self, db_with_schema):
        """posts_features.source_type must be NOT NULL (Phase 1 user-sources)."""
        columns = self._get_columns(db_with_schema, "posts_features")
        assert "source_type" in columns, "source_type column missing from posts_features"
        assert columns["source_type"]["nullable"] is False, (
            "source_type must be NOT NULL"
        )

    def test_posts_features_source_id_index(self, db_with_schema):
        """posts_features must have idx_posts_features_source_id for source-filtered retrieval."""
        indexes = self._get_indexes(db_with_schema, "posts_features")
        assert "idx_posts_features_source_id" in indexes, (
            "Index idx_posts_features_source_id missing from posts_features"
        )

    def test_posts_features_source_processed_composite_index(self, db_with_schema):
        """posts_features must have composite (source_id, processed_at DESC) index."""
        indexes = self._get_indexes(db_with_schema, "posts_features")
        assert "idx_posts_features_source_processed" in indexes, (
            "Index idx_posts_features_source_processed missing from posts_features"
        )

    def test_posts_features_hnsw_index(self, db_with_schema):
        """posts_features must have HNSW index on embedding column."""
        indexes = self._get_indexes(db_with_schema, "posts_features")
        assert "idx_posts_features_embedding" in indexes, (
            "HNSW index idx_posts_features_embedding missing from posts_features"
        )

    def test_posts_features_processed_index(self, db_with_schema):
        """posts_features must have processed_at index for freshness retrieval."""
        indexes = self._get_indexes(db_with_schema, "posts_features")
        assert "idx_posts_features_processed" in indexes, (
            "Index idx_posts_features_processed missing from posts_features"
        )

    def test_rec_profiles_table_created(self, db_with_schema):
        """data_flow.rec_profiles must exist after upgrade 007."""
        tables = self._get_tables(db_with_schema)
        assert "rec_profiles" in tables, (
            "rec_profiles not found in data_flow schema after migration 007"
        )

    def test_rec_profiles_columns(self, db_with_schema):
        """rec_profiles must have all required user profile columns."""
        columns = self._get_columns(db_with_schema, "rec_profiles")
        expected = [
            "user_id",
            "topic_vector",
            "embedding",
            "sentiment_prefs",
            "format_prefs",
            "interaction_count",
            "created_at",
            "last_updated",
        ]
        for col in expected:
            assert col in columns, f"Column '{col}' missing from rec_profiles"

    def test_rec_entity_interests_table_created(self, db_with_schema):
        """data_flow.rec_entity_interests must exist after upgrade 007."""
        tables = self._get_tables(db_with_schema)
        assert "rec_entity_interests" in tables, (
            "rec_entity_interests not found in data_flow schema after migration 007"
        )

    def test_rec_entity_interests_composite_pk(self, db_with_schema):
        """rec_entity_interests must have composite PK (user_id, entity_type, entity_name)."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema)
        inspector = inspect(engine)
        pk = inspector.get_pk_constraint("rec_entity_interests", schema="data_flow")
        engine.dispose()

        pk_cols = pk["constrained_columns"]
        for col in ["user_id", "entity_type", "entity_name"]:
            assert col in pk_cols, (
                f"Column '{col}' missing from rec_entity_interests composite PK"
            )

    def test_rec_entity_interests_user_index(self, db_with_schema):
        """rec_entity_interests must have user_id index."""
        indexes = self._get_indexes(db_with_schema, "rec_entity_interests")
        assert "idx_rec_entity_interests_user" in indexes, (
            "Index idx_rec_entity_interests_user missing from rec_entity_interests"
        )

    def test_rec_config_table_created(self, db_with_schema):
        """data_flow.rec_config must exist after upgrade 007."""
        tables = self._get_tables(db_with_schema)
        assert "rec_config" in tables, (
            "rec_config not found in data_flow schema after migration 007"
        )

    def test_rec_config_seed_data_six_rows(self, db_with_schema):
        """rec_config must contain exactly 6 seed rows after upgrade 007 (Phase 1 adds narrow_ranking_weights)."""
        from sqlalchemy import create_engine, text

        engine = create_engine(db_with_schema)
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT key FROM data_flow.rec_config ORDER BY key")
            )
            keys = {row[0] for row in result.fetchall()}
        engine.dispose()

        expected_keys = {
            "signal_weights",
            "scoring_weights",
            "profile_params",
            "ranking_params",
            "onboarding_params",
            "narrow_ranking_weights",
        }
        assert keys == expected_keys, (
            f"rec_config seed mismatch. Expected {expected_keys}, got {keys}"
        )

    def test_rec_config_seed_narrow_ranking_weights_shape(self, db_with_schema):
        """rec_config must contain a narrow_ranking_weights row with cosine/freshness/halflife keys."""
        from sqlalchemy import create_engine, text

        engine = create_engine(db_with_schema)
        with engine.connect() as conn:
            result = conn.execute(
                text(
                    "SELECT value FROM data_flow.rec_config WHERE key = 'narrow_ranking_weights'"
                )
            )
            row = result.fetchone()
        engine.dispose()

        assert row is not None, (
            "narrow_ranking_weights seed row missing from rec_config"
        )
        value = row[0]
        assert "cosine" in value, f"narrow_ranking_weights missing 'cosine': {value}"
        assert "freshness" in value, (
            f"narrow_ranking_weights missing 'freshness': {value}"
        )
        assert "halflife_hours" in value, (
            f"narrow_ranking_weights missing 'halflife_hours': {value}"
        )
        assert value["cosine"] == pytest.approx(0.7), (
            f"narrow_ranking_weights.cosine expected 0.7, got {value['cosine']}"
        )
        assert value["freshness"] == pytest.approx(0.3), (
            f"narrow_ranking_weights.freshness expected 0.3, got {value['freshness']}"
        )
        assert value["halflife_hours"] == 48, (
            f"narrow_ranking_weights.halflife_hours expected 48, got {value['halflife_hours']}"
        )

    def test_pgvector_extension_enabled(self, db_with_schema):
        """pgvector extension must be present after migration 007."""
        from sqlalchemy import create_engine, text

        engine = create_engine(db_with_schema)
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT extname FROM pg_extension WHERE extname = 'vector'")
            )
            row = result.fetchone()
        engine.dispose()

        assert row is not None, "pgvector extension not found after migration 007"


@pytest.mark.integration
class TestMigration007Downgrade:
    """Verify migration 007 downgrade drops all rec tables."""

    def test_downgrade_drops_all_tables(self, db_with_schema):
        """All 4 rec tables must be absent in data_flow schema after downgrade 007."""
        _upgrade_007(db_with_schema)
        _downgrade_007(db_with_schema)

        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names(schema="data_flow"))
        engine.dispose()

        for table in ["posts_features", "rec_profiles", "rec_entity_interests", "rec_config"]:
            assert table not in tables, (
                f"Table data_flow.{table} still exists after downgrade 007"
            )
