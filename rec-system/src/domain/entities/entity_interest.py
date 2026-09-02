from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass
class EntityInterest:
    """Represents a user's interest in a named entity (person, organisation, location).

    Identity is composite: user_id + entity_type + entity_name.
    All mutation methods return a new instance (immutable value).
    """

    user_id: str
    entity_type: str  # 'person' | 'organization' | 'location'
    entity_name: str
    weight: float
    last_seen: datetime

    def apply_decay(self, decay_factor: float) -> EntityInterest:
        """Return a new EntityInterest with weight multiplied by *decay_factor*."""
        return EntityInterest(
            user_id=self.user_id,
            entity_type=self.entity_type,
            entity_name=self.entity_name,
            weight=self.weight * decay_factor,
            last_seen=self.last_seen,
        )

    def increment_weight(self) -> EntityInterest:
        """Return a new EntityInterest with weight += 1 and last_seen = now (UTC)."""
        return EntityInterest(
            user_id=self.user_id,
            entity_type=self.entity_type,
            entity_name=self.entity_name,
            weight=self.weight + 1.0,
            last_seen=datetime.now(timezone.utc),
        )

    def is_expired(self, cleanup_days: int) -> bool:
        """Return True when last_seen is older than *cleanup_days* ago."""
        now = datetime.now(timezone.utc)
        last_seen = self.last_seen
        if last_seen.tzinfo is None:
            last_seen = last_seen.replace(tzinfo=timezone.utc)
        age_days = (now - last_seen).days
        return age_days > cleanup_days
