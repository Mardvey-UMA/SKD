"""BehaviorSimulator: turns a Persona into deterministic synthetic DB state.

Driven by persona interaction patterns and a seed for the PRNG; given the same
(persona, user_id, seed) pair two runs produce byte-identical interactions and
identical profile snapshots.
"""
from __future__ import annotations

import os
import random
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
from typing import Any
from uuid import UUID

from src.application.dto.onboard import OnboardRequest
from src.application.dto.update_profile import UpdateProfileCommand
from src.application.use_cases.onboard_user import OnboardUserUseCase
from src.application.use_cases.update_profile import UpdateProfileUseCase
from src.domain.entities.content_features import ContentFeatures
from src.domain.entities.user_interaction import UserInteraction
from src.domain.entities.user_profile import UserProfile
from src.domain.interfaces.content_repository import ContentRepository
from src.domain.interfaces.interaction_repository import InteractionRepository
from src.domain.interfaces.user_profile_repository import UserProfileRepository
from tests.evaluation.models import Persona

# Synthetic timeline: each step is 90 seconds after a fixed epoch.
_EVAL_EPOCH = datetime(2024, 1, 1, 0, 0, 0, tzinfo=timezone.utc)
_STEP_INTERVAL_SECONDS = 90

# Probability of sampling from dislike topics to generate DISLIKE/skip events.
# Kept at 3% so the dislike-topic share stays well below the 5% test threshold
# even for short sessions (30 interactions), while still producing negative signals.
_DISLIKE_SAMPLE_PROB = 0.03


class EvalSafetyError(Exception):
    """Raised when the simulator detects a production database URL."""


class SimulatorCorpusError(Exception):
    """Raised when the corpus contains no posts matching the persona's interests."""


@dataclass
class SimulatedUserState:
    user_id: UUID
    persona_id: str
    seed: int
    created_at: datetime
    onboarding_categories: list[str]
    interactions: list[UserInteraction]
    profile_snapshot: UserProfile
    sampled_post_ids: list[UUID]


