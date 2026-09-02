from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from uuid import UUID


@dataclass
class RawContent:
    id: UUID                          # UUID (from parser's raw_content table)
    title: str                        # extracted from raw_data->>'title'
    content_body: str                 # extracted from raw_data->>'content'
    source_type: str                  # column source_type
    external_id: str                  # column external_id
    published_at: Optional[datetime]  # extracted from raw_data->>'publishedAt'

    @property
    def text_for_normalization(self) -> str:
        """Concatenate title and body for dedup processing."""
        return f"{self.title}\n{self.content_body}"
