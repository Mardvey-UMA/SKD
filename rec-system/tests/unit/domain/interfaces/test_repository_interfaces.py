"""Failing tests for domain repository ABC interfaces (Task 17)."""

import inspect
import pytest

from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.domain.interfaces.entity_interest_repository import EntityInterestRepository
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.interfaces.config_repository import ConfigRepository


class TestContentRepository:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            ContentRepository()  # type: ignore[abstract]

    def test_has_get_unprocessed_method(self):
        assert hasattr(ContentRepository, "get_unprocessed")
        method = getattr(ContentRepository, "get_unprocessed")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "batch_size" in params

    def test_has_save_features_method(self):
        assert hasattr(ContentRepository, "save_features")
        method = getattr(ContentRepository, "save_features")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "features" in params

    def test_has_get_candidates_by_freshness_method(self):
        assert hasattr(ContentRepository, "get_candidates_by_freshness")
        method = getattr(ContentRepository, "get_candidates_by_freshness")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "max_age_hours" in params
        assert "limit" in params

    def test_has_get_candidates_by_embedding_method(self):
        assert hasattr(ContentRepository, "get_candidates_by_embedding")
        method = getattr(ContentRepository, "get_candidates_by_embedding")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "embedding" in params
        assert "limit" in params

    def test_has_get_by_ids_method(self):
        assert hasattr(ContentRepository, "get_by_ids")
        method = getattr(ContentRepository, "get_by_ids")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "post_ids" in params

    def test_has_get_dedup_clusters_method(self):
        assert hasattr(ContentRepository, "get_dedup_clusters")
        method = getattr(ContentRepository, "get_dedup_clusters")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "post_ids" in params

    def test_has_get_related_pairs_method(self):
        assert hasattr(ContentRepository, "get_related_pairs")
        method = getattr(ContentRepository, "get_related_pairs")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "post_ids" in params

    def test_has_get_published_ids_method(self):
        assert hasattr(ContentRepository, "get_published_ids")
        method = getattr(ContentRepository, "get_published_ids")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "content_ids" in params


class TestUserProfileRepository:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            UserProfileRepository()  # type: ignore[abstract]

    def test_has_get_by_user_id_method(self):
        assert hasattr(UserProfileRepository, "get_by_user_id")
        method = getattr(UserProfileRepository, "get_by_user_id")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "user_id" in params

    def test_has_save_method(self):
        assert hasattr(UserProfileRepository, "save")
        method = getattr(UserProfileRepository, "save")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "profile" in params


class TestEntityInterestRepository:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            EntityInterestRepository()  # type: ignore[abstract]

    def test_has_get_top_for_user_method(self):
        assert hasattr(EntityInterestRepository, "get_top_for_user")
        method = getattr(EntityInterestRepository, "get_top_for_user")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "user_id" in params
        assert "limit" in params

    def test_has_upsert_method(self):
        assert hasattr(EntityInterestRepository, "upsert")
        method = getattr(EntityInterestRepository, "upsert")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "interest" in params

    def test_has_decay_and_cleanup_methods(self):
        assert hasattr(EntityInterestRepository, "decay_all_for_user")
        assert hasattr(EntityInterestRepository, "cleanup_expired")
        decay_sig = inspect.signature(getattr(EntityInterestRepository, "decay_all_for_user"))
        decay_params = list(decay_sig.parameters)
        assert "user_id" in decay_params
        assert "decay_factor" in decay_params
        cleanup_sig = inspect.signature(getattr(EntityInterestRepository, "cleanup_expired"))
        cleanup_params = list(cleanup_sig.parameters)
        assert "user_id" in cleanup_params
        assert "cleanup_days" in cleanup_params


class TestInteractionRepository:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            InteractionRepository()  # type: ignore[abstract]

    def test_has_get_unprocessed_for_user_method(self):
        assert hasattr(InteractionRepository, "get_unprocessed_for_user")
        method = getattr(InteractionRepository, "get_unprocessed_for_user")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "user_id" in params

    def test_has_mark_processed_method(self):
        assert hasattr(InteractionRepository, "mark_processed")
        method = getattr(InteractionRepository, "mark_processed")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "interaction_ids" in params

    def test_has_get_unprocessed_method(self):
        assert hasattr(InteractionRepository, "get_unprocessed")
        method = getattr(InteractionRepository, "get_unprocessed")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "limit" in params


class TestConfigRepository:
    def test_is_abstract(self):
        with pytest.raises(TypeError):
            ConfigRepository()  # type: ignore[abstract]

    def test_has_get_config_method(self):
        assert hasattr(ConfigRepository, "get_config")
        method = getattr(ConfigRepository, "get_config")
        sig = inspect.signature(method)
        params = list(sig.parameters)
        assert "key" in params


