"""Tests for new Pydantic schemas: recommendations, categories, onboarding."""
from __future__ import annotations

import pytest
from datetime import datetime, timezone
from uuid import UUID, uuid4
from pydantic import ValidationError


@pytest.mark.unit
class TestRecommendationsRequestSchema:
    def test_valid_request(self):
        from src.presentation.schemas.recommendations import RecommendationsRequestSchema
        user_id = uuid4()
        schema = RecommendationsRequestSchema(user_id=user_id, count=50)
        assert schema.user_id == user_id
        assert schema.count == 50

    def test_invalid_user_id_raises(self):
        from src.presentation.schemas.recommendations import RecommendationsRequestSchema
        with pytest.raises(ValidationError):
            RecommendationsRequestSchema(user_id="not-a-uuid", count=50)

    def test_count_less_than_1_raises(self):
        from src.presentation.schemas.recommendations import RecommendationsRequestSchema
        with pytest.raises(ValidationError):
            RecommendationsRequestSchema(user_id=uuid4(), count=0)

    def test_count_negative_raises(self):
        from src.presentation.schemas.recommendations import RecommendationsRequestSchema
        with pytest.raises(ValidationError):
            RecommendationsRequestSchema(user_id=uuid4(), count=-5)


@pytest.mark.unit
class TestRecommendationsResponseSchema:
    def test_serialization(self):
        from src.presentation.schemas.recommendations import RecommendationsResponseSchema
        user_id = uuid4()
        items = [uuid4(), uuid4()]
        now = datetime.now(timezone.utc)
        schema = RecommendationsResponseSchema(
            user_id=user_id,
            items=items,
            count=len(items),
            generated_at=now,
        )
        data = schema.model_dump()
        assert data["user_id"] == user_id
        assert len(data["items"]) == 2
        assert data["count"] == 2
        assert "generated_at" in data

    def test_items_are_uuids(self):
        from src.presentation.schemas.recommendations import RecommendationsResponseSchema
        user_id = uuid4()
        items = [uuid4(), uuid4()]
        schema = RecommendationsResponseSchema(
            user_id=user_id,
            items=items,
            count=2,
            generated_at=datetime.now(timezone.utc),
        )
        assert all(isinstance(item, UUID) for item in schema.items)


@pytest.mark.unit
class TestColdStartResponseSchema:
    def test_serialization(self):
        from src.presentation.schemas.recommendations import ColdStartResponseSchema
        items = [uuid4(), uuid4(), uuid4()]
        now = datetime.now(timezone.utc)
        schema = ColdStartResponseSchema(items=items, count=3, generated_at=now)
        data = schema.model_dump()
        assert len(data["items"]) == 3
        assert data["count"] == 3
        assert "generated_at" in data


@pytest.mark.unit
class TestCategoriesResponseSchema:
    def test_serialization(self):
        from src.presentation.schemas.categories import CategoriesResponseSchema, CategoryItemSchema
        items = [
            CategoryItemSchema(id="технологии", name="Технологии", icon="laptop"),
            CategoryItemSchema(id="наука", name="Наука", icon="flask"),
        ]
        schema = CategoriesResponseSchema(categories=items, min_select=3, max_select=10)
        data = schema.model_dump()
        assert len(data["categories"]) == 2
        assert data["min_select"] == 3
        assert data["max_select"] == 10

    def test_category_item_fields(self):
        from src.presentation.schemas.categories import CategoryItemSchema
        item = CategoryItemSchema(id="спорт", name="Спорт", icon="trophy")
        assert item.id == "спорт"
        assert item.name == "Спорт"
        assert item.icon == "trophy"


@pytest.mark.unit
class TestOnboardingRequestSchema:
    def test_valid_request_with_3_categories(self):
        from src.presentation.schemas.onboarding import OnboardingRequestSchema
        user_id = uuid4()
        schema = OnboardingRequestSchema(
            user_id=user_id,
            categories=["технологии", "наука", "бизнес"],
        )
        assert schema.user_id == user_id
        assert len(schema.categories) == 3

    def test_fewer_than_3_categories_raises(self):
        from src.presentation.schemas.onboarding import OnboardingRequestSchema
        with pytest.raises(ValidationError):
            OnboardingRequestSchema(
                user_id=uuid4(),
                categories=["технологии", "наука"],
            )

    def test_source_content_ids_can_be_none(self):
        from src.presentation.schemas.onboarding import OnboardingRequestSchema
        schema = OnboardingRequestSchema(
            user_id=uuid4(),
            categories=["технологии", "наука", "бизнес"],
            source_content_ids=None,
        )
        assert schema.source_content_ids is None

    def test_more_than_10_source_content_ids_raises(self):
        from src.presentation.schemas.onboarding import OnboardingRequestSchema
        too_many = [uuid4() for _ in range(11)]
        with pytest.raises(ValidationError):
            OnboardingRequestSchema(
                user_id=uuid4(),
                categories=["технологии", "наука", "бизнес"],
                source_content_ids=too_many,
            )

    def test_exactly_10_source_content_ids_ok(self):
        from src.presentation.schemas.onboarding import OnboardingRequestSchema
        exactly_10 = [uuid4() for _ in range(10)]
        schema = OnboardingRequestSchema(
            user_id=uuid4(),
            categories=["технологии", "наука", "бизнес"],
            source_content_ids=exactly_10,
        )
        assert len(schema.source_content_ids) == 10


@pytest.mark.unit
class TestOnboardingResponseSchema:
    def test_serialization(self):
        from src.presentation.schemas.onboarding import OnboardingResponseSchema
        user_id = uuid4()
        schema = OnboardingResponseSchema(user_id=user_id, profile_initialized=True)
        data = schema.model_dump()
        assert data["user_id"] == user_id
        assert data["profile_initialized"] is True


@pytest.mark.unit
class TestErrorResponseSchema:
    def test_with_user_id(self):
        from src.presentation.schemas.onboarding import ErrorResponseSchema
        user_id = uuid4()
        schema = ErrorResponseSchema(
            error="user_not_found",
            user_id=user_id,
            message="No recommendation profile exists for this user",
        )
        data = schema.model_dump()
        assert data["error"] == "user_not_found"
        assert data["user_id"] == user_id
        assert "message" in data

    def test_without_user_id(self):
        from src.presentation.schemas.onboarding import ErrorResponseSchema
        schema = ErrorResponseSchema(
            error="overloaded",
            user_id=None,
            message="Model inference queue full",
        )
        assert schema.user_id is None
