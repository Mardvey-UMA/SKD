"""DB helpers for the evaluation harness.

reset_eval_user() clears all per-user rows written by simulation so each
benchmark combination starts with a clean slate.  Guarded by the same
safety check as BehaviorSimulator: refuses to run against prod DBs unless
REC_ALLOW_EVAL_IN_PROD is explicitly set.
"""
from __future__ import annotations

import os
from uuid import UUID

from sqlalchemy import text


class EvalSafetyError(Exception):
    """Raised when reset is attempted against a production database."""


async def reset_eval_user(user_id: UUID, session_factory) -> None:
    """Delete all eval-generated rows for user_id.

    Clears: recommendation_history, rec_entity_interests, user_interactions,
    rec_profiles (in FK-safe order).

    Safety: refuses to run if DATABASE_URL contains 'prod' and
    REC_ALLOW_EVAL_IN_PROD is not set.
    """
    db_url = os.environ.get("DATABASE_URL", "")
    if "prod" in db_url and not os.environ.get("REC_ALLOW_EVAL_IN_PROD"):
        raise EvalSafetyError(
            "DATABASE_URL contains 'prod' and REC_ALLOW_EVAL_IN_PROD is not set. "
            "Refusing to reset eval user in production database."
        )

    async with session_factory() as session:
        await session.execute(
            text("DELETE FROM data_flow.recommendation_history WHERE user_id = :uid"),
            {"uid": user_id},
        )
        await session.execute(
            text("DELETE FROM data_flow.rec_entity_interests WHERE user_id = :uid"),
            {"uid": user_id},
        )
        await session.execute(
            text("DELETE FROM data_flow.user_interactions WHERE user_id = :uid"),
            {"uid": user_id},
        )
        await session.execute(
            text("DELETE FROM data_flow.rec_profiles WHERE user_id = :uid"),
            {"uid": user_id},
        )
        await session.commit()
