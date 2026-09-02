from datetime import datetime, timezone
from uuid import UUID, uuid4

import pytest


class TestRawContent:
    @pytest.mark.unit
    def test_create_raw_content(self):
        from src.domain.entities.raw_content import RawContent

        uid = uuid4()
        rc = RawContent(
            id=uid,
            title="Breaking news",
            content_body="Some article body text",
            source_type="REUTERS",
            external_id="ext-123",
            published_at=datetime(2026, 4, 1, tzinfo=timezone.utc),
        )
        assert rc.id == uid
        assert rc.title == "Breaking news"
        assert rc.content_body == "Some article body text"
        assert rc.source_type == "REUTERS"
        assert rc.external_id == "ext-123"

    @pytest.mark.unit
    def test_create_minimal_raw_content(self):
        from src.domain.entities.raw_content import RawContent

        rc = RawContent(
            id=uuid4(),
            title="Title",
            content_body="Body",
            source_type="HABR",
            external_id="ext-1",
            published_at=None,
        )
        assert rc.published_at is None

    @pytest.mark.unit
    def test_id_is_uuid(self):
        from src.domain.entities.raw_content import RawContent

        uid = uuid4()
        rc = RawContent(
            id=uid,
            title="Title",
            content_body="Body",
            source_type="HABR",
            external_id="ext-1",
            published_at=None,
        )
        assert isinstance(rc.id, UUID)

    @pytest.mark.unit
    def test_text_for_normalization_concatenates(self):
        from src.domain.entities.raw_content import RawContent

        rc = RawContent(
            id=uuid4(),
            title="Hello",
            content_body="World body",
            source_type="VCRU",
            external_id="ext-2",
            published_at=None,
        )
        result = rc.text_for_normalization
        assert result == "Hello\nWorld body"

    @pytest.mark.unit
    def test_text_for_normalization_with_empty_body(self):
        from src.domain.entities.raw_content import RawContent

        rc = RawContent(
            id=uuid4(),
            title="Only title",
            content_body="",
            source_type="HABR",
            external_id="ext-3",
            published_at=None,
        )
        assert rc.text_for_normalization == "Only title\n"
