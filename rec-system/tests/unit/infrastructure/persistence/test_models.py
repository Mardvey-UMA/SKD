import pytest
from sqlalchemy import inspect, Integer, BigInteger, String, Text, Boolean, Float, DateTime
from sqlalchemy.dialects.postgresql import JSONB, UUID


class TestRawContentModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.raw_content import RawContentModel
        assert RawContentModel.__tablename__ == "raw_content"

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.raw_content import RawContentModel
        # Check schema via table_args or metadata
        table = RawContentModel.__table__
        assert table.schema == "data_flow"

    def test_columns(self):
        from src.infrastructure.persistence.models.raw_content import RawContentModel
        mapper = inspect(RawContentModel)
        col_names = {c.key for c in mapper.columns}
        assert "id" in col_names
        assert "external_id" in col_names
        assert "source_id" in col_names
        assert "source_type" in col_names
        assert "raw_data" in col_names
        assert "processing_status" in col_names
        assert "is_processed_by_rec" in col_names
        assert "received_at" in col_names

    def test_id_is_uuid(self):
        from src.infrastructure.persistence.models.raw_content import RawContentModel
        mapper = inspect(RawContentModel)
        id_col = next(c for c in mapper.columns if c.key == "id")
        assert isinstance(id_col.type, UUID)


class TestPostsFeaturesModel:
    def test_table_name_and_columns(self):
        from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
        assert PostsFeaturesModel.__tablename__ == "posts_features"
        mapper = inspect(PostsFeaturesModel)
        col_names = {c.key for c in mapper.columns}
        assert "post_id" in col_names
        assert "source_id" in col_names
        assert "source_type" in col_names
        assert "word_count" in col_names
        assert "text_length" in col_names
        assert "reading_time" in col_names
        assert "complexity" in col_names
        assert "is_short_form" in col_names
        assert "is_long_form" in col_names
        assert "topic_1" in col_names
        assert "topic_1_score" in col_names
        assert "sentiment" in col_names
        assert "entities_persons" in col_names
        assert "embedding" in col_names
        assert "processed_at" in col_names

    def test_source_id_is_uuid_not_null(self):
        """Phase 1 user-sources: source_id is a NOT NULL UUID column."""
        from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
        mapper = inspect(PostsFeaturesModel)
        col = next(c for c in mapper.columns if c.key == "source_id")
        assert isinstance(col.type, UUID)
        assert col.nullable is False

    def test_source_type_is_string_not_null(self):
        """Phase 1 user-sources: source_type is a NOT NULL VARCHAR(50) column."""
        from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
        mapper = inspect(PostsFeaturesModel)
        col = next(c for c in mapper.columns if c.key == "source_type")
        assert isinstance(col.type, String)
        assert col.nullable is False

    def test_embedding_column_is_vector(self):
        from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
        from pgvector.sqlalchemy import Vector
        mapper = inspect(PostsFeaturesModel)
        embedding_col = next(c for c in mapper.columns if c.key == "embedding")
        assert isinstance(embedding_col.type, Vector)

    def test_post_id_is_uuid(self):
        from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
        mapper = inspect(PostsFeaturesModel)
        post_id_col = next(c for c in mapper.columns if c.key == "post_id")
        assert isinstance(post_id_col.type, UUID)

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.posts_features import PostsFeaturesModel
        table = PostsFeaturesModel.__table__
        assert table.schema == "data_flow"


class TestRecProfilesModel:
    def test_table_name_and_columns(self):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
        assert RecProfilesModel.__tablename__ == "rec_profiles"
        mapper = inspect(RecProfilesModel)
        col_names = {c.key for c in mapper.columns}
        assert "user_id" in col_names
        assert "topic_vector" in col_names
        assert "embedding" in col_names
        assert "sentiment_prefs" in col_names
        assert "format_prefs" in col_names
        assert "interaction_count" in col_names
        assert "last_updated" in col_names
        assert "created_at" in col_names

    def test_embedding_column_is_vector(self):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
        from pgvector.sqlalchemy import Vector
        mapper = inspect(RecProfilesModel)
        embedding_col = next(c for c in mapper.columns if c.key == "embedding")
        assert isinstance(embedding_col.type, Vector)

    def test_vector_dimension(self):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
        from pgvector.sqlalchemy import Vector
        mapper = inspect(RecProfilesModel)
        embedding_col = next(c for c in mapper.columns if c.key == "embedding")
        assert embedding_col.type.dim == 312

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.rec_profiles import RecProfilesModel
        table = RecProfilesModel.__table__
        assert table.schema == "data_flow"


class TestRecEntityInterestsModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.rec_entity_interests import RecEntityInterestsModel
        assert RecEntityInterestsModel.__tablename__ == "rec_entity_interests"

    def test_composite_primary_key(self):
        from src.infrastructure.persistence.models.rec_entity_interests import RecEntityInterestsModel
        mapper = inspect(RecEntityInterestsModel)
        pk_cols = {c.key for c in mapper.primary_key}
        assert "user_id" in pk_cols
        assert "entity_type" in pk_cols
        assert "entity_name" in pk_cols
        assert len(pk_cols) == 3

    def test_columns(self):
        from src.infrastructure.persistence.models.rec_entity_interests import RecEntityInterestsModel
        mapper = inspect(RecEntityInterestsModel)
        col_names = {c.key for c in mapper.columns}
        assert "weight" in col_names
        assert "last_seen" in col_names

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.rec_entity_interests import RecEntityInterestsModel
        table = RecEntityInterestsModel.__table__
        assert table.schema == "data_flow"


class TestUserInteractionsModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.user_interactions import UserInteractionsModel
        assert UserInteractionsModel.__tablename__ == "user_interactions"

    def test_columns(self):
        from src.infrastructure.persistence.models.user_interactions import UserInteractionsModel
        mapper = inspect(UserInteractionsModel)
        col_names = {c.key for c in mapper.columns}
        assert "id" in col_names
        assert "event_id" in col_names
        assert "user_id" in col_names
        assert "post_id" in col_names
        assert "event_type" in col_names
        assert "duration_ms" in col_names
        assert "scroll_pct" in col_names
        assert "max_scroll_pct" in col_names
        assert "processed" in col_names
        assert "created_at" in col_names


class TestRecConfigModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.rec_config import RecConfigModel
        assert RecConfigModel.__tablename__ == "rec_config"

    def test_columns(self):
        from src.infrastructure.persistence.models.rec_config import RecConfigModel
        mapper = inspect(RecConfigModel)
        col_names = {c.key for c in mapper.columns}
        assert "key" in col_names
        assert "value" in col_names
        assert "description" in col_names
        assert "updated_at" in col_names

    def test_value_is_jsonb(self):
        from src.infrastructure.persistence.models.rec_config import RecConfigModel
        from sqlalchemy.dialects.postgresql import JSONB
        mapper = inspect(RecConfigModel)
        value_col = next(c for c in mapper.columns if c.key == "value")
        assert isinstance(value_col.type, JSONB)

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.rec_config import RecConfigModel
        table = RecConfigModel.__table__
        assert table.schema == "data_flow"


class TestArticleModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.article import ArticleModel
        assert ArticleModel.__tablename__ == "articles"

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.article import ArticleModel
        assert ArticleModel.__table__.schema == "data_flow"

    def test_columns(self):
        from src.infrastructure.persistence.models.article import ArticleModel
        mapper = inspect(ArticleModel)
        col_names = {c.key for c in mapper.columns}
        assert "id" in col_names
        assert "raw_content_id" in col_names
        assert "content_hash" in col_names
        assert "source" in col_names
        assert "created_at" in col_names

    def test_id_is_primary_key(self):
        from src.infrastructure.persistence.models.article import ArticleModel
        mapper = inspect(ArticleModel)
        pk_cols = {c.key for c in mapper.primary_key}
        assert "id" in pk_cols
        assert len(pk_cols) == 1

    def test_raw_content_id_is_uuid(self):
        from src.infrastructure.persistence.models.article import ArticleModel
        mapper = inspect(ArticleModel)
        col = next(c for c in mapper.columns if c.key == "raw_content_id")
        assert isinstance(col.type, UUID)

    def test_in_init_exports(self):
        from src.infrastructure.persistence.models import ArticleModel
        assert ArticleModel is not None


class TestSimilarityModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.similarity import SimilarityModel
        assert SimilarityModel.__tablename__ == "similarities"

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.similarity import SimilarityModel
        assert SimilarityModel.__table__.schema == "data_flow"

    def test_composite_primary_key(self):
        from src.infrastructure.persistence.models.similarity import SimilarityModel
        mapper = inspect(SimilarityModel)
        pk_cols = {c.key for c in mapper.primary_key}
        assert "article_a" in pk_cols
        assert "article_b" in pk_cols
        assert len(pk_cols) == 2

    def test_columns(self):
        from src.infrastructure.persistence.models.similarity import SimilarityModel
        mapper = inspect(SimilarityModel)
        col_names = {c.key for c in mapper.columns}
        assert "article_a" in col_names
        assert "article_b" in col_names
        assert "score" in col_names
        assert "rel_type" in col_names

    def test_in_init_exports(self):
        from src.infrastructure.persistence.models import SimilarityModel
        assert SimilarityModel is not None


class TestPublishedContentModel:
    def test_table_name(self):
        from src.infrastructure.persistence.models.published_content import PublishedContentModel
        assert PublishedContentModel.__tablename__ == "published_content"

    def test_schema_is_data_flow(self):
        from src.infrastructure.persistence.models.published_content import PublishedContentModel
        assert PublishedContentModel.__table__.schema == "data_flow"

    def test_columns(self):
        from src.infrastructure.persistence.models.published_content import PublishedContentModel
        mapper = inspect(PublishedContentModel)
        col_names = {c.key for c in mapper.columns}
        assert "id" in col_names
        assert "content_id" in col_names

    def test_id_is_primary_key_and_uuid(self):
        from src.infrastructure.persistence.models.published_content import PublishedContentModel
        mapper = inspect(PublishedContentModel)
        pk_cols = {c.key for c in mapper.primary_key}
        assert "id" in pk_cols
        assert len(pk_cols) == 1
        id_col = next(c for c in mapper.columns if c.key == "id")
        assert isinstance(id_col.type, UUID)

    def test_content_id_is_uuid(self):
        from src.infrastructure.persistence.models.published_content import PublishedContentModel
        mapper = inspect(PublishedContentModel)
        col = next(c for c in mapper.columns if c.key == "content_id")
        assert isinstance(col.type, UUID)

    def test_in_init_exports(self):
        from src.infrastructure.persistence.models import PublishedContentModel
        assert PublishedContentModel is not None
