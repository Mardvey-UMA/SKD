"""LiveProfileService: blends stored profile embedding with a live-computed vector.

On each /recommendations request (when REC_FEATURE_LIVE_PROFILE=true), fetches the
user's N most-recent interactions, classifies them, and computes a time-decayed
weighted average of the corresponding post embeddings. The result is blended with the
stored EMA embedding and L2-normalised before being used for ANN candidate retrieval.
"""
from __future__ import annotations

import math
from datetime import datetime, timezone
from typing import TYPE_CHECKING
from uuid import UUID

from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.interfaces.live_profile_computer import LiveProfileComputer
from src.domain.services.signal_classifier import SignalClassifier

if TYPE_CHECKING:
    from src.domain.entities.user_profile import UserProfile


def _normalize_l2(vec: list[float]) -> list[float] | None:
    norm = math.sqrt(sum(v * v for v in vec))
    if norm < 1e-10:
        return None
    return [v / norm for v in vec]


class LiveProfileService(LiveProfileComputer):
    """Computes a live embedding blend from the user's recent positive interactions."""

    def __init__(
        self,
        interaction_repo: InteractionRepository,
        content_repo: ContentRepository,
        signal_classifier: SignalClassifier,
    ) -> None:
        self._interaction_repo = interaction_repo
        self._content_repo = content_repo
        self._signal_classifier = signal_classifier

    async def compute(
        self,
        user_id: UUID,
        stored_profile: "UserProfile",
        blend_alpha: float,
        recent_n: int,
        decay_hours: float = 24.0,
    ) -> list[float] | None:
        """Return blended embedding or None if there are no usable positive interactions.

        Algorithm:
        1. Fetch recent_n interactions (any type, any processed state).
        2. Classify → keep positives only.
        3. Weighted sum of post embeddings: weight = signal_strength * exp(-age/decay_hours).
        4. L2-normalise the weighted sum → live_avg.
        5. If stored profile has no/zero embedding → return live_avg.
        6. Otherwise blend: blend_alpha * stored + (1-blend_alpha) * live_avg, then normalise.
        """
        interactions = await self._interaction_repo.get_recent(user_id, limit=recent_n)
        if not interactions:
            return None

        signals = self._signal_classifier.classify_batch(interactions)
        positives = [s for s in signals if s.is_positive]
        if not positives:
            return None

        # Build post_id -> most-recent created_at (interactions are ordered DESC so first wins)
        post_id_to_ts: dict = {}
        for inter in interactions:
            pid = inter.post_id  # UUID at runtime despite int type annotation on entity
            if pid not in post_id_to_ts:
                post_id_to_ts[pid] = inter.created_at

        positive_post_ids = list({s.post_id for s in positives})
        features_list = await self._content_repo.get_by_ids(positive_post_ids)
        features_map = {f.post_id: f for f in features_list}

        now = datetime.now(timezone.utc)
        weighted_embs: list[tuple[float, list[float]]] = []

        for signal in positives:
            feat = features_map.get(signal.post_id)
            if feat is None or feat.embedding is None:
                continue
            ts = post_id_to_ts.get(signal.post_id)
            if ts is not None:
                if ts.tzinfo is None:
                    ts = ts.replace(tzinfo=timezone.utc)
                age_hours = max((now - ts).total_seconds() / 3600.0, 0.0)
            else:
                age_hours = 0.0
            time_weight = math.exp(-age_hours / max(decay_hours, 1e-6))
            weighted_embs.append((signal.weight * time_weight, feat.embedding))

        if not weighted_embs:
            return None

        dim = len(weighted_embs[0][1])
        live_raw = [0.0] * dim
        for w, emb in weighted_embs:
            for i, v in enumerate(emb):
                live_raw[i] += w * v

        live_avg_norm = _normalize_l2(live_raw)
        if live_avg_norm is None:
            return None

        stored_emb = stored_profile.embedding
        if stored_emb is None or all(v == 0.0 for v in stored_emb):
            return live_avg_norm

        blended = [
            blend_alpha * s + (1.0 - blend_alpha) * l
            for s, l in zip(stored_emb, live_avg_norm)
        ]
        return _normalize_l2(blended)
