"""Integration tests for migration 008 -- data_flow.categories table.

Runs the migration against a real PostgreSQL+pgvector testcontainer and verifies
the table schema, seed data, and downgrade behaviour.
"""

from pathlib import Path

import pytest

PROJECT_ROOT = Path(__file__).parent.parent.parent
MIGRATIONS_DIR = PROJECT_ROOT / "src/infrastructure/persistence/migrations"
MIGRATION_007_PATH = MIGRATIONS_DIR / "versions/007_data_flow_schema.py"
MIGRATION_008_PATH = MIGRATIONS_DIR / "versions/008_add_categories_table.py"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(scope="module")
def pgvector_container_008():
    from testcontainers.postgres import PostgresContainer

    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="module")
def db_url_008(pgvector_container_008):
    url = pgvector_container_008.get_connection_url()
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


@pytest.fixture(scope="module")
def db_after_007(db_url_008):
    """Run migration 007 once, so 008 has its prerequisite tables in place."""
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location("migration_007", MIGRATION_007_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url_008)
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
                        "INSERT INTO alembic_version (version_num) VALUES ('007') "
                        "ON CONFLICT DO NOTHING"
                    )
                )
    engine.dispose()
    return db_url_008


def _run_migration_008_upgrade(db_url: str) -> None:
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location("migration_008", MIGRATION_008_PATH)
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
                        "INSERT INTO alembic_version (version_num) VALUES ('008') "
                        "ON CONFLICT DO NOTHING"
                    )
                )
    engine.dispose()


def _run_migration_008_downgrade(db_url: str) -> None:
    import importlib.util

    from alembic.operations import Operations
    from alembic.runtime.migration import MigrationContext
    from sqlalchemy import create_engine, text

    spec = importlib.util.spec_from_file_location("migration_008", MIGRATION_008_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    engine = create_engine(db_url)
    with engine.connect() as conn:
        ctx = MigrationContext.configure(conn)
        with Operations.context(ctx):
            with conn.begin():
                module.downgrade()
                conn.execute(
                    text("DELETE FROM alembic_version WHERE version_num = '008'")
                )
    engine.dispose()


EXPECTED_CATEGORY_IDS = [
    "политика",
    "экономика",
    "технологии",
    "наука",
    "спорт",
    "культура",
    "общество",
    "происшествия",
    "международные новости",
    "бизнес",
    "финансы",
    "образование",
    "здоровье",
    "развлечения",
    "криминал",
    "армия",
    "природа",
    "транспорт",
]


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestMigration008Upgrade:
    """Verify migration 008 creates the categories table with correct schema and seed data."""

    @pytest.fixture(autouse=True)
    def run_migration(self, db_after_007):
        _run_migration_008_upgrade(db_after_007)
        yield
        _run_migration_008_downgrade(db_after_007)

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

    def test_categories_table_created(self, db_after_007):
        tables = self._get_tables(db_after_007)
        assert "categories" in tables, (
            "categories table not found in data_flow schema after migration 008"
        )

    def test_categories_columns_present(self, db_after_007):
        columns = self._get_columns(db_after_007, "categories")
        for col in ["id", "name", "icon", "sort_order", "is_active", "created_at"]:
            assert col in columns, f"Column '{col}' missing from data_flow.categories"

    def test_categories_id_is_primary_key(self, db_after_007):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_007)
        inspector = inspect(engine)
        pk = inspector.get_pk_constraint("categories", schema="data_flow")
        engine.dispose()
        assert "id" in pk["constrained_columns"]

    def test_categories_seed_exactly_18_rows(self, db_after_007):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_007)
        with engine.connect() as conn:
            result = conn.execute(text("SELECT COUNT(*) FROM data_flow.categories"))
            count = result.scalar()
        engine.dispose()
        assert count == 18, f"Expected 18 categories, got {count}"

    def test_categories_all_expected_ids_present(self, db_after_007):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_007)
        with engine.connect() as conn:
            result = conn.execute(text("SELECT id FROM data_flow.categories"))
            ids = {row[0] for row in result.fetchall()}
        engine.dispose()
        for cat_id in EXPECTED_CATEGORY_IDS:
            assert cat_id in ids, f"Category ID '{cat_id}' missing from seed data"

    def test_categories_sort_order_sequential(self, db_after_007):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_007)
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT sort_order FROM data_flow.categories ORDER BY sort_order")
            )
            orders = [row[0] for row in result.fetchall()]
        engine.dispose()
        assert orders == list(range(1, 19)), (
            f"sort_order values are not sequential 1-18: {orders}"
        )

    def test_categories_all_active_by_default(self, db_after_007):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_007)
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT COUNT(*) FROM data_flow.categories WHERE is_active = true")
            )
            count = result.scalar()
        engine.dispose()
        assert count == 18, "All 18 seed categories must have is_active = true"

    def test_categories_icons_not_empty(self, db_after_007):
        from sqlalchemy import create_engine, text

        engine = create_engine(db_after_007)
        with engine.connect() as conn:
            result = conn.execute(
                text(
                    "SELECT COUNT(*) FROM data_flow.categories "
                    "WHERE icon IS NULL OR icon = ''"
                )
            )
            count = result.scalar()
        engine.dispose()
        assert count == 0, "All categories must have a non-empty icon"


@pytest.mark.integration
class TestMigration008Downgrade:
    """Verify migration 008 downgrade drops the categories table."""

    def test_downgrade_drops_categories_table(self, db_after_007):
        _run_migration_008_upgrade(db_after_007)
        _run_migration_008_downgrade(db_after_007)

        from sqlalchemy import create_engine, inspect

        engine = create_engine(db_after_007)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names(schema="data_flow"))
        engine.dispose()

        assert "categories" not in tables, (
            "data_flow.categories still exists after downgrade 008"
        )
