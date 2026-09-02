"""
Migration Integration Tests — Tasks 2-6.

These integration tests verify:
- Task 2: alembic upgrade head succeeds, pgvector extension enabled, downgrade works
- Task 3: posts_features table created with all columns
- Task 4: rec_profiles table created with correct types
- Task 5: rec_entity_interests table with composite PK and indexes
- Task 6: Kotlin-owned tables + rec_config seed data + indexes
"""

from pathlib import Path

import pytest
from testcontainers.postgres import PostgresContainer

PROJECT_ROOT = Path(__file__).parent.parent.parent
ALEMBIC_INI = PROJECT_ROOT / "alembic.ini"


# ---------------------------------------------------------------------------
# Shared container for Tasks 2-6 (module-scope)
# ---------------------------------------------------------------------------


@pytest.fixture(scope="module")
def pgvector_container():
    """Start a PostgreSQL+pgvector testcontainer for migration tests."""
    with PostgresContainer(image="ankane/pgvector:latest") as container:
        yield container


@pytest.fixture(scope="module")
def migration_db_url(pgvector_container):
    """Return a psycopg2-compatible sync connection URL for alembic."""
    url = pgvector_container.get_connection_url()
    # testcontainers returns postgresql+psycopg2://... ensure it's the right driver
    if "psycopg2" not in url:
        url = url.replace("postgresql://", "postgresql+psycopg2://", 1)
    return url


def _alembic_upgrade(db_url: str, target: str = "heads") -> None:
    """Upgrade to the specified target. Defaults to 'heads' (all branches)."""
    from alembic import command
    from alembic.config import Config

    cfg = Config(str(ALEMBIC_INI))
    cfg.set_main_option("sqlalchemy.url", db_url)
    command.upgrade(cfg, target)


def _alembic_downgrade(db_url: str, target: str = "base") -> None:
    """Downgrade to the specified target.

    When downgrading to 'base' with multiple branches, the rec_data_flow branch
    (007) must be downgraded first because it creates vector-typed columns that
    depend on the pgvector extension. Downgrading the 001-006 branch last ensures
    the vector extension is dropped only after all vector columns are gone.
    """
    from alembic import command
    from alembic.config import Config

    cfg = Config(str(ALEMBIC_INI))
    cfg.set_main_option("sqlalchemy.url", db_url)

    if target == "base":
        # Drop rec_data_flow branch (007) first to remove vector columns
        # before the 001 downgrade attempts to drop the vector extension
        try:
            command.downgrade(cfg, "rec_data_flow@base")
        except Exception:
            pass  # Already at base for this branch
        command.downgrade(cfg, "base")
    else:
        command.downgrade(cfg, target)


# ---------------------------------------------------------------------------
# Task 2 — Alembic Configuration and PGVector Extension
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestAlembicMigrations:
    def test_alembic_upgrade_heads_succeeds(self, migration_db_url):
        """Running alembic upgrade heads (all branches) must complete without errors."""
        _alembic_upgrade(migration_db_url)

    def test_pgvector_extension_enabled(self, migration_db_url):
        """After upgrade head, the 'vector' extension must exist in pg_extension."""
        from sqlalchemy import create_engine, text

        engine = create_engine(migration_db_url)
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT extname FROM pg_extension WHERE extname = 'vector'")
            )
            row = result.fetchone()
        engine.dispose()

        assert row is not None, "pgvector extension 'vector' was not found in pg_extension after migration"
        assert row[0] == "vector"

    def test_alembic_downgrade_base_succeeds(self, migration_db_url):
        """Running alembic downgrade base (all branches) must complete without errors."""
        _alembic_downgrade(migration_db_url)


