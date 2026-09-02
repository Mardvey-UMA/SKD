from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Category:
    """Reference entity for an NLP topic / onboarding category.

    id is the canonical topic key used across TopicVector, NLI classifier,
    and the onboarding UI.  It is identical to the display name in Russian.
    """

    id: str
    name: str
    icon: str
    sort_order: int
    is_active: bool = field(default=True)
