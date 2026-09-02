"""FastAPI safety-net dependency: guarantee a rec_profiles row exists.

ensure_user_profile_exists checks whether a cold-start profile exists for
the given user_id, and creates one on the fly if missing.  This guards
against Kafka consumer lag in the auth→user-service→rec-system propagation
chain (BUG-011).

It is idempotent: concurrent calls for the same user_id hit the DB UNIQUE
constraint on rec_profiles.user_id; the resulting IntegrityError is caught
and the function falls back to reading the already-inserted row.
"""
from __future__ import annotations

import logging
from uuid import UUID

from sqlalchemy.exc import IntegrityError

from src.domain.factories.user_profile_factory import create_cold_start_profile
from src.domain.interfaces.user_profile_repository import UserProfileRepository

logger = logging.getLogger(__name__)


async def ensure_user_profile_exists(
    user_id: UUID,
    profile_repo: UserProfileRepository,
) -> UUID:
    """Guarantee that a rec_profiles row exists for user_id.

    If missing, creates a cold_start profile on the fly and logs a WARN
    (Kafka consumer lag suspected).  IntegrityError from concurrent inserts
    is caught idempotently.

    Args:
        user_id: The user whose profile must exist.
        profile_repo: Repository for reading/writing rec_profiles.

    Returns:
        user_id (unchanged) — convenient for use as a FastAPI dependency result.
    """
    existing = await profile_repo.get_by_user_id(user_id)
    if existing is not None:
        return user_id

    logger.warning(
        "rec_profile missing for user_id=%s — auto-creating cold_start fallback "
        "(Kafka consumer lag suspected)",
        user_id,
    )

    profile = create_cold_start_profile(user_id)
    try:
        await profile_repo.save(profile)
    except IntegrityError:
        # Concurrent request already created the row — read it to confirm existence.
        await profile_repo.get_by_user_id(user_id)

    return user_id


async def get_user_profile_repository() -> UserProfileRepository:
    """Dependency provider stub for UserProfileRepository.

    Overridden by the DI container in main.py via dependency_overrides.
    """
    raise NotImplementedError("DI container must override get_user_profile_repository")
