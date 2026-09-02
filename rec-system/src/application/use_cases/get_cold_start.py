"""GetColdStartUseCase application use case."""
from __future__ import annotations

import json
from datetime import datetime, timezone
from uuid import UUID

from src.application.dto.cold_start import ColdStartResponse
from src.domain.interfaces.cache_client import CacheClient
from src.domain.interfaces.content_repository import ContentRepository

COLD_START_CACHE_KEY = "rec:cold-start:trending"
MAX_COUNT = 50
DEFAULT_COUNT = 30


class GetColdStartUseCase:
    """Returns a pre-computed trending list for cold-start users.

    Checks Redis cache first; falls back to querying published_content by recency.
    Implements GET /recommendations/cold-start from design/08-api-contracts.md.
    """

    def __init__(
        self,
        cache_client: CacheClient,
        content_repo: ContentRepository,
    ) -> None:
        self._cache_client = cache_client
        self._content_repo = content_repo

    async def execute(self, count: int = DEFAULT_COUNT) -> ColdStartResponse:
        """Return trending content items for cold-start users.

        Args:
            count: Number of items to return. Capped at 50.

        Returns:
            ColdStartResponse with items (UUIDs), count, and generated_at.
        """
        count = min(count, MAX_COUNT)

        cached = await self._cache_client.get(COLD_START_CACHE_KEY)
        if cached is not None:
            data = json.loads(cached)
            all_items = [UUID(item_id) for item_id in data["items"]]
            items = all_items[:count]
            generated_at = datetime.fromisoformat(data["generated_at"])
            return ColdStartResponse(
                items=items,
                count=len(items),
                generated_at=generated_at,
            )

        # Cache miss: query published_content and populate cache
        all_ids = await self._content_repo.get_recent_published_ids(limit=MAX_COUNT)
        generated_at = datetime.now(timezone.utc)

        # Write to cache so next request is fast
        payload = json.dumps({
            "items": [str(item_id) for item_id in all_ids],
            "generated_at": generated_at.isoformat(),
        })
        await self._cache_client.set(COLD_START_CACHE_KEY, payload, ttl=3600)

        items = all_ids[:count]
        return ColdStartResponse(
            items=items,
            count=len(items),
            generated_at=generated_at,
        )
