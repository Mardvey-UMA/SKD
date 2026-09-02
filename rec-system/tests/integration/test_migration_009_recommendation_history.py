"""Integration tests for migration 009 -- data_flow.recommendation_history table.

Verifies composite PK, index, and clean downgrade behaviour.
"""

from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent
MIGRATIONS_DIR = PROJECT_ROOT / "src/infrastructure/persistence/migrations"
MIGRATION_007_PATH = MIGRATIONS_DIR / "versions/007_data_flow_schema.py"
MIGRATION_008_PATH = MIGRATIONS_DIR / "versions/008_add_categories_table.py"
MIGRATION_009_PATH = MIGRATIONS_DIR / "versions/009_add_recommendation_history.py"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(scope="module")
def pgvector_container_009():
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="module")
def db_url_009(pgvector_container_009):
    url = pgvector_container_009.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


def _load_and_run(path: Path, fn: str, db_url: str) -> None:
    """Load a migration module and run upgrade() or downgrade()."""
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url)
    with engine.connect() as conn:
        ctx = MigrationContext.configure(conn)
        with Operations.context(ctx):
            with conn.begin():
                getattr(module, fn)()
                ver = module.revision
                if fn == "upgrade":
                    conn.execute(
                        text(
                            "CREATE TABLE IF NOT EXISTS alembic_version "
                            "(version_num VARCHAR(32) NOT NULL, "
                            "CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num))"
                        )
                    )
                    conn.execute(
                        text(
                            f"INSERT INTO alembic_version (version_num) VALUES ('{ver}') "
                            "ON CONFLICT DO NOTHING"
                        )
                    )
                else:
                    conn.execute(
                        text(f"DELETE FROM alembic_version WHERE version_num = '{ver}'")
                    )
    engine.dispose()


@pytest.fixture(scope="module")
def db_after_008(db_url_009):
    """Run migrations 007 and 008 once so 009 has prerequisites."""
    for path in [MIGRATION_007_PATH, MIGRATION_008_PATH]:
        _load_and_run(path, "upgrade", db_url_009)
    return db_url_009


def _upgrade_009(db_url: str) -> None:
    _load_and_run(MIGRATION_009_PATH, "upgrade", db_url)


def _downgrade_009(db_url: str) -> None:
    _load_and_run(MIGRATION_009_PATH, "downgrade", db_url)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestMigration009Upgrade:
    """Verify migration 009 creates recommendation_history with correct schema."""

    @pytest.fixture(autouse=True)
    def run_migration(self, db_after_008):
        _upgrade_009(db_after_008)
        yield
        _downgrade_009(db_after_008)

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

    def test_recommendation_history_table_created(self, db_after_008):
        tables = self._get_tables(db_after_008)
        assert "recommendation_history" in tables, (
            "recommendation_history not found in data_flow schema after migration 009"
        )

    def test_recommendation_history_columns_present(self, db_after_008):
        columns = self._get_columns(db_after_008, "recommendation_history")
        for col in ["user_id", "content_id", "recommended_at"]:
            assert col in columns, (
                f"Column '{col}' missing from data_flow.recommendation_history"
            )

    def test_recommendation_history_composite_pk(self, db_after_008):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_008)
        inspector = inspect(engine)
        pk = inspector.get_pk_constraint("recommendation_history", schema="data_flow")
        engine.dispose()
        pk_cols = pk["constrained_columns"]
        assert "user_id" in pk_cols, "user_id missing from composite PK"
        assert "content_id" in pk_cols, "content_id missing from composite PK"

    def test_recommendation_history_index_exists(self, db_after_008):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_008)
        inspector = inspect(engine)
        indexes = {
            idx["name"]
            for idx in inspector.get_indexes("recommendation_history", schema="data_flow")
        }
        engine.dispose()
        assert "ix_rec_history_user_recommended_at" in indexes, (
            "Index ix_rec_history_user_recommended_at missing from recommendation_history"
        )

    def test_recommended_at_has_default(self, db_after_008):
        """recommended_at column should have a server default."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_008)
        inspector = inspect(engine)
        columns = {
            col["name"]: col
            for col in inspector.get_columns("recommendation_history", schema="data_flow")
        }
        engine.dispose()
        rec_at = columns["recommended_at"]
        assert rec_at.get("default") or rec_at.get("server_default"), (
            "recommended_at must have a default (now())"
        )


@pytest.mark.integration
class TestMigration009Downgrade:
    """Verify migration 009 downgrade drops recommendation_history cleanly."""

    def test_downgrade_drops_table(self, db_after_008):
        _upgrade_009(db_after_008)
        _downgrade_009(db_after_008)

        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_008)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names(schema="data_flow"))
        engine.dispose()

        assert "recommendation_history" not in tables, (
            "data_flow.recommendation_history still exists after downgrade 009"
        )
