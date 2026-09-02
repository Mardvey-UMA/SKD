"""Integration tests for migration 010 -- cold_start column on rec_profiles.

Verifies that the column is added with correct type and default, and that
downgrade removes it cleanly.
"""

from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent
MIGRATIONS_DIR = PROJECT_ROOT / "src/infrastructure/persistence/migrations"
MIGRATION_007_PATH = MIGRATIONS_DIR / "versions/007_data_flow_schema.py"
MIGRATION_008_PATH = MIGRATIONS_DIR / "versions/008_add_categories_table.py"
MIGRATION_009_PATH = MIGRATIONS_DIR / "versions/009_add_recommendation_history.py"
MIGRATION_010_PATH = MIGRATIONS_DIR / "versions/010_add_cold_start_column.py"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(scope="module")
def pgvector_container_010():
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="module")
def db_url_010(pgvector_container_010):
    url = pgvector_container_010.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


def _load_and_run(path: Path, fn: str, db_url: str) -> None:
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
def db_after_009(db_url_010):
    """Run migrations 007-009 once so migration 010 has prerequisites."""
    for path in [
        MIGRATION_007_PATH,
        MIGRATION_008_PATH,
        MIGRATION_009_PATH,
    ]:
        _load_and_run(path, "upgrade", db_url_010)
    return db_url_010


def _upgrade_010(db_url: str) -> None:
    _load_and_run(MIGRATION_010_PATH, "upgrade", db_url)


def _downgrade_010(db_url: str) -> None:
    _load_and_run(MIGRATION_010_PATH, "downgrade", db_url)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestMigration010Upgrade:
    """Verify migration 010 adds cold_start column to rec_profiles."""

    @pytest.fixture(autouse=True)
    def run_migration(self, db_after_009):
        _upgrade_010(db_after_009)
        yield
        _downgrade_010(db_after_009)

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

    def test_cold_start_column_exists(self, db_after_009):
        columns = self._get_columns(db_after_009, "rec_profiles")
        assert "cold_start" in columns, (
            "cold_start column missing from data_flow.rec_profiles after migration 010"
        )

    def test_cold_start_is_boolean(self, db_after_009):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_009)
        with engine.connect() as conn:
            result = conn.execute(
                text(
                    "SELECT data_type FROM information_schema.columns "
                    "WHERE table_schema = 'data_flow' "
                    "AND table_name = 'rec_profiles' "
                    "AND column_name = 'cold_start'"
                )
            )
            row = result.fetchone()
        engine.dispose()
        assert row is not None, "cold_start column not found in information_schema"
        assert row[0] == "boolean", f"Expected boolean, got {row[0]}"

    def test_cold_start_not_nullable(self, db_after_009):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_009)
        with engine.connect() as conn:
            result = conn.execute(
                text(
                    "SELECT is_nullable FROM information_schema.columns "
                    "WHERE table_schema = 'data_flow' "
                    "AND table_name = 'rec_profiles' "
                    "AND column_name = 'cold_start'"
                )
            )
            row = result.fetchone()
        engine.dispose()
        assert row is not None
        assert row[0] == "NO", "cold_start must be NOT NULL"

    def test_cold_start_default_is_true(self, db_after_009):
        """Existing rows inserted without cold_start should get true via default."""
        from sqlalchemy import create_engine, text
        import uuid

        engine = create_engine(db_after_009)
        with engine.connect() as conn:
            uid = str(uuid.uuid4())
            conn.execute(
                text(
                    "INSERT INTO data_flow.rec_profiles "
                    "(user_id, topic_vector, sentiment_prefs, format_prefs) "
                    "VALUES (:uid, '{}', '{}', '{}')"
                ),
                {"uid": uid},
            )
            result = conn.execute(
                text(
                    "SELECT cold_start FROM data_flow.rec_profiles WHERE user_id = :uid"
                ),
                {"uid": uid},
            )
            row = result.fetchone()
            conn.execute(
                text("DELETE FROM data_flow.rec_profiles WHERE user_id = :uid"),
                {"uid": uid},
            )
            conn.commit()
        engine.dispose()
        assert row is not None
        assert row[0] is True, f"cold_start default should be true, got {row[0]}"


@pytest.mark.integration
class TestMigration010Downgrade:
    """Verify migration 010 downgrade removes cold_start column."""

    def test_downgrade_removes_cold_start_column(self, db_after_009):
        _upgrade_010(db_after_009)
        _downgrade_010(db_after_009)

        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_009)
        inspector = inspect(engine)
        columns = {
            col["name"]
            for col in inspector.get_columns("rec_profiles", schema="data_flow")
        }
        engine.dispose()

        assert "cold_start" not in columns, (
            "cold_start column still exists in rec_profiles after downgrade 010"
        )
