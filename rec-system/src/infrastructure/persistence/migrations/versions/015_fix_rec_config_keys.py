"""Fix rec_config keys so that they match what application code actually reads.

Revision ID: 015
Revises: 014
Create Date: 2026-05-20

ROOT CAUSE
==========

Seed migration 007 inserted rec_config rows with **short** keys:
    'scoring_weights' = {topic, embedding, entity, sentiment, freshness, format}
    'ranking_params'  = {halflife_hours, freshness_limit, embedding_limit, streak, ratio, ...}
    'profile_params'  = {lr, entity_decay, cleanup_days, ...}
    'signal_weights'  = {flat dict of event_type -> weight}

But the application code reads **long** keys via different group names:

  src/application/use_cases/generate_feed.py
    line  97  await config_repo.get_config("feed")                  ← group key 'feed' (no row exists)
    line 133  config.get("max_age_hours", 48)
    line 134  config.get("freshness_candidate_limit", 500)
    line 135  config.get("embedding_candidate_limit", 500)
    line 289  config.get("halflife_hours", 48)                       ← explain copies it under
                                                                       'freshness_halflife_hours'
    line 185  await config_repo.get_config("dedup")                 ← group key 'dedup' (no row)
    line 186  dedup_config.get("enable_dedup_clustering", True)
    line 272  dedup_config.get("enable_related_spacing", True)

  src/domain/services/scorer.py
    line  36  config.get("freshness_halflife_hours", 48)
    line  28  config.get("scoring_weights", {"topic_match": 0.30, "embedding_sim": 0.25,
                                              "entity_match": 0.15, "sentiment_match": 0.05,
                                              "freshness": 0.15, "format_match": 0.10})

  src/domain/services/diversity_filter.py
    lines 37-39  max_topic_streak / max_topic_ratio / related_min_gap

  src/domain/services/narrow_scorer.py
    line  31  config.get("narrow_ranking_weights",
                          {"cosine": 0.7, "freshness": 0.3, "halflife_hours": 48})

  src/application/use_cases/update_profile.py
    line  53  await config_repo.get_config("profile")               ← group key 'profile' (no row)
    line 107  config.get("entity_decay_factor", 0.9)                 (NB: 0.9, not 0.95 like seed!)
    line 111  config.get("entity_cleanup_days", 30)

  src/domain/services/profile_updater.py
    line  33  config.get("learning_rate", 0.08)
    line  34  config.get("entity_min_weight", 0.4)
    line  35  config.get("entity_max_per_post", 5)

  src/domain/services/onboarding_service.py
    line  42  config.get("baseline_weight", 0.01)                    (was 'baseline' in seed)

Result before fix: rec_config rows are effectively dead — every config.get(...) falls
through to the hardcoded defaults in the calling code, and any change made via the
rec_config table is silently ignored.

WHAT THIS MIGRATION DOES
========================

* INSERT three missing group rows: 'feed', 'dedup', 'profile' with long keys
  exactly matching what the code reads.
* UPDATE 'onboarding_params' to rename 'baseline' → 'baseline_weight'.
* Leave existing 'scoring_weights' / 'ranking_params' / 'profile_params' /
  'signal_weights' rows intact (for now) but mark them deprecated in the
  description column — they are referenced by RecConfigLoader.get_* helpers
  which are nowhere wired into the actual call chain, but removing them would
  break any external tooling that reads them.

DEFAULTS
========

All values match the defaults in the calling code (see references above).
This migration changes only the storage layout — runtime behaviour is
identical to the pre-migration state where defaults always won.
"""

from typing import Sequence, Union

from alembic import op

revision: str = "015"
down_revision: Union[str, None] = "014"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute(r"""
        INSERT INTO data_flow.rec_config (key, value, description, updated_at)
        VALUES
        (
          'feed',
          '{
            "scoring_weights": {
              "topic_match":      0.30,
              "embedding_sim":    0.25,
              "entity_match":     0.15,
              "sentiment_match":  0.05,
              "freshness":        0.15,
              "format_match":     0.10
            },
            "freshness_halflife_hours":   48,
            "halflife_hours":             48,
            "max_age_hours":              48,
            "freshness_candidate_limit": 500,
            "embedding_candidate_limit": 500,
            "feed_size":                 200,
            "page_size":                  30,
            "max_topic_streak":            3,
            "max_topic_ratio":          0.40,
            "related_min_gap":             3,
            "narrow_ranking_weights": {
              "cosine":          0.7,
              "freshness":       0.3,
              "halflife_hours":   48
            }
          }'::jsonb,
          'Главный конфиг для GenerateFeedUseCase (Scorer, NarrowScorer, DiversityFilter). Создан миграцией 015 — заменяет короткоключевую запись scoring_weights/ranking_params, которая кодом не читалась.',
          NOW()
        ),
        (
          'dedup',
          '{
            "enable_dedup_clustering": true,
            "enable_related_spacing":  true
          }'::jsonb,
          'Флаги дедупликации (Union-Find кластеризация и RELATED-spacing). Читается в generate_feed.py:185,272.',
          NOW()
        ),
        (
          'profile',
          '{
            "learning_rate":         0.08,
            "entity_min_weight":     0.4,
            "entity_max_per_post":   5,
            "entity_decay_factor":   0.9,
            "entity_cleanup_days":  30
          }'::jsonb,
          'Конфиг EMA-обновления профиля и затухания entity_interests. Читается в update_profile.py и передаётся в ProfileUpdater.',
          NOW()
        )
        ON CONFLICT (key) DO UPDATE SET
          value       = EXCLUDED.value,
          description = EXCLUDED.description,
          updated_at  = NOW()
    """)

    # onboarding_params: rename baseline → baseline_weight (used in onboarding_service.py:42)
    op.execute(r"""
        UPDATE data_flow.rec_config
        SET value = jsonb_set(
                      value - 'baseline',
                      '{baseline_weight}',
                      COALESCE(value->'baseline', '0.01'::jsonb)
                    ),
            description = 'Параметры онбординга. baseline_weight используется в onboarding_service.py для не-выбранных тем.',
            updated_at = NOW()
        WHERE key = 'onboarding_params'
          AND value ? 'baseline'
    """)

    # Mark legacy rows as deprecated (do not delete — external tooling may still read them)
    op.execute(r"""
        UPDATE data_flow.rec_config
        SET description = '[DEPRECATED 015] Короткие ключи, не читаются ProcessContentUseCase / GenerateFeedUseCase. Используй ключи feed/profile/dedup.'
        WHERE key IN ('scoring_weights', 'ranking_params', 'profile_params', 'signal_weights')
    """)


def downgrade() -> None:
    op.execute("DELETE FROM data_flow.rec_config WHERE key IN ('feed', 'dedup', 'profile')")
    op.execute(r"""
        UPDATE data_flow.rec_config
        SET value = jsonb_set(
                      value - 'baseline_weight',
                      '{baseline}',
                      COALESCE(value->'baseline_weight', '0.01'::jsonb)
                    ),
            updated_at = NOW()
        WHERE key = 'onboarding_params'
          AND value ? 'baseline_weight'
    """)
    op.execute(r"""
        UPDATE data_flow.rec_config
        SET description = NULL
        WHERE key IN ('scoring_weights', 'ranking_params', 'profile_params', 'signal_weights')
    """)
