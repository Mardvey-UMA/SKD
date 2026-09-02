# Research Report: Codebase State — rec-system (2026-04-03)

## 1. Design Summary (from CLAUDE.md)

The rec-system is a Python FastAPI service (~95% implemented per CLAUDE.md).  
Key design facts relevant to the files inspected:

- Feed generation pipeline: process unprocessed user events inline → load profile → two-stage retrieval → dedup clustering → score → diversity filter → map to published_content IDs.
- Signal classification: 6 Kafka event types (IMPRESSION, CLOSE, OPEN, LIKE, DISLIKE, BOOKMARK) mapped to weighted signals via `SignalClassifier`.
- Profile update: EMA-based updates via `ProfileUpdater.apply_batch()`; entity interests upserted separately.
- `HandleInteractionsBatchUseCase` handles `user.interactions.batch` Kafka events; updates profile + publishes `recommendations.updated` with Redis debounce (15 min, max once per user).
- Disliked posts: excluded from feed candidates; `user_disliked_posts` table is a placeholder filled by the Kotlin gateway (rec-system reads only).
- Migrations: Alembic chain 007→008→009→010, branch label `rec_data_flow`, targets `content_agg_db` `data_flow` schema.

---

## 2. Existing Code — File-by-File

### 2.1 `src/application/use_cases/handle_interactions_batch.py`

Full content confirmed. 162 lines.

Key facts:
- Class: `HandleInteractionsBatchUseCase`
- Constructor dependencies: `profile_repo`, `content_repo`, `signal_classifier`, `profile_updater`, `event_publisher`, `cache_client`
- `execute(event: InteractionsBatchEvent) -> None`
  - Loads or creates empty profile
  - Maps `InteractionItem` → `UserInteraction` via `_make_fake_interaction()`
  - Calls `signal_classifier.classify_batch(interactions)`
  - Calls `content_repo.get_by_ids(post_ids)` for content features
  - Calls `profile_updater.apply_batch(profile, signals, content_features_map, config={})`
  - Saves updated profile via `profile_repo.save()`
  - If any `action_type` is in `STRONG_ACTION_TYPES = {"click", "share", "save", "hide"}`, calls `_maybe_publish_recommendations_updated()`
- `_maybe_publish_recommendations_updated()`: uses `cache_client.get/set` with key `rec:invalidation:last:{user_id}`, TTL=900s (15 min). Calls `event_publisher.publish_recommendations_updated(user_id=str(user_id), reason="interactions_processed")`.
- `_create_empty_profile()`: creates `UserProfile` with `cold_start=True`, `embedding=None`, uniform vectors.
- Action type map: `view→IMPRESSION, click→LIKE, share→BOOKMARK, save→BOOKMARK, hide→DISLIKE, scroll_past→IMPRESSION`
- NOTE: `apply_batch()` is called with `config={}` (empty dict, no config repo injection in this use case).
- NOTE: Entity changes returned by `apply_batch()` are IGNORED (assigned to `_entity_changes`) — no `entity_interest_repo` is injected into this use case.

### 2.2 `src/application/use_cases/update_profile.py` — lines 95–120

```
95:  content_features_map=content_features_map,
96:  config=config,
97:  )
98:
99:  # Persist updated profile
100: await self._profile_repo.save(updated_profile)
101:
102: # Upsert entity interests
103: for entity_interest in entity_changes:
104:     await self._entity_interest_repo.upsert(entity_interest)
105:
106: # Apply entity decay
107: decay_factor = config.get("entity_decay_factor", 0.9)
108: await self._entity_interest_repo.decay_all_for_user(user_uuid, decay_factor)
109:
110: # Clean up expired entities
111: cleanup_days = config.get("entity_cleanup_days", 30)
112: await self._entity_interest_repo.cleanup_expired(user_uuid, cleanup_days)
113:
114: # Mark interactions as processed
115: interaction_ids = [inter.id for inter in user_interactions]
116: await self._interaction_repo.mark_processed(interaction_ids)
117:
118: users_updated += 1
119: events_processed += len(user_interactions)
120:
```

This is inside the per-user processing loop of `UpdateProfileUseCase.execute()`. Contrast with `HandleInteractionsBatchUseCase`, which does NOT upsert entity interests, does NOT apply decay, and does NOT mark interactions as processed from `user_interactions` table.

### 2.3 `src/infrastructure/container.py`

Full content confirmed. 330 lines.