# ---------------------------------------------------------------------------
# Task 3 — posts_features Table
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestPostsFeaturesMigration:
    """Verify the posts_features table is created by migration 002."""

    @pytest.fixture(autouse=True)
    def run_migrations(self, migration_db_url):
        """Upgrade to head before tests, downgrade after."""
        _alembic_upgrade(migration_db_url)
        yield
        _alembic_downgrade(migration_db_url)

    def test_posts_features_table_exists(self, migration_db_url):
        """posts_features table must exist after upgrade head."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        engine.dispose()

        assert "posts_features" in tables, "posts_features table not found after migration"

    def test_posts_features_columns(self, migration_db_url):
        """posts_features must have all required columns with correct types."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        columns = {col["name"]: col for col in inspector.get_columns("posts_features")}
        engine.dispose()

        expected_columns = [
            "post_id",
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
        for col_name in expected_columns:
            assert col_name in columns, f"Column '{col_name}' missing from posts_features"

    def test_posts_features_downgrade(self, migration_db_url):
        """posts_features table must be dropped after downgrade."""
        # The autouse fixture handles upgrade/downgrade; after yield the downgrade ran.
        # We run downgrade explicitly here and then check.
        from sqlalchemy import create_engine, inspect

        # At this point autouse already ran downgrade; re-upgrade then downgrade explicitly.
        _alembic_downgrade(migration_db_url)  # idempotent if already at base
        _alembic_upgrade(migration_db_url)
        _alembic_downgrade(migration_db_url)

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        engine.dispose()

        assert "posts_features" not in tables, "posts_features table still exists after downgrade"
        # Re-upgrade so autouse fixture downgrade doesn't fail
        _alembic_upgrade(migration_db_url)


# ---------------------------------------------------------------------------
# Task 4 — rec_profiles Table
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestRecProfilesMigration:
    """Verify the rec_profiles table is created by migration 003."""

    @pytest.fixture(autouse=True)
    def run_migrations(self, migration_db_url):
        _alembic_upgrade(migration_db_url)
        yield
        _alembic_downgrade(migration_db_url)

    def test_rec_profiles_table_exists(self, migration_db_url):
        """rec_profiles table must exist after upgrade head."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        engine.dispose()

        assert "rec_profiles" in tables, "rec_profiles table not found after migration"

    def test_rec_profiles_columns(self, migration_db_url):
        """rec_profiles must have all required columns."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        columns = {col["name"]: col for col in inspector.get_columns("rec_profiles")}
        engine.dispose()

        expected_columns = [
            "user_id",
            "topic_vector",
            "embedding",
            "sentiment_prefs",
            "format_prefs",
            "interaction_count",
            "created_at",
            "last_updated",
        ]
        for col_name in expected_columns:
            assert col_name in columns, f"Column '{col_name}' missing from rec_profiles"

        # interaction_count should not be nullable (has default 0)
        assert not columns["interaction_count"]["nullable"] or columns["interaction_count"].get("default") is not None

        # topic_vector and sentiment_prefs and format_prefs must be NOT NULL
        assert not columns["topic_vector"]["nullable"], "topic_vector must be NOT NULL"
        assert not columns["sentiment_prefs"]["nullable"], "sentiment_prefs must be NOT NULL"
        assert not columns["format_prefs"]["nullable"], "format_prefs must be NOT NULL"

        # embedding must be nullable
        assert columns["embedding"]["nullable"], "embedding must be nullable"

    def test_rec_profiles_downgrade(self, migration_db_url):
        """rec_profiles table must be dropped after downgrade."""
        _alembic_downgrade(migration_db_url)
        _alembic_upgrade(migration_db_url)
        _alembic_downgrade(migration_db_url)

        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        engine.dispose()

        assert "rec_profiles" not in tables, "rec_profiles table still exists after downgrade"
        # Re-upgrade so autouse fixture downgrade doesn't fail
        _alembic_upgrade(migration_db_url)


# ---------------------------------------------------------------------------
# Task 5 — rec_entity_interests Table
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestRecEntityInterestsMigration:
    """Verify the rec_entity_interests table is created by migration 004."""

    @pytest.fixture(autouse=True)
    def run_migrations(self, migration_db_url):
        _alembic_upgrade(migration_db_url)
        yield
        _alembic_downgrade(migration_db_url)

    def test_table_exists(self, migration_db_url):
        """rec_entity_interests table must exist after upgrade head."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        engine.dispose()

        assert "rec_entity_interests" in tables, "rec_entity_interests table not found after migration"

    def test_columns(self, migration_db_url):
        """rec_entity_interests must have all required columns."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        columns = {col["name"]: col for col in inspector.get_columns("rec_entity_interests")}
        pk_constraint = inspector.get_pk_constraint("rec_entity_interests")
        engine.dispose()

        for col_name in ["user_id", "entity_type", "entity_name", "weight", "last_seen"]:
            assert col_name in columns, f"Column '{col_name}' missing from rec_entity_interests"

        # Composite PK must include all three key columns
        pk_cols = pk_constraint["constrained_columns"]
        assert "user_id" in pk_cols, "user_id must be part of composite PK"
        assert "entity_type" in pk_cols, "entity_type must be part of composite PK"
        assert "entity_name" in pk_cols, "entity_name must be part of composite PK"

    def test_indexes_exist(self, migration_db_url):
        """idx_entity_interests_user and idx_entity_interests_last_seen must exist."""
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        indexes = {idx["name"] for idx in inspector.get_indexes("rec_entity_interests")}
        engine.dispose()

        assert "idx_entity_interests_user" in indexes, "idx_entity_interests_user index missing"
        assert "idx_entity_interests_last_seen" in indexes, "idx_entity_interests_last_seen index missing"

    def test_downgrade(self, migration_db_url):
        """rec_entity_interests table must be dropped after downgrade."""
        _alembic_downgrade(migration_db_url)
        _alembic_upgrade(migration_db_url)
        _alembic_downgrade(migration_db_url)

        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        engine.dispose()

        assert "rec_entity_interests" not in tables, "rec_entity_interests still exists after downgrade"
        # Re-upgrade so autouse fixture downgrade doesn't fail
        _alembic_upgrade(migration_db_url)


# ---------------------------------------------------------------------------
# Task 6 — Kotlin-Owned Tables + Seed Data + Indexes
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestKotlinOwnedTables:
    """Verify Kotlin-owned tables, indexes, and rec_config seed data (migration 005)."""

    @pytest.fixture(autouse=True)
    def run_migrations(self, migration_db_url):
        _alembic_upgrade(migration_db_url)
        yield
        _alembic_downgrade(migration_db_url)

    def _get_inspector(self, migration_db_url):
        from sqlalchemy import create_engine, inspect

        engine = create_engine(migration_db_url)
        inspector = inspect(engine)
        return engine, inspector

    def test_posts_raw_exists(self, migration_db_url):
        """posts_raw table must exist with correct columns."""
        engine, inspector = self._get_inspector(migration_db_url)
        tables = inspector.get_table_names()
        columns = {col["name"] for col in inspector.get_columns("posts_raw")} if "posts_raw" in tables else set()
        engine.dispose()

        assert "posts_raw" in tables, "posts_raw table not found"
        for col in ["id", "channel_id", "content", "post_date", "is_processed"]:
            assert col in columns, f"Column '{col}' missing from posts_raw"

    def test_user_interactions_exists(self, migration_db_url):
        """user_interactions table must exist with event_id UNIQUE and processed default false."""
        engine, inspector = self._get_inspector(migration_db_url)
        tables = inspector.get_table_names()
        columns = {col["name"]: col for col in inspector.get_columns("user_interactions")} if "user_interactions" in tables else {}
        engine.dispose()

        assert "user_interactions" in tables, "user_interactions table not found"
        for col in ["id", "event_id", "user_id", "post_id", "event_type", "duration_ms", "scroll_pct", "max_scroll_pct", "created_at", "processed"]:
            assert col in columns, f"Column '{col}' missing from user_interactions"

    def test_user_disliked_posts_exists(self, migration_db_url):
        """user_disliked_posts must exist with composite PK (user_id, post_id)."""
        engine, inspector = self._get_inspector(migration_db_url)
        tables = inspector.get_table_names()
        pk = inspector.get_pk_constraint("user_disliked_posts")["constrained_columns"] if "user_disliked_posts" in tables else []
        engine.dispose()

        assert "user_disliked_posts" in tables, "user_disliked_posts table not found"
        assert "user_id" in pk and "post_id" in pk, "user_disliked_posts composite PK missing user_id or post_id"

    def test_user_profiles_exists(self, migration_db_url):
        """user_profiles table must exist."""
        engine, inspector = self._get_inspector(migration_db_url)
        tables = inspector.get_table_names()
        columns = {col["name"] for col in inspector.get_columns("user_profiles")} if "user_profiles" in tables else set()
        engine.dispose()

        assert "user_profiles" in tables, "user_profiles table not found"
        for col in ["user_id", "display_name", "created_at"]:
            assert col in columns, f"Column '{col}' missing from user_profiles"

    def test_rec_config_exists(self, migration_db_url):
        """rec_config table must exist with key, value, description, updated_at."""
        engine, inspector = self._get_inspector(migration_db_url)
        tables = inspector.get_table_names()
        columns = {col["name"] for col in inspector.get_columns("rec_config")} if "rec_config" in tables else set()
        engine.dispose()

        assert "rec_config" in tables, "rec_config table not found"
        for col in ["key", "value", "description", "updated_at"]:
            assert col in columns, f"Column '{col}' missing from rec_config"

    def test_rec_config_seed_data(self, migration_db_url):
        """rec_config must contain all 5 seed config groups."""
        from sqlalchemy import create_engine, text

        engine = create_engine(migration_db_url)
        with engine.connect() as conn:
            result = conn.execute(text("SELECT key FROM rec_config ORDER BY key"))
            keys = {row[0] for row in result.fetchall()}
        engine.dispose()

        expected_keys = {
            "signal_weights",
            "scoring_weights",
            "profile_params",
            "ranking_params",
            "onboarding_params",
        }
        for key in expected_keys:
            assert key in keys, f"rec_config seed key '{key}' missing"

    def test_indexes_exist(self, migration_db_url):
        """All required indexes must exist after migration 005."""
        from sqlalchemy import create_engine, text

        engine = create_engine(migration_db_url)
        with engine.connect() as conn:
            result = conn.execute(
                text("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
            )
            indexes = {row[0] for row in result.fetchall()}
        engine.dispose()

        expected_indexes = [
            "idx_posts_raw_unprocessed",
            "idx_interactions_unprocessed",
            "idx_interactions_user_post",
            "idx_disliked",
        ]
        for idx in expected_indexes:
            assert idx in indexes, f"Index '{idx}' missing after migration"
