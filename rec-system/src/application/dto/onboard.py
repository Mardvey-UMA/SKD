from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional
from uuid import UUID


@dataclass
class OnboardRequest:
    user_id: UUID
    categories: List[str]
    source_content_ids: Optional[List[UUID]] = None
    # Legacy field kept for backward compatibility with existing tests
    chosen_topics: Optional[List[str]] = None


@dataclass
class OnboardResponse:
    user_id: UUID
    profile_initialized: bool
    # Legacy field kept for backward compatibility
    status: str = "ok"
