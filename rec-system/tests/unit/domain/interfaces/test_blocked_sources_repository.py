"""RED tests for BlockedSourcesRepository port (Phase 1 user-sources)."""
from __future__ import annotations

import inspect

import pytest


class TestBlockedSourcesRepositoryPort:
    def test_is_abstract(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        with pytest.raises(TypeError):
            BlockedSourcesRepository()  # type: ignore[abstract]

    def test_has_get_source_ids_for_user(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        assert hasattr(BlockedSourcesRepository, "get_source_ids_for_user")
        sig = inspect.signature(BlockedSourcesRepository.get_source_ids_for_user)
        assert "user_id" in sig.parameters

    def test_has_add(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        assert hasattr(BlockedSourcesRepository, "add")
        sig = inspect.signature(BlockedSourcesRepository.add)
        assert "user_id" in sig.parameters
        assert "source_id" in sig.parameters

    def test_has_remove(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        assert hasattr(BlockedSourcesRepository, "remove")
        sig = inspect.signature(BlockedSourcesRepository.remove)
        assert "user_id" in sig.parameters
        assert "source_id" in sig.parameters

    def test_has_list_for_user(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        assert hasattr(BlockedSourcesRepository, "list_for_user")
        sig = inspect.signature(BlockedSourcesRepository.list_for_user)
        assert "user_id" in sig.parameters

    def test_incomplete_subclass_not_instantiable(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        class Incomplete(BlockedSourcesRepository):
            async def get_source_ids_for_user(self, user_id):
                return set()

        with pytest.raises(TypeError):
            Incomplete()  # type: ignore[abstract]

    def test_complete_subclass_is_instantiable(self):
        from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository

        class Complete(BlockedSourcesRepository):
            async def get_source_ids_for_user(self, user_id):
                return set()

            async def add(self, user_id, source_id):
                return None

            async def remove(self, user_id, source_id):
                return None

            async def list_for_user(self, user_id):
                return []

        assert isinstance(Complete(), BlockedSourcesRepository)


class TestBlockedSourceValueObject:
    """The port returns BlockedSource value objects from list_for_user."""

    def test_importable(self):
        from src.domain.value_objects.blocked_source import BlockedSource

        assert BlockedSource is not None

    def test_has_source_id_and_blocked_at(self):
        import uuid
        from datetime import datetime, timezone

        from src.domain.value_objects.blocked_source import BlockedSource

        src = uuid.uuid4()
        now = datetime.now(timezone.utc)
        bs = BlockedSource(source_id=src, blocked_at=now)
        assert bs.source_id == src
        assert bs.blocked_at == now
