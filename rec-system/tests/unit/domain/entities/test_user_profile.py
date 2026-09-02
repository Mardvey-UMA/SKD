import pytest
from datetime import datetime, timezone
from uuid import UUID, uuid4

from src.domain.entities.user_profile import UserProfile
from src.domain.value_objects.topic_vector import TopicVector
from src.domain.value_objects.sentiment_prefs import SentimentPrefs
from src.domain.value_objects.format_prefs import FormatPrefs


class TestUserProfile:
    @pytest.fixture
    def topic_vector(self) -> TopicVector:
        return TopicVector.create_uniform()

    @pytest.fixture
    def sentiment_prefs(self) -> SentimentPrefs:
        return SentimentPrefs.create_uniform()

    @pytest.fixture
    def format_prefs(self) -> FormatPrefs:
        return FormatPrefs.create_default()

    @pytest.fixture
    def user_id(self) -> UUID:
        return UUID("12345678-1234-5678-1234-567812345678")

    @pytest.fixture
    def profile_with_embedding(
        self,
        user_id: UUID,
        topic_vector: TopicVector,
        sentiment_prefs: SentimentPrefs,
        format_prefs: FormatPrefs,
    ) -> UserProfile:
        return UserProfile(
            user_id=user_id,
            topic_vector=topic_vector,
            embedding=[0.1, 0.2, 0.3],
            sentiment_prefs=sentiment_prefs,
            format_prefs=format_prefs,
            interaction_count=5,
            created_at=datetime(2024, 1, 1, tzinfo=timezone.utc),
            last_updated=datetime(2024, 1, 15, tzinfo=timezone.utc),
        )

    @pytest.fixture
    def profile_cold_start(
        self,
        user_id: UUID,
        topic_vector: TopicVector,
        sentiment_prefs: SentimentPrefs,
        format_prefs: FormatPrefs,
    ) -> UserProfile:
        return UserProfile(
            user_id=user_id,
            topic_vector=topic_vector,
            embedding=None,
            sentiment_prefs=sentiment_prefs,
            format_prefs=format_prefs,
            interaction_count=0,
            created_at=datetime(2024, 1, 1, tzinfo=timezone.utc),
            last_updated=datetime(2024, 1, 1, tzinfo=timezone.utc),
        )

    def test_create_with_all_fields(self, profile_with_embedding: UserProfile) -> None:
        p = profile_with_embedding
        assert p.user_id == UUID("12345678-1234-5678-1234-567812345678")
        assert isinstance(p.topic_vector, TopicVector)
        assert p.embedding == [0.1, 0.2, 0.3]
        assert isinstance(p.sentiment_prefs, SentimentPrefs)
        assert isinstance(p.format_prefs, FormatPrefs)
        assert p.interaction_count == 5
        assert p.created_at == datetime(2024, 1, 1, tzinfo=timezone.utc)
        assert p.last_updated == datetime(2024, 1, 15, tzinfo=timezone.utc)

    def test_has_embedding_true(self, profile_with_embedding: UserProfile) -> None:
        assert profile_with_embedding.has_embedding is True

    def test_has_embedding_false(self, profile_cold_start: UserProfile) -> None:
        assert profile_cold_start.has_embedding is False

    def test_is_cold_start_true(self, profile_cold_start: UserProfile) -> None:
        assert profile_cold_start.is_cold_start is True

    def test_is_cold_start_false(self, profile_with_embedding: UserProfile) -> None:
        assert profile_with_embedding.is_cold_start is False

    def test_increment_interaction_count(self, profile_cold_start: UserProfile) -> None:
        updated = profile_cold_start.increment_interaction_count()
        assert updated is not profile_cold_start
        assert updated.interaction_count == 1
        assert profile_cold_start.interaction_count == 0

    def test_with_updated_topic_vector(
        self, profile_cold_start: UserProfile
    ) -> None:
        new_vector = TopicVector.create_from_selected(["политика", "спорт"])
        updated = profile_cold_start.with_updated_topic_vector(new_vector)
        assert updated is not profile_cold_start
        assert updated.topic_vector is new_vector
        assert profile_cold_start.topic_vector is not new_vector

    def test_with_updated_embedding(
        self, profile_cold_start: UserProfile
    ) -> None:
        new_embedding = [0.9, 0.8, 0.7]
        updated = profile_cold_start.with_updated_embedding(new_embedding)
        assert updated is not profile_cold_start
        assert updated.embedding == [0.9, 0.8, 0.7]
        assert profile_cold_start.embedding is None

    def test_with_updated_sentiment_prefs(
        self, profile_cold_start: UserProfile
    ) -> None:
        new_prefs = SentimentPrefs({"POSITIVE": 0.8, "NEGATIVE": 0.1, "NEUTRAL": 0.1})
        updated = profile_cold_start.with_updated_sentiment_prefs(new_prefs)
        assert updated is not profile_cold_start
        assert updated.sentiment_prefs is new_prefs
        assert profile_cold_start.sentiment_prefs is not new_prefs

    def test_with_updated_format_prefs(
        self, profile_cold_start: UserProfile
    ) -> None:
        new_prefs = FormatPrefs(avg_length=500.0, avg_complexity=0.8)
        updated = profile_cold_start.with_updated_format_prefs(new_prefs)
        assert updated is not profile_cold_start
        assert updated.format_prefs is new_prefs
        assert profile_cold_start.format_prefs is not new_prefs