class BehaviorSimulator:
    """Simulates deterministic user behaviour for a given Persona.

    Injects a real user profile + interactions into the database via the
    injected repositories, then triggers profile update via UpdateProfileUseCase.
    All randomness flows through a seeded random.Random instance so runs are
    reproducible across processes given the same (persona, user_id, seed).
    """

    def __init__(
        self,
        content_repo: ContentRepository,
        profile_repo: UserProfileRepository,
        interactions_repo: InteractionRepository,
        signal_classifier: Any,
        update_profile_use_case: UpdateProfileUseCase,
        onboard_use_case: OnboardUserUseCase,
    ) -> None:
        self._content_repo = content_repo
        self._profile_repo = profile_repo
        self._interactions_repo = interactions_repo
        self._signal_classifier = signal_classifier
        self._update_profile_use_case = update_profile_use_case
        self._onboard_use_case = onboard_use_case

    async def simulate(
        self,
        persona: Persona,
        user_id: UUID,
        seed: int = 42,
    ) -> SimulatedUserState:
        """Simulate persona behaviour and persist state to the DB.

        Safety: refuses to run if DATABASE_URL contains 'prod' and
        REC_ALLOW_EVAL_IN_PROD env var is not set.
        """
        self._check_safety()

        created_at = datetime.now(timezone.utc)

        # Step 1: top-3 onboarding categories by weight
        # (pad from canonical topics if persona.base_interests has < 3 keys)
        onboarding_categories = _top_categories(
            persona.base_interests, n=3, dislikes=persona.dislikes
        )

        # Step 2: onboard — creates rec_profiles row
        await self._onboard_use_case.execute(
            OnboardRequest(user_id=user_id, categories=onboarding_categories)
        )

        # Cold-start persona: no interactions requested
        if persona.pattern.interactions_per_session == 0:
            profile = await self._profile_repo.get_by_user_id(user_id)
            return SimulatedUserState(
                user_id=user_id,
                persona_id=persona.id,
                seed=seed,
                created_at=created_at,
                onboarding_categories=onboarding_categories,
                interactions=[],
                profile_snapshot=profile,
                sampled_post_ids=[],
            )

        # Step 3: load corpus
        corpus = await self._content_repo.get_corpus_sample(limit=1000)
        if not corpus:
            raise SimulatorCorpusError(
                f"Corpus is empty — cannot simulate persona '{persona.id}'"
            )

        all_corpus_topics = {
            t
            for post in corpus
            for t in (post.topic_1, post.topic_2, post.topic_3)
            if t is not None
        }
        interest_topics = set(persona.base_interests.keys())
        if not interest_topics.intersection(all_corpus_topics):
            raise SimulatorCorpusError(
                f"No corpus posts match any interest of persona '{persona.id}'. "
                f"Interests: {interest_topics}. Corpus topics: {all_corpus_topics}"
            )

        # Step 4: simulate interactions
        rng = random.Random(seed)
        interactions: list[UserInteraction] = []
        sampled_post_ids: list[UUID] = []

        n = persona.pattern.interactions_per_session
        for step in range(n):
            # Determine interest distribution (with optional drift)
            if (
                persona.drift_after_interactions > 0
                and step >= persona.drift_after_interactions
                and persona.drift_phase2
            ):
                current_interests = persona.drift_phase2
            else:
                current_interests = persona.base_interests

            # 15% chance: sample from dislike topics to generate negative signals
            use_dislike_sample = (
                rng.random() < _DISLIKE_SAMPLE_PROB and bool(persona.dislikes)
            )

            if use_dislike_sample:
                post = _sample_dislike_post(rng, corpus, persona)
            else:
                post = _sample_interest_post(rng, corpus, current_interests)

            sampled_post_ids.append(post.post_id)

            interaction = _build_interaction(rng, persona, post, step, user_id, current_interests)
            interactions.append(interaction)

        # Step 5: persist interactions
        await self._interactions_repo.save_batch(interactions)

        # Step 6: run profile update (classifies signals, applies EMA)
        await self._update_profile_use_case.execute(
            UpdateProfileCommand(user_id=user_id)
        )

        # Step 7: read back final profile
        profile = await self._profile_repo.get_by_user_id(user_id)

        return SimulatedUserState(
            user_id=user_id,
            persona_id=persona.id,
            seed=seed,
            created_at=created_at,
            onboarding_categories=onboarding_categories,
            interactions=interactions,
            profile_snapshot=profile,
            sampled_post_ids=sampled_post_ids,
        )

    @staticmethod
    def _check_safety() -> None:
        db_url = os.environ.get("DATABASE_URL", "")
        if "prod" in db_url and not os.environ.get("REC_ALLOW_EVAL_IN_PROD"):
            raise EvalSafetyError(
                "DATABASE_URL contains 'prod' and REC_ALLOW_EVAL_IN_PROD is not set. "
                "Refusing to write eval data to a production database."
            )


# ---------------------------------------------------------------------------
# Pure helpers (no state, testable in isolation)
# ---------------------------------------------------------------------------


# 18 canonical NLP topic categories (data_flow.categories seed in migration 008).
# Used to pad onboarding for narrow personas whose base_interests has < 3 keys
# (OnboardUserUseCase enforces min_topics=3 per rec_config.onboarding_params).
_CANONICAL_TOPICS: tuple[str, ...] = (
    "политика", "экономика", "технологии", "наука", "спорт", "культура",
    "общество", "происшествия", "международные новости", "бизнес",
    "финансы", "образование", "здоровье", "развлечения", "криминал",
    "армия", "природа", "транспорт",
)


def _top_categories(
    interests: dict[str, float],
    n: int,
    dislikes: dict[str, float] | None = None,
) -> list[str]:
    """Return the top-n category names sorted by weight descending.

    If `interests` has fewer than n entries, pad from the canonical 18-topic list,
    skipping any in `dislikes`. Keeps narrow personas (e.g. sports-fan with a
    single 'спорт' interest) compatible with the onboarding min_topics=3 rule
    without polluting their primary signal.
    """
    chosen = [cat for cat, _ in sorted(interests.items(), key=lambda x: x[1], reverse=True)[:n]]
    if len(chosen) >= n:
        return chosen
    existing = set(chosen)
    avoid = set(dislikes or ())
    for cat in _CANONICAL_TOPICS:
        if len(chosen) >= n:
            break
        if cat in existing or cat in avoid:
            continue
        chosen.append(cat)
    return chosen


def _weighted_choice(rng: random.Random, corpus: list[ContentFeatures], weights: list[float]) -> ContentFeatures:
    """Manual weighted choice using rng.random() for full cross-version determinism."""
    total = sum(weights)
    if total <= 0:
        return rng.choice(corpus)
    r = rng.random() * total
    cumsum = 0.0
    for post, w in zip(corpus, weights):
        cumsum += w
        if r <= cumsum:
            return post
    return corpus[-1]