All providers registered:
- Repositories: `user_profile_repo`, `content_repo`, `config_repo`, `entity_interest_repo`, `interaction_repo`, `disliked_posts_repo`, `category_repo`, `recommendation_history_repo`
- NLP singletons: `topic_classifier`, `sentiment_analyzer`, `entity_extractor`, `content_encoder`, `text_analyzer`
- Domain services: `scorer`, `diversity_filter`, `signal_classifier` (Factory, hardcoded `signal_weights`), `profile_updater`, `onboarding_service`
- Use cases: `generate_feed_use_case`, `onboard_user_use_case`, `process_content_use_case`, `update_profile_use_case`, `get_categories_use_case`, `get_cold_start_use_case`, `get_recommendations_use_case`, `handle_user_created_use_case`, `handle_interactions_batch_use_case`
- Kafka consumers: `user_created_consumer`, `interactions_batch_consumer`
- Scheduler: `job_scheduler`

`handle_interactions_batch_use_case` is wired as:
```python
HandleInteractionsBatchUseCase(
    profile_repo=user_profile_repo,
    content_repo=content_repo,
    signal_classifier=signal_classifier,
    profile_updater=profile_updater,
    event_publisher=kafka_event_publisher,
    cache_client=redis_cache_client,
)
```
— `entity_interest_repo` is NOT injected into this use case.

`signal_classifier` is wired as `providers.Factory(SignalClassifier, signal_weights={...})` with hardcoded weights (not loaded from `rec_config` table). Weights hardcoded: impression_read=0.15, impression_skip=-0.05, open=0.1, close_fast=-0.2, close_full=0.5, close_half=0.4, close_other=0.1, like=0.6, dislike=-0.7, bookmark=0.8.

### 2.4 `src/infrastructure/persistence/pg_interaction_repository.py`

Full content confirmed. 75 lines.

Class: `PgInteractionRepository(InteractionRepository)`
- `get_unprocessed_for_user(user_id)`: SELECT with `WHERE processed = False AND user_id = ?`, `WITH FOR UPDATE SKIP LOCKED`
- `get_unprocessed(limit)`: SELECT with `WHERE processed = False LIMIT ?`, `WITH FOR UPDATE SKIP LOCKED`
- `mark_processed(interaction_ids)`: UPDATE SET processed=True WHERE id IN (...)

### 2.5 `src/infrastructure/persistence/pg_disliked_posts_repository.py`

Full content confirmed. 30 lines.

Class: `PgDislikedPostsRepository(DislikedPostsRepository)`
- Single method: `get_disliked_post_ids(user_id)` — SELECT post_id WHERE user_id = ?
- Returns `List[UUID]`

### 2.6 `src/infrastructure/persistence/models/user_disliked_posts.py`

Full content confirmed. 12 lines.

```python
class UserDislikedPostsModel(Base):
    __tablename__ = "user_disliked_posts"
    user_id = Column(UUID(as_uuid=True), primary_key=True)
    post_id = Column(UUID(as_uuid=True), primary_key=True)
```

No `__table_args__` with schema. The model uses the default schema — no explicit `schema="data_flow"` set. The `Base` definition in `models/base.py` determines whether the schema is set at the declarative base level.

### 2.7 `src/application/use_cases/generate_feed.py`

Full content confirmed. 236 lines.

Class: `GenerateFeedUseCase`
- Constructor: `profile_repo`, `content_repo`, `entity_interest_repo`, `interaction_repo`, `config_repo`, `disliked_posts_repo`, `scorer`, `diversity_filter`, `signal_classifier`, `profile_updater`
- `execute(request: GenerateFeedRequest) -> GenerateFeedResponse`
  1. Loads config via `config_repo.get_config("feed")`
  2. Processes user events inline via `_process_user_events_inline()` (includes entity upsert)
  3. Loads profile
  4. Two-stage retrieval: freshness + embedding candidates
  5. Deduplicates by post_id
  6. Excludes disliked posts via `disliked_posts_repo.get_disliked_post_ids()`
  7. Dedup clustering via `content_repo.get_dedup_clusters()` (if `enable_dedup_clustering=True`)
  8. Loads entity interests for scoring
  9. Scores via `scorer.score_batch()`
  10. Applies diversity filter via `diversity_filter.filter()` (includes RELATED spacing via `content_repo.get_related_pairs()`)
  11. Maps to published IDs via `content_repo.get_published_ids()`
  12. Returns `GenerateFeedResponse(feed=[{post_id, score}], meta={...})`

`_process_user_events_inline()`: does entity upsert (`entity_interest_repo.upsert()`), marks interactions processed. Uses `interaction_repo.get_unprocessed_for_user()`.

