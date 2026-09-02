"""Integration tests for migration 012 — rec_user_blocked_sources table (Phase 1 user-sources).

Verifies the new per-user source blacklist table, its composite PK, and the user_id index.
"""

from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent
MIGRATIONS_DIR = PROJECT_ROOT / "src/infrastructure/persistence/migrations"
MIGRATION_007_PATH = MIGRATIONS_DIR / "versions/007_data_flow_schema.py"
MIGRATION_012_PATH = MIGRATIONS_DIR / "versions/012_add_rec_user_blocked_sources.py"


@pytest.fixture(scope="module")
def pgvector_container_012():
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="module")
def db_url_012(pgvector_container_012):
    url = pgvector_container_012.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


@pytest.fixture(scope="module")
def db_with_schema_012(db_url_012):
    from sqlalchemy import create_engine, text

    engine = create_engine(db_url_012)
    with engine.connect() as conn:
        conn.execute(text("CREATE SCHEMA IF NOT EXISTS data_flow"))
        conn.commit()
    engine.dispose()
    return db_url_012


def _run_module_upgrade(db_url: str, module_path: Path, version: str) -> None:
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location(f"migration_{version}", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url)
    with engine.connect() as conn:
        ctx = MigrationContext.configure(conn)
        with Operations.context(ctx):
            with conn.begin():
                module.upgrade()
                conn.execute(
                    text(
                        "CREATE TABLE IF NOT EXISTS alembic_version "
                        "(version_num VARCHAR(32) NOT NULL, "
                        "CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num))"
                    )
                )
                conn.execute(
                    text(
                        f"INSERT INTO alembic_version (version_num) VALUES ('{version}') "
                        "ON CONFLICT DO NOTHING"
                    )
                )
    engine.dispose()


def _run_module_downgrade(db_url: str, module_path: Path, version: str) -> None:
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location(f"migration_{version}", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url)
    with engine.connect() as conn:
        ctx = MigrationContext.configure(conn)
        with Operations.context(ctx):
            with conn.begin():
                module.downgrade()
                conn.execute(
                    text(f"DELETE FROM alembic_version WHERE version_num = '{version}'")
                )
    engine.dispose()


@pytest.mark.integration
class TestMigration012Upgrade:
    """Verify migration 012 creates rec_user_blocked_sources with correct schema."""

    @pytest.fixture(autouse=True)
    def run_migration(self, db_with_schema_012):
        # 007 must run first because 012 lives on the same rec_data_flow branch
        _run_module_upgrade(db_with_schema_012, MIGRATION_007_PATH, "007")
        _run_module_upgrade(db_with_schema_012, MIGRATION_012_PATH, "012")
        yield
        _run_module_downgrade(db_with_schema_012, MIGRATION_012_PATH, "012")
        _run_module_downgrade(db_with_schema_012, MIGRATION_007_PATH, "007")

    def test_table_exists(self, db_with_schema_012):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema_012)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names(schema="data_flow"))
        engine.dispose()

        assert "rec_user_blocked_sources" in tables, (
            "rec_user_blocked_sources not found in data_flow schema after migration 012"
        )

    def test_columns(self, db_with_schema_012):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema_012)
        inspector = inspect(engine)
        columns = {
            col["name"]: col
            for col in inspector.get_columns("rec_user_blocked_sources", schema="data_flow")
        }
        engine.dispose()

        for col in ["user_id", "source_id", "blocked_at"]:
            assert col in columns, f"Column '{col}' missing from rec_user_blocked_sources"

        assert columns["user_id"]["nullable"] is False, "user_id must be NOT NULL"
        assert columns["source_id"]["nullable"] is False, "source_id must be NOT NULL"
        assert columns["blocked_at"]["nullable"] is False, "blocked_at must be NOT NULL"

    def test_composite_pk(self, db_with_schema_012):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema_012)
        inspector = inspect(engine)
        pk = inspector.get_pk_constraint(
            "rec_user_blocked_sources", schema="data_flow"
        )
        engine.dispose()

        pk_cols = set(pk["constrained_columns"])
        assert pk_cols == {"user_id", "source_id"}, (
            f"Composite PK mismatch. Expected (user_id, source_id), got {pk_cols}"
        )

    def test_user_index(self, db_with_schema_012):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema_012)
        inspector = inspect(engine)
        indexes = {
            idx["name"]
            for idx in inspector.get_indexes(
                "rec_user_blocked_sources", schema="data_flow"
            )
        }
        engine.dispose()

        assert "idx_blocked_sources_user" in indexes, (
            "idx_blocked_sources_user index missing from rec_user_blocked_sources"
        )

    def test_blocked_at_default_now(self, db_with_schema_012):
        """INSERT without explicit blocked_at must populate it via default now()."""
        import uuid

        from sqlalchemy import create_engine, text

        engine = create_engine(db_with_schema_012)
        user_id = str(uuid.uuid4())
        source_id = str(uuid.uuid4())
        with engine.connect() as conn:
            conn.execute(
                text(
                    "INSERT INTO data_flow.rec_user_blocked_sources (user_id, source_id) "
                    "VALUES (:u, :s)"
                ),
                {"u": user_id, "s": source_id},
            )
            conn.commit()
            result = conn.execute(
                text(
                    "SELECT blocked_at FROM data_flow.rec_user_blocked_sources "
                    "WHERE user_id = :u AND source_id = :s"
                ),
                {"u": user_id, "s": source_id},
            )
            row = result.fetchone()
        engine.dispose()

        assert row is not None, "inserted row not found"
        assert row[0] is not None, "blocked_at must default to now()"


@pytest.mark.integration
class TestMigration012Downgrade:
    """Verify migration 012 downgrade drops rec_user_blocked_sources."""

    def test_downgrade_drops_table(self, db_with_schema_012):
        _run_module_upgrade(db_with_schema_012, MIGRATION_007_PATH, "007")
        _run_module_upgrade(db_with_schema_012, MIGRATION_012_PATH, "012")
        _run_module_downgrade(db_with_schema_012, MIGRATION_012_PATH, "012")

        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_with_schema_012)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names(schema="data_flow"))
        engine.dispose()

        # 007 tables should still exist; only blocked sources should be gone
        assert "rec_user_blocked_sources" not in tables, (
            "rec_user_blocked_sources still exists after downgrade 012"
        )
        assert "posts_features" in tables, (
            "downgrade 012 must not touch posts_features"
        )

        # clean up
        _run_module_downgrade(db_with_schema_012, MIGRATION_007_PATH, "007")
