"""FastAPI router for POST /onboarding endpoint."""
from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends

from src.application.dto.onboard import OnboardRequest
from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from src.presentation.dependencies.user_profile_guard import (
    ensure_user_profile_exists,
    get_user_profile_repository,
)
from src.presentation.schemas.onboarding import (
    OnboardingRequestSchema,
    OnboardingResponseSchema,
)

onboarding_router = APIRouter()


async def get_onboard_user_use_case() -> OnboardUserUseCase:
    """Dependency provider for OnboardUserUseCase.

    In production this is overridden by the DI container.
    """
    raise NotImplementedError("DI container must override get_onboard_user_use_case")


@onboarding_router.post("/onboarding", response_model=OnboardingResponseSchema)
async def onboard_user(
    request: OnboardingRequestSchema,
    use_case: OnboardUserUseCase = Depends(get_onboard_user_use_case),
    profile_repo: UserProfileRepository = Depends(get_user_profile_repository),
) -> OnboardingResponseSchema:
    """Onboard a user with their chosen categories.

    Guarantees a rec_profiles row exists (auto-creates cold_start profile if missing).
    Validates the request, updates the user profile with topic vector and optional
    embedding warmup, and publishes a Kafka event.

    Raises:
        422: If request validation fails (< 3 categories, > 10 source_content_ids).
    """
    await ensure_user_profile_exists(user_id=request.user_id, profile_repo=profile_repo)

    dto = OnboardRequest(
        user_id=request.user_id,
        categories=request.categories,
        source_content_ids=request.source_content_ids,
    )
    result = await use_case.execute(dto)

    return OnboardingResponseSchema(
        user_id=result.user_id,
        profile_initialized=result.profile_initialized,
    )