---

## 3. DB Tables and Migrations

### Migration chain (all in `data_flow` schema, `content_agg_db`)

| Revision | File | What it creates |
|----------|------|-----------------|
| 007 (root, branch_labels="rec_data_flow") | `007_data_flow_schema.py` | `raw_content`, `posts_features`, `rec_profiles`, `rec_entity_interests`, `rec_config`, `user_interactions`, `user_disliked_posts`; seeds default rec_config |
| 008 (→ 007) | `008_add_categories_table.py` | `data_flow.categories` with 18 NLP topic rows |
| 009 (→ 008) | `009_add_recommendation_history.py` | `data_flow.recommendation_history` (user_id, content_id, recommended_at) |
| 010 (→ 009) | `010_add_cold_start_column.py` | ADD COLUMN `cold_start BOOLEAN NOT NULL DEFAULT true` to `data_flow.rec_profiles` |

Migrations 001–006 target `rec_db` (legacy, not `content_agg_db`).

### Schema: `data_flow.user_disliked_posts` (from migration 007)
```sql
CREATE TABLE IF NOT EXISTS data_flow.user_disliked_posts (
    user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    PRIMARY KEY (user_id, post_id)
)
```

### Schema: `data_flow.rec_profiles` (from migration 007 + 010)
Columns: `user_id UUID PK`, `topic_vector JSONB`, `embedding VECTOR(312)`, `sentiment_prefs JSONB`, `format_prefs JSONB`, `interaction_count INTEGER`, `created_at TIMESTAMPTZ`, `last_updated TIMESTAMPTZ`, `cold_start BOOLEAN NOT NULL DEFAULT true` (added in 010).

### Schema: `data_flow.recommendation_history` (from migration 009)
Columns: `user_id UUID`, `content_id UUID`, `recommended_at TIMESTAMPTZ DEFAULT now()`, PK(user_id, content_id).  
Index: `ix_rec_history_user_recommended_at ON (user_id, recommended_at)`.

---

## 4. Domain Interfaces (`src/domain/interfaces/`)

All 16 files listed:

| File | Class | Abstract Methods |
|------|-------|-----------------|
| `user_profile_repository.py` | `UserProfileRepository(ABC)` | `get_by_user_id(user_id)`, `save(profile)` |
| `content_repository.py` | `ContentRepository(ABC)` | `get_unprocessed`, `save_features`, `get_candidates_by_freshness`, `get_candidates_by_embedding`, `get_by_ids`, `get_dedup_clusters`, `get_related_pairs`, `get_published_ids`, `get_recent_published_ids` |
| `entity_interest_repository.py` | `EntityInterestRepository(ABC)` | `get_top_for_user`, `upsert`, `decay_all_for_user`, `cleanup_expired` |
| `interaction_repository.py` | `InteractionRepository(ABC)` | `get_unprocessed_for_user`, `get_unprocessed`, `mark_processed` |
| `config_repository.py` | `ConfigRepository(ABC)` | `get_config(key)` |
| `disliked_posts_repository.py` | `DislikedPostsRepository(ABC)` | `get_disliked_post_ids(user_id)` |
| `cache_client.py` | `CacheClient(ABC)` | `get(key)`, `set(key, value, ttl)`, `delete(key)` |
| `event_publisher.py` | `EventPublisher(ABC)` | `publish_recommendations_updated(user_id, reason)` |
| `category_repository.py` | `CategoryRepository(ABC)` | `get_active_categories()`, `get_category_ids()` |
| `recommendation_history_repository.py` | `RecommendationHistoryRepository(ABC)` | `get_recommended_ids(user_id)`, `save_recommendations(user_id, content_ids)`, `delete_older_than(days)` |
| `content_encoder.py` | `ContentEncoder(ABC)` | `encode(text)`, `encode_batch(texts)` |
| `topic_classifier.py` | `TopicClassifier(ABC)` | `classify(text)` |
| `sentiment_analyzer.py` | `SentimentAnalyzer(ABC)` | `analyze(text)` |
| `entity_extractor.py` | `EntityExtractor(ABC)` | `extract(text)` |
| `text_analyzer.py` | `TextAnalyzer(ABC)` | `analyze(text)` (sync) |
| `__init__.py` | (empty) | — |

---

## 5. Infrastructure Persistence Models (`src/infrastructure/persistence/models/`)

All 14 files listed:

