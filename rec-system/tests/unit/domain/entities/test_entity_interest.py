import pytest
from datetime import datetime, timedelta, timezone

from src.domain.entities.entity_interest import EntityInterest


class TestEntityInterest:
    def test_create(self) -> None:
        now = datetime.now(timezone.utc)
        interest = EntityInterest(
            user_id="user-1",
            entity_type="person",
            entity_name="Elon Musk",
            weight=2.5,
            last_seen=now,
        )
        assert interest.user_id == "user-1"
        assert interest.entity_type == "person"
        assert interest.entity_name == "Elon Musk"
        assert interest.weight == 2.5
        assert interest.last_seen == now

    def test_entity_types(self) -> None:
        now = datetime.now(timezone.utc)
        for entity_type in ("person", "organization", "location"):
            interest = EntityInterest(
                user_id="user-1",
                entity_type=entity_type,
                entity_name="Test Entity",
                weight=1.0,
                last_seen=now,
            )
            assert interest.entity_type == entity_type

    def test_apply_decay(self) -> None:
        now = datetime.now(timezone.utc)
        interest = EntityInterest(
            user_id="user-1",
            entity_type="person",
            entity_name="Elon Musk",
            weight=2.0,
            last_seen=now,
        )
        decayed = interest.apply_decay(decay_factor=0.95)
        assert abs(decayed.weight - 1.9) < 1e-9
        # original is unchanged
        assert interest.weight == 2.0
        # composite identity preserved
        assert decayed.user_id == interest.user_id
        assert decayed.entity_type == interest.entity_type
        assert decayed.entity_name == interest.entity_name
        assert decayed.last_seen == interest.last_seen

    def test_increment_weight(self) -> None:
        old_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
        interest = EntityInterest(
            user_id="user-1",
            entity_type="organization",
            entity_name="Tesla",
            weight=1.5,
            last_seen=old_time,
        )
        before = datetime.now(timezone.utc)
        updated = interest.increment_weight()
        after = datetime.now(timezone.utc)

        assert abs(updated.weight - 2.5) < 1e-9
        assert updated.last_seen >= before
        assert updated.last_seen <= after
        # original unchanged
        assert interest.weight == 1.5
        assert interest.last_seen == old_time

    def test_is_expired(self) -> None:
        now = datetime.now(timezone.utc)
        old_time = now - timedelta(days=31)
        recent_time = now - timedelta(days=10)

        expired_interest = EntityInterest(
            user_id="user-1",
            entity_type="location",
            entity_name="Moscow",
            weight=0.5,
            last_seen=old_time,
        )
        fresh_interest = EntityInterest(
            user_id="user-1",
            entity_type="location",
            entity_name="Moscow",
            weight=0.5,
            last_seen=recent_time,
        )

        assert expired_interest.is_expired(cleanup_days=30) is True
        assert fresh_interest.is_expired(cleanup_days=30) is False
