from abc import ABC, abstractmethod
from uuid import UUID

from src.domain.entities.raw_content import RawContent


class RawContentRepositoryPort(ABC):
    @abstractmethod
    def fetch_pending_batch(self, batch_size: int) -> list[RawContent]:
        """Fetch unprocessed rows from parser's raw_content with JSONB extraction."""
        ...

    @abstractmethod
    def mark_processed(self, ids: list[UUID]) -> None:
        """Set is_processed_by_dedup = true for given IDs."""
        ...
