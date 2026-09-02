"""FastAPI router for POST /recommendations and GET /recommendations/cold-start endpoints."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, Query
from fastapi.responses import JSONResponse

from src.application.dto.recommendations import RecommendationsRequest
from src.application.use_cases.get_recommendations import (
    GetRecommendationsUseCase,
    UserProfileNotFoundError,
)
from src.application.use_cases.get_cold_start import GetColdStartUseCase
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.presentation.dependencies.user_profile_guard import (
    ensure_user_profile_exists,
    get_user_profile_repository,
)
from src.presentation.schemas.recommendations import (
    FeedItemDetail,
    RecommendationsRequestSchema,
    RecommendationsResponseSchema,
    ColdStartResponseSchema,
)
from src.presentation.schemas.onboarding import ErrorResponseSchema

recommendations_router = APIRouter()


async def get_recommendations_use_case() -> GetRecommendationsUseCase:
    """Dependency provider for GetRecommendationsUseCase.

    In production this is overridden by the DI container.
    """
    raise NotImplementedError("DI container must override get_recommendations_use_case")


async def get_cold_start_use_case() -> GetColdStartUseCase:
    """Dependency provider for GetColdStartUseCase.

    In production this is overridden by the DI container.
    """
    raise NotImplementedError("DI container must override get_cold_start_use_case")


@recommendations_router.post("/recommendations", response_model=RecommendationsResponseSchema)
async def get_recommendations(
    request: RecommendationsRequestSchema,
    include_breakdown: bool = Query(False, description="Return per-item scoring breakdown alongside items"),
    use_case: GetRecommendationsUseCase = Depends(get_recommendations_use_case),
    profile_repo: UserProfileRepository = Depends(get_user_profile_repository),
):
    """Generate personalized recommendations for the given user.

    Guarantees a rec_profiles row exists (auto-creates cold_start profile if missing).
    Returns ordered UUIDs without scores. History exclusion handled internally.

    Phase 1 user-sources: optional include_source_ids / exclude_source_ids and
    ranking_mode fields; NARROW mode requires a non-empty include_source_ids.

    Raises:
        400: NARROW ranking_mode without include_source_ids.
        404: If no recommendation profile exists for the user (should not happen after guard).
        503: If model inference queue is full (future).
    """
    # NARROW requires non-empty include_source_ids (locked decision, design §7.4).
    if request.ranking_mode == "NARROW" and not request.include_source_ids:
        return JSONResponse(
            status_code=400,
            content={
                "error": "narrow_mode_requires_include_source_ids",
                "message": "ranking_mode=NARROW requires a non-empty include_source_ids list",
            },
        )

    await ensure_user_profile_exists(user_id=request.user_id, profile_repo=profile_repo)

    dto = RecommendationsRequest(
        user_id=request.user_id,
        count=request.count,
        include_source_ids=request.include_source_ids,
        exclude_source_ids=request.exclude_source_ids,
        ranking_mode=request.ranking_mode.value if hasattr(request.ranking_mode, "value") else str(request.ranking_mode),
        include_breakdown=include_breakdown,
    )
    try:
        result = await use_case.execute(dto)
    except UserProfileNotFoundError as exc:
        error_body = ErrorResponseSchema(
            error="user_not_found",
            user_id=exc.user_id,
            message="No recommendation profile exists for this user",
        )
        return JSONResponse(status_code=404, content=error_body.model_dump(mode="json"))

    # Build optional items_detailed from DTO list-of-dicts → list-of-FeedItemDetail
    items_detailed = None
    if result.items_detailed:
        items_detailed = [FeedItemDetail.model_validate(d) for d in result.items_detailed]

    schema = RecommendationsResponseSchema(
        user_id=result.user_id,
        items=result.items,
        count=result.count,
        generated_at=result.generated_at,
        items_detailed=items_detailed,
        latency_breakdown=result.latency_breakdown,
        profile_snapshot=result.profile_snapshot,
        feature_flags=result.feature_flags,
    )
    return JSONResponse(content=schema.model_dump(mode="json", exclude_none=True))


@recommendations_router.get(
    "/recommendations/cold-start",
    response_model=ColdStartResponseSchema,
)
async def get_cold_start(
    count: int = 30,
    use_case: GetColdStartUseCase = Depends(get_cold_start_use_case),
) -> ColdStartResponseSchema:
    """Return a pre-computed trending list for cold-start or anonymous users.

    Args:
        count: Number of items to return (default 30, max 50).
        use_case: GetColdStartUseCase injected via DI.

    Returns:
        ColdStartResponseSchema with items, count, and generated_at.
    """
    capped_count = min(count, 50)
    result = await use_case.execute(count=capped_count)
    return ColdStartResponseSchema(
        items=result.items,
        count=result.count,
        generated_at=result.generated_at,
    )
