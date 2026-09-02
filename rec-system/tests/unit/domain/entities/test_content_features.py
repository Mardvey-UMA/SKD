import pytest
from datetime import datetime, timezone
from uuid import UUID, uuid4

from src.domain.entities.content_features import ContentFeatures

SAMPLE_UUID = UUID("12345678-1234-5678-1234-567812345678")


class TestContentFeatures:
    @pytest.fixture
    def full_content_features(self) -> ContentFeatures:
        return ContentFeatures(
            post_id=SAMPLE_UUID,
            content="This is a sample post about technology.",
            post_date=datetime(2024, 1, 15, 10, 0, 0, tzinfo=timezone.utc),
            title="Sample Post Title",
            source_type="TELEGRAM",
            text_length=39.0,
            word_count=7,
            reading_time=0.5,
            complexity=0.6,
            is_short_form=True,
            is_long_form=False,
            topic_1="технологии",
            topic_1_score=0.7,
            topic_2="наука",
            topic_2_score=0.2,
            topic_3="бизнес",
            topic_3_score=0.1,
            sentiment="POSITIVE",
            sentiment_score=0.85,
            entities_persons=["Иванов"],
            entities_organizations=["Яндекс"],
            entities_locations=["Москва"],
            embedding=[0.1, 0.2, 0.3],
            processed_at=datetime(2024, 1, 15, 11, 0, 0, tzinfo=timezone.utc),
        )

    def test_post_id_is_uuid(self, full_content_features: ContentFeatures) -> None:
        """post_id must be a UUID, not int."""
        assert isinstance(full_content_features.post_id, UUID)
        assert full_content_features.post_id == SAMPLE_UUID

    def test_create_with_all_fields(self, full_content_features: ContentFeatures) -> None:
        cf = full_content_features
        assert cf.post_id == SAMPLE_UUID
        assert cf.title == "Sample Post Title"
        assert cf.source_type == "TELEGRAM"
        assert cf.content == "This is a sample post about technology."
        assert cf.post_date == datetime(2024, 1, 15, 10, 0, 0, tzinfo=timezone.utc)
        assert cf.text_length == 39.0
        assert cf.word_count == 7
        assert cf.reading_time == 0.5
        assert cf.complexity == 0.6
        assert cf.is_short_form is True
        assert cf.is_long_form is False
        assert cf.topic_1 == "технологии"
        assert cf.topic_1_score == 0.7
        assert cf.topic_2 == "наука"
        assert cf.topic_2_score == 0.2
        assert cf.topic_3 == "бизнес"
        assert cf.topic_3_score == 0.1
        assert cf.sentiment == "POSITIVE"
        assert cf.sentiment_score == 0.85
        assert cf.entities_persons == ["Иванов"]
        assert cf.entities_organizations == ["Яндекс"]
        assert cf.entities_locations == ["Москва"]
        assert cf.embedding == [0.1, 0.2, 0.3]
        assert cf.processed_at == datetime(2024, 1, 15, 11, 0, 0, tzinfo=timezone.utc)

    def test_no_channel_id(self, full_content_features: ContentFeatures) -> None:
        """ContentFeatures must NOT have channel_id property."""
        assert not hasattr(full_content_features, 'channel_id')

    def test_has_title_property(self) -> None:
        """ContentFeatures must have title property."""
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        assert hasattr(cf, 'title')
        assert cf.title is None

    def test_has_source_type_property(self) -> None:
        """ContentFeatures must have source_type property."""
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        assert hasattr(cf, 'source_type')
        assert cf.source_type is None

    def test_has_source_id_property(self) -> None:
        """Phase 1 user-sources: ContentFeatures must have a source_id property."""
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        assert hasattr(cf, 'source_id')
        assert cf.source_id is None

    def test_source_id_round_trip(self) -> None:
        """Phase 1: source_id passed into the constructor is exposed on the property."""
        src = uuid4()
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
            source_id=src,
        )
        assert cf.source_id == src

    def test_create_with_minimal_fields(self) -> None:
        uid = uuid4()
        cf = ContentFeatures(
            post_id=uid,
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        assert cf.post_id == uid
        assert cf.text_length is None
        assert cf.embedding is None
        assert cf.processed_at is None
        assert cf.title is None
        assert cf.source_type is None

    def test_topic_dict(self, full_content_features: ContentFeatures) -> None:
        result = full_content_features.topic_dict()
        assert isinstance(result, dict)
        assert result["технологии"] == 0.7
        assert result["наука"] == 0.2
        assert result["бизнес"] == 0.1

    def test_topic_dict_partial(self) -> None:
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
            topic_1="политика",
            topic_1_score=0.9,
        )
        result = cf.topic_dict()
        assert result["политика"] == 0.9
        assert len(result) == 1

    def test_topic_dict_empty(self) -> None:
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        result = cf.topic_dict()
        assert result == {}

    def test_all_entities(self, full_content_features: ContentFeatures) -> None:
        result = full_content_features.all_entities()
        assert isinstance(result, list)
        assert "Иванов" in result
        assert "Яндекс" in result
        assert "Москва" in result
        assert len(result) == 3

    def test_all_entities_empty(self) -> None:
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        result = cf.all_entities()
        assert result == []

    def test_is_processed_true(self, full_content_features: ContentFeatures) -> None:
        assert full_content_features.is_processed is True

    def test_is_processed_false(self) -> None:
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        assert cf.is_processed is False

    def test_has_embedding_true(self, full_content_features: ContentFeatures) -> None:
        assert full_content_features.has_embedding is True

    def test_has_embedding_false(self) -> None:
        cf = ContentFeatures(
            post_id=uuid4(),
            content="Hello",
            post_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )
        assert cf.has_embedding is False
