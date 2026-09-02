"""Dev-only POST /recommendations/explain endpoint (env-gated).

IMPORTANT: This endpoint is for development and debugging only.
Never expose it through api-gateway or in a production environment.
Gate: REC_EXPLAIN_ENABLED=true (default OFF).
"""
from __future__ import annotations

import logging
import os

from fastapi import APIRouter, Depends, HTTPException

from src.application.services.scoring_explainer import NotFoundError, ScoringExplainer
from src.presentation.schemas.explain import ExplainRequest, ExplainResponse

logger = logging.getLogger(__name__)
_explain_warned = False

explain_router = APIRouter(prefix="/recommendations", tags=["explain"])


async def get_explainer() -> ScoringExplainer:
    """Dependency provider for ScoringExplainer. Overridden by DI container in production."""
    raise NotImplementedError("DI container must override get_explainer")


@explain_router.post("/explain", response_model=ExplainResponse)
async def explain(
    body: ExplainRequest,
    explainer: ScoringExplainer = Depends(get_explainer),
) -> ExplainResponse:
    """Return per-component scoring breakdown for a (user, post) pair. Dev only."""
    if os.getenv("REC_EXPLAIN_ENABLED", "false").lower() != "true":
        raise HTTPException(status_code=403, detail="Explainability endpoint disabled")

    global _explain_warned
    if not _explain_warned:
        logger.warning("EXPLAIN endpoint invoked — make sure this is not a production environment")
        _explain_warned = True

    try:
        result = await explainer.explain(body.user_id, body.content_id)
    except NotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc))

    return ExplainResponse.from_explanation(result)
