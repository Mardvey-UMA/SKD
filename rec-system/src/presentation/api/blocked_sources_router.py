"""FastAPI router for /users/{user_id}/blocked-sources endpoints (Phase 1 user-sources).

All mutations are idempotent: POST returns 204 both for new rows and for
duplicates; DELETE returns 204 for both existing rows and rows that were
never there. Callers can safely retry without worrying about 409/404.
"""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends
from fastapi.responses import Response

from src.domain.interfaces.blocked_sources_repository import BlockedSourcesRepository
from src.presentation.schemas.blocked_sources import (
    BlockedSourceItemSchema,
    BlockedSourcesListResponseSchema,
    BlockSourceRequestSchema,
)

blocked_sources_router = APIRouter()


async def get_blocked_sources_repository() -> BlockedSourcesRepository:
    """Dependency provider for BlockedSourcesRepository.

    In production this is overridden by the DI container.
    """
    raise NotImplementedError("DI container must override get_blocked_sources_repository")


@blocked_sources_router.post(
    "/users/{user_id}/blocked-sources",
    status_code=204,
)
async def block_source(
    user_id: UUID,
    request: BlockSourceRequestSchema,
    repo: BlockedSourcesRepository = Depends(get_blocked_sources_repository),
) -> Response:
    """Block a source for a user. Idempotent (204 for new or duplicate)."""
    await repo.add(user_id, request.source_id)
    return Response(status_code=204)


@blocked_sources_router.delete(
    "/users/{user_id}/blocked-sources/{source_id}",
    status_code=204,
)
async def unblock_source(
    user_id: UUID,
    source_id: UUID,
    repo: BlockedSourcesRepository = Depends(get_blocked_sources_repository),
) -> Response:
    """Unblock a source for a user. Idempotent (204 for present or missing)."""
    await repo.remove(user_id, source_id)
    return Response(status_code=204)


@blocked_sources_router.get(
    "/users/{user_id}/blocked-sources",
    response_model=BlockedSourcesListResponseSchema,
)
async def list_blocked_sources(
    user_id: UUID,
    repo: BlockedSourcesRepository = Depends(get_blocked_sources_repository),
) -> BlockedSourcesListResponseSchema:
    """Return all blocked sources for a user. No pagination in Phase 1."""
    items = await repo.list_for_user(user_id)
    return BlockedSourcesListResponseSchema(
        items=[
            BlockedSourceItemSchema(source_id=bs.source_id, blocked_at=bs.blocked_at)
            for bs in items
        ],
        count=len(items),
    )