| File | Class | Table |
|------|-------|-------|
| `base.py` | `Base` | declarative base |
| `rec_config.py` | — | `rec_config` |
| `raw_content.py` | — | `raw_content` |
| `user_disliked_posts.py` | `UserDislikedPostsModel` | `user_disliked_posts` — no explicit `schema="data_flow"` in model definition visible |
| `user_interactions.py` | `UserInteractionsModel` | `user_interactions` |
| `posts_features.py` | — | `posts_features` |
| `rec_entity_interests.py` | — | `rec_entity_interests` |
| `article.py` | — | (dedup `articles` table) |
| `similarity.py` | — | (dedup `similarities` table) |
| `__init__.py` | — | — |
| `category.py` | — | `categories` |
| `recommendation_history.py` | — | `recommendation_history` |
| `rec_profiles.py` | — | `rec_profiles` |
| `published_content.py` | — | `published_content` |

---

## 6. Grep Results

### `disliked` in `src/` (8 files)

```
src/infrastructure/container.py
src/application/use_cases/generate_feed.py
src/infrastructure/persistence/models/__init__.py
src/infrastructure/persistence/migrations/versions/007_data_flow_schema.py
src/domain/interfaces/disliked_posts_repository.py
src/infrastructure/persistence/pg_disliked_posts_repository.py
src/infrastructure/persistence/models/user_disliked_posts.py
src/infrastructure/persistence/migrations/versions/005_create_kotlin_owned_tables.py
```

### `disliked` in `tests/` (8 files)

```
tests/unit/infrastructure/test_container.py
tests/unit/application/use_cases/test_generate_feed.py
tests/unit/domain/interfaces/test_repository_interfaces.py
tests/unit/infrastructure/persistence/test_models.py
tests/integration/test_generate_feed_integration.py
tests/integration/test_pg_content_repository.py
tests/integration/test_migrations.py
tests/unit/infrastructure/persistence/test_pg_disliked_posts_repository.py
```

### `entity_interest` in `tests/unit/` (10 files)

```
tests/unit/infrastructure/test_container.py
tests/unit/application/use_cases/test_generate_feed.py
tests/unit/application/use_cases/test_update_profile.py
tests/unit/domain/interfaces/test_repository_interfaces.py
tests/unit/domain/services/test_profile_updater.py
tests/unit/infrastructure/persistence/test_models.py
tests/unit/domain/services/test_scorer.py
tests/unit/test_migration_007_structure.py
tests/unit/infrastructure/persistence/test_pg_entity_interest_repository.py
tests/unit/domain/entities/test_entity_interest.py
```

### `HandleInteractionsBatch|handle_interactions_batch` in `tests/` (3 files)

```
tests/unit/test_task26_wiring.py
tests/unit/presentation/consumers/test_kafka_consumers.py
tests/unit/application/use_cases/test_handle_interactions_batch.py
```

---

## 7. Key Gaps and Asymmetries Observed (FACTS only, no suggestions)

1. `HandleInteractionsBatchUseCase.execute()` calls `profile_updater.apply_batch()` and receives `_entity_changes` (discarded with `_` prefix). `entity_interest_repo` is NOT injected into `HandleInteractionsBatchUseCase`. Entity interest changes from Kafka-driven batch events are NOT persisted.

2. `HandleInteractionsBatchUseCase.execute()` calls `apply_batch(config={})` — empty config dict. No `config_repo` is injected. Signal weights and profile update parameters are not loaded from DB for this path.

3. `UpdateProfileUseCase` (scheduler-driven) DOES upsert entity interests, apply decay, cleanup expired, and mark interactions as processed. `HandleInteractionsBatchUseCase` does NONE of these steps.

4. `HandleInteractionsBatchUseCase` does NOT mark any `user_interactions` records as processed (it never calls `interaction_repo.mark_processed()`). It also does not read from `user_interactions` table — it operates solely on the Kafka-delivered `InteractionsBatchEvent`.

5. `signal_classifier` in the DI container is wired with hardcoded `signal_weights` dict, not loaded from `rec_config` table. The `config_repo` is not used for `SignalClassifier` initialization.

6. `UserDislikedPostsModel` has no explicit `schema="data_flow"` visible in its 12-line definition. Whether the schema is set at Base level is determined by `models/base.py` (not inspected in this pass).

7. Migrations 001–006 target `rec_db` (legacy). Only 007–010 target `content_agg_db` `data_flow` schema.

8. `rec_config` seeded in migration 007 does NOT include a `dedup_params` key (only signal_weights, scoring_weights, profile_params, ranking_params, onboarding_params are seeded). `dedup_params` is referenced in CLAUDE.md under "Configuration" but is absent from the seed INSERT.
