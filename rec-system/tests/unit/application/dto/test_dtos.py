import pytest
from uuid import UUID, uuid4
from typing import Optional

from src.application.dto.generate_feed import GenerateFeedRequest, GenerateFeedResponse
from src.application.dto.onboard import OnboardRequest, OnboardResponse
from src.application.dto.process_content import ProcessContentCommand, ProcessContentResult
from src.application.dto.update_profile import UpdateProfileCommand, UpdateProfileResult


class TestGenerateFeedRequest:
    def test_create(self) -> None:
        user_id = UUID("12345678-1234-5678-1234-567812345678")
        req = GenerateFeedRequest(user_id=user_id, feed_size=200)
        assert req.user_id == user_id
        assert req.feed_size == 200

    def test_default_feed_size(self) -> None:
        user_id = uuid4()
        req = GenerateFeedRequest(user_id=user_id)
        assert req.feed_size == 200

    def test_custom_feed_size(self) -> None:
        user_id = uuid4()
        req = GenerateFeedRequest(user_id=user_id, feed_size=50)
        assert req.feed_size == 50


class TestGenerateFeedResponse:
    def test_create(self) -> None:
        feed = [{"post_id": 1, "score": 0.9}, {"post_id": 2, "score": 0.8}]
        meta = {"total": 2, "candidates_scored": 100}
        resp = GenerateFeedResponse(feed=feed, meta=meta)
        assert resp.feed == feed
        assert resp.meta == meta

    def test_empty_feed(self) -> None:
        resp = GenerateFeedResponse(feed=[], meta={"total": 0})
        assert resp.feed == []


class TestOnboardRequest:
    def test_create(self) -> None:
        user_id = UUID("12345678-1234-5678-1234-567812345678")
        topics = ["технологии", "спорт", "наука"]
        req = OnboardRequest(user_id=user_id, categories=topics)
        assert req.user_id == user_id
        assert req.categories == topics

    def test_empty_categories(self) -> None:
        user_id = uuid4()
        req = OnboardRequest(user_id=user_id, categories=[])
        assert req.categories == []

    def test_with_source_content_ids(self) -> None:
        user_id = uuid4()
        content_ids = [uuid4(), uuid4()]
        req = OnboardRequest(user_id=user_id, categories=["технологии", "спорт", "наука"],
                             source_content_ids=content_ids)
        assert req.source_content_ids == content_ids


class TestOnboardResponse:
    def test_create(self) -> None:
        user_id = uuid4()
        resp = OnboardResponse(user_id=user_id, profile_initialized=True)
        assert resp.profile_initialized is True
        assert resp.user_id == user_id

    def test_status_field_default(self) -> None:
        user_id = uuid4()
        resp = OnboardResponse(user_id=user_id, profile_initialized=True)
        assert resp.status == "ok"


class TestProcessContentCommand:
    def test_create(self) -> None:
        cmd = ProcessContentCommand(batch_size=32)
        assert cmd.batch_size == 32

    def test_different_batch_size(self) -> None:
        cmd = ProcessContentCommand(batch_size=64)
        assert cmd.batch_size == 64


class TestProcessContentResult:
    def test_create(self) -> None:
        result = ProcessContentResult(processed_count=10)
        assert result.processed_count == 10

    def test_zero_processed(self) -> None:
        result = ProcessContentResult(processed_count=0)
        assert result.processed_count == 0


class TestUpdateProfileCommand:
    def test_create_with_user_id(self) -> None:
        user_id = UUID("12345678-1234-5678-1234-567812345678")
        cmd = UpdateProfileCommand(user_id=user_id)
        assert cmd.user_id == user_id

    def test_create_all_users(self) -> None:
        cmd = UpdateProfileCommand(user_id=None)
        assert cmd.user_id is None

    def test_default_user_id_is_none(self) -> None:
        cmd = UpdateProfileCommand()
        assert cmd.user_id is None


class TestUpdateProfileResult:
    def test_create(self) -> None:
        result = UpdateProfileResult(users_updated=5, events_processed=20)
        assert result.users_updated == 5
        assert result.events_processed == 20

    def test_zero_values(self) -> None:
        result = UpdateProfileResult(users_updated=0, events_processed=0)
        assert result.users_updated == 0
        assert result.events_processed == 0