def _sample_interest_post(
    rng: random.Random,
    corpus: list[ContentFeatures],
    current_interests: dict[str, float],
) -> ContentFeatures:
    weights = [
        sum(
            current_interests.get(t, 0.0) * s
            for t, s in (
                (post.topic_1, post.topic_1_score or 0.0),
                (post.topic_2, post.topic_2_score or 0.0),
                (post.topic_3, post.topic_3_score or 0.0),
            )
            if t is not None
        )
        for post in corpus
    ]
    return _weighted_choice(rng, corpus, weights)


def _sample_dislike_post(
    rng: random.Random,
    corpus: list[ContentFeatures],
    persona: Persona,
) -> ContentFeatures:
    dislike_posts = [p for p in corpus if p.topic_1 and p.topic_1 in persona.dislikes]
    if dislike_posts:
        return rng.choice(dislike_posts)
    return rng.choice(corpus)


def _build_interaction(
    rng: random.Random,
    persona: Persona,
    post: ContentFeatures,
    step: int,
    user_id: UUID,
    current_interests: dict[str, float],
) -> UserInteraction:
    # XOR with user_id ensures uniqueness across users even with the same seed,
    # preventing ON CONFLICT DO NOTHING from silently dropping interactions in tests.
    event_id = uuid.UUID(int=(rng.getrandbits(128) ^ int(user_id)))
    occurred_at = _EVAL_EPOCH + timedelta(seconds=step * _STEP_INTERVAL_SECONDS)

    post_top_topic = post.topic_1
    dislike_weight = persona.dislikes.get(post_top_topic, 0.0) if post_top_topic else 0.0
    is_disliked = dislike_weight >= 0.5

    interest_weight = current_interests.get(post_top_topic, 0.0) if post_top_topic else 0.0
    is_interest = interest_weight > 0.05

    if is_disliked:
        r = rng.random()
        cum_dislike = persona.pattern.dislike_probability_on_dislike
        cum_skip = cum_dislike + persona.pattern.skip_probability_on_dislike
        if r < cum_dislike:
            event_type = "DISLIKE"
            duration_ms = int(400 + rng.random() * 600)
            scroll_pct = round(rng.uniform(0.05, 0.15), 3)
        elif r < cum_skip:
            event_type = "IMPRESSION"
            duration_ms = int(600 + rng.random() * 400)
            scroll_pct = round(rng.uniform(0.05, 0.20), 3)
        else:
            event_type = "IMPRESSION"
            duration_ms = int(1000 + rng.random() * 1000)
            scroll_pct = round(rng.uniform(0.10, 0.30), 3)
    elif is_interest:
        r = rng.random()
        cum_bm = persona.pattern.bookmark_probability_on_interest
        cum_like = cum_bm + persona.pattern.like_probability_on_interest
        if r < cum_bm:
            event_type = "BOOKMARK"
            duration_ms = int(persona.pattern.avg_dwell_seconds * 1000 * rng.uniform(1.2, 2.0))
            scroll_pct = round(min(persona.pattern.scroll_depth_p50 * rng.uniform(0.9, 1.1), 1.0), 3)
        elif r < cum_like:
            event_type = "LIKE"
            duration_ms = int(persona.pattern.avg_dwell_seconds * 1000 * rng.uniform(0.8, 1.5))
            scroll_pct = round(min(persona.pattern.scroll_depth_p50 * rng.uniform(0.8, 1.2), 1.0), 3)
        else:
            event_type = "IMPRESSION"
            duration_ms = int(persona.pattern.avg_dwell_seconds * 1000 * rng.uniform(0.5, 1.5))
            scroll_pct = round(min(persona.pattern.scroll_depth_p50 * rng.uniform(0.6, 1.1), 1.0), 3)
    else:
        # Neutral post
        event_type = "IMPRESSION"
        duration_ms = int(persona.pattern.avg_dwell_seconds * 500 * rng.uniform(0.3, 0.8))
        scroll_pct = round(rng.uniform(0.1, 0.4), 3)

    return UserInteraction(
        id=0,
        event_id=event_id,
        user_id=str(user_id),
        post_id=post.post_id,  # type annotation says int, actual DB column is UUID
        event_type=event_type,
        duration_ms=duration_ms,
        scroll_pct=scroll_pct,
        max_scroll_pct=scroll_pct,
        created_at=occurred_at,
        processed=False,
    )
