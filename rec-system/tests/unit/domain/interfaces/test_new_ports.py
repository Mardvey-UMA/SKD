"""Failing tests for new domain ABC ports (Task 5).

Tests for:
- CacheClient
- EventPublisher
- CategoryRepository
- RecommendationHistoryRepository

Also verifies the Category domain entity used by CategoryRepository.
"""

import inspect
import pytest
from uuid import UUID


class TestCacheClient:
    def test_is_abstract(self):
        from src.domain.interfaces.cache_client import CacheClient

        with pytest.raises(TypeError):
            CacheClient()  # type: ignore[abstract]

    def test_has_get_method(self):
        from src.domain.interfaces.cache_client import CacheClient

        assert hasattr(CacheClient, "get")
        sig = inspect.signature(CacheClient.get)
        assert "key" in sig.parameters

    def test_has_set_method(self):
        from src.domain.interfaces.cache_client import CacheClient

        assert hasattr(CacheClient, "set")
        sig = inspect.signature(CacheClient.set)
        params = sig.parameters
        assert "key" in params
        assert "value" in params
        assert "ttl" in params

    def test_has_delete_method(self):
        from src.domain.interfaces.cache_client import CacheClient

        assert hasattr(CacheClient, "delete")
        sig = inspect.signature(CacheClient.delete)
        assert "key" in sig.parameters

    def test_concrete_subclass_must_implement_all(self):
        from src.domain.interfaces.cache_client import CacheClient

        class Incomplete(CacheClient):
            async def get(self, key: str): ...

        with pytest.raises(TypeError):
            Incomplete()  # type: ignore[abstract]

    def test_concrete_subclass_passes_when_complete(self):
        from src.domain.interfaces.cache_client import CacheClient

        class Complete(CacheClient):
            async def get(self, key: str): return None
            async def set(self, key: str, value: str, ttl=None): ...
            async def delete(self, key: str): ...

        instance = Complete()
        assert isinstance(instance, CacheClient)


class TestEventPublisher:
    def test_is_abstract(self):
        from src.domain.interfaces.event_publisher import EventPublisher

        with pytest.raises(TypeError):
            EventPublisher()  # type: ignore[abstract]

    def test_has_publish_recommendations_updated(self):
        from src.domain.interfaces.event_publisher import EventPublisher

        assert hasattr(EventPublisher, "publish_recommendations_updated")
        sig = inspect.signature(EventPublisher.publish_recommendations_updated)
        params = sig.parameters
        assert "user_id" in params
        assert "reason" in params

    def test_concrete_subclass_must_implement(self):
        from src.domain.interfaces.event_publisher import EventPublisher

        class Incomplete(EventPublisher):
            pass

        with pytest.raises(TypeError):
            Incomplete()  # type: ignore[abstract]

    def test_concrete_subclass_passes_when_complete(self):
        from src.domain.interfaces.event_publisher import EventPublisher

        class Complete(EventPublisher):
            async def publish_recommendations_updated(self, user_id: str, reason: str): ...

        instance = Complete()
        assert isinstance(instance, EventPublisher)


class TestCategoryEntity:
    """Category domain entity is needed by CategoryRepository."""

    def test_category_entity_importable(self):
        from src.domain.entities.category import Category
        assert Category is not None

    def test_category_has_required_fields(self):
        from src.domain.entities.category import Category

        cat = Category(id="технологии", name="Технологии", icon="laptop", sort_order=3)
        assert cat.id == "технологии"
        assert cat.name == "Технологии"
        assert cat.icon == "laptop"
        assert cat.sort_order == 3
        assert cat.is_active is True  # default

    def test_category_is_active_defaults_true(self):
        from src.domain.entities.category import Category

        cat = Category(id="спорт", name="Спорт", icon="trophy", sort_order=5)
        assert cat.is_active is True

    def test_category_can_be_inactive(self):
        from src.domain.entities.category import Category

        cat = Category(id="старое", name="Старое", icon="x", sort_order=99, is_active=False)
        assert cat.is_active is False


class TestCategoryRepository:
    def test_is_abstract(self):
        from src.domain.interfaces.category_repository import CategoryRepository

        with pytest.raises(TypeError):
            CategoryRepository()  # type: ignore[abstract]

    def test_has_get_active_categories(self):
        from src.domain.interfaces.category_repository import CategoryRepository

        assert hasattr(CategoryRepository, "get_active_categories")
        sig = inspect.signature(CategoryRepository.get_active_categories)
        assert "self" in sig.parameters

    def test_has_get_category_ids(self):
        from src.domain.interfaces.category_repository import CategoryRepository

        assert hasattr(CategoryRepository, "get_category_ids")
        sig = inspect.signature(CategoryRepository.get_category_ids)
        assert "self" in sig.parameters

    def test_concrete_subclass_must_implement_all(self):
        from src.domain.interfaces.category_repository import CategoryRepository

        class Incomplete(CategoryRepository):
            async def get_active_categories(self): return []

        with pytest.raises(TypeError):
            Incomplete()  # type: ignore[abstract]

    def test_concrete_subclass_passes_when_complete(self):
        from src.domain.interfaces.category_repository import CategoryRepository

        class Complete(CategoryRepository):
            async def get_active_categories(self): return []
            async def get_category_ids(self): return []

        instance = Complete()
        assert isinstance(instance, CategoryRepository)


class TestRecommendationHistoryRepository:
    def test_is_abstract(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        with pytest.raises(TypeError):
            RecommendationHistoryRepository()  # type: ignore[abstract]

    def test_has_get_recommended_ids(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        assert hasattr(RecommendationHistoryRepository, "get_recommended_ids")
        sig = inspect.signature(RecommendationHistoryRepository.get_recommended_ids)
        assert "user_id" in sig.parameters

    def test_has_save_recommendations(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        assert hasattr(RecommendationHistoryRepository, "save_recommendations")
        sig = inspect.signature(RecommendationHistoryRepository.save_recommendations)
        params = sig.parameters
        assert "user_id" in params
        assert "content_ids" in params

    def test_has_delete_older_than(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        assert hasattr(RecommendationHistoryRepository, "delete_older_than")
        sig = inspect.signature(RecommendationHistoryRepository.delete_older_than)
        assert "days" in sig.parameters

    def test_concrete_subclass_must_implement_all(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        class Incomplete(RecommendationHistoryRepository):
            async def get_recommended_ids(self, user_id): return set()
            async def save_recommendations(self, user_id, content_ids): ...

        with pytest.raises(TypeError):
            Incomplete()  # type: ignore[abstract]

    def test_has_delete_for_user(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        assert hasattr(RecommendationHistoryRepository, "delete_for_user")
        sig = inspect.signature(RecommendationHistoryRepository.delete_for_user)
        assert "user_id" in sig.parameters

    def test_concrete_subclass_passes_when_complete(self):
        from src.domain.interfaces.recommendation_history_repository import (
            RecommendationHistoryRepository,
        )

        class Complete(RecommendationHistoryRepository):
            async def get_recommended_ids(self, user_id): return set()
            async def save_recommendations(self, user_id, content_ids): ...
            async def delete_for_user(self, user_id) -> int: return 0
            async def delete_older_than(self, days: int) -> int: return 0

        instance = Complete()
        assert isinstance(instance, RecommendationHistoryRepository)
