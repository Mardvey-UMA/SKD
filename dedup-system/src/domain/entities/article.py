from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from uuid import UUID

from src.domain.value_objects.content_hash import ContentHash
from src.domain.value_objects.embedding import Embedding


@dataclass
class Article:
    id: Optional[int]
    raw_content_id: UUID
    content_hash: ContentHash
    normalized_text: str
    embedding: Optional[Embedding]
    source: Optional[str]
    created_at: Optional[datetime]

    @property
    def has_embedding(self) -> bool:
        return self.embedding is not None
