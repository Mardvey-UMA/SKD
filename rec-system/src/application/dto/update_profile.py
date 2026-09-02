from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional
from uuid import UUID


@dataclass
class UpdateProfileCommand:
    user_id: Optional[UUID] = None


@dataclass
class UpdateProfileResult:
    users_updated: int
    events_processed: int
