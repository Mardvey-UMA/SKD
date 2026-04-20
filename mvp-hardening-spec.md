# Technical Specification: MVP Hardening & Cleanup

**Project**: cross-service (backend + rec-system + frontend-app + SKD root)
**Version**: 1.0
**Created**: 2026-04-20
**Target root**: `/home/mattew/SKD/`
**Estimated effort**: 8–12 рабочих дней (1 engineer) or 5–7 days with parallelism
**Execution mode**: SKD orchestrator → fresh `claude -p` sub-sessions
**Goal**: устранить ВСЕ known issues / technical debt накопленный за три предыдущих проекта, довести систему до максимально стабильного, чистого, maintainable состояния. После этой работы система готова к real-user soft launch без technical blockers.

---

## 0. Context (MUST READ before starting)

### Предыдущие проекты (все ✅ merged в master'ах)

1. **Eval harness** (`/home/mattew/SKD/rec-system-eval-harness-spec.md`) — persona benchmark + LLM-as-judge + proxy metrics
2. **Feature delivery** (`/home/mattew/SKD/rec-system-feature-delivery-spec.md`) — Phase A/B/C (live profile, hot arrival, cross-encoder rerank) за feature flags
3. **Data capture foundation** (`/home/mattew/SKD/data-capture-spec.md`) — X-Request-Id chain, feed_requests/feed_items logging, structured JSON logs, Kafka v2 schema

Все три спеки + их final_report'ы живут в:
- `.claude/artifacts/rec-eval/` (eval + feature delivery)
- `.claude/artifacts/data-capture/` (foundation)

### Текущее состояние (AS-IS verified 2026-04-20)

#### ✅ Работает и проверено end-to-end
- X-Request-Id chain от gateway до rec-system до user_interactions (money JOIN verified)
- feed_requests + feed_items таблицы получают данные per request (20 items × scoring_components JSONB)
- InteractionsBatchConsumer timestamp bug — ИСПРАВЛЕН (c9ff9e5, eeb63ec, e336613)
- Structured JSON logs во всех 7 сервисах
- rec-system `/metrics` Prometheus endpoint
- pgvector search_path fix применён к main container.py (line 94)
- Все 8 feat/mvp-data-capture веток merged в master'ы
- raw_content backlog: 2 pending_rec / 0 pending_dedup (практически drained)
- Memory обновлена: 5 новых записей отражают known issues

#### ⚠️ Известные issues / technical debt (предмет этого проекта)

Разбито по Tier по критичности:

### Tier A — Blocker для real users

**A1. ActionType contract mismatch**
- Backend enum (`backend/user-interactions-service/src/main/kotlin/com/skd/userinteractions/domain/ActionType.kt`): `VIEW, CLICK, SCROLL_PAST, SHARE, SAVE, HIDE, DISLIKE`
- Frontend ожидания (per `frontend-app/API_CONTRACTS.md` + rec-system signal weights): `LIKE, BOOKMARK, CLOSE, IMPRESSION, DISLIKE, OPEN`
- rec-system `SignalClassifier` в `src/domain/services/signal_classifier.py` ожидает те же типы что и frontend
- **Результат**: если frontend отправит LIKE — backend silently отклонит (validator returns `accepted=0`, HTTP 202, но DB не пишется). Rec-system EMA не обновляется. Recommendations остаются прежними.
- **Критичность**: любой positive signal от real user теряется. Training data невозможно собрать.
- **Memory**: `project_action_type_contract_mismatch.md`

**A2. Frontend mock repositories not fully replaced with API**
- Present mocks (5 из 5 согласно frontend-app/CLAUDE.md checklist):
  - `lib/features/feed/data/repositories/mock_feed_repository.dart`
  - `lib/features/collections/data/repositories/mock_collections_repository.dart`
  - `lib/features/profile/data/repositories/mock_profile_repository.dart`
  - `lib/features/settings/data/repositories/mock_settings_repository.dart`
  - `lib/features/onboarding/data/repositories/mock_onboarding_repository.dart`
- Auth repository УЖЕ имеет ApiAuthRepository impl
- **Критичность**: real users не могут использовать feed/collections/onboarding/profile/settings через frontend
- User's message (2026-04-20): "фронт сейчас уже нормально работает и интегрировать с API старые мок пока остались" — частично integrated

### Tier B — Data quality loss (блокер для будущего ML training)

**B1. user_interactions missing columns: scroll_depth + metadata**
- Kafka event schema v2 передаёт `scroll_depth` + `metadata` (JSONB)
- HTTP `POST /api/interactions/batch` принимает эти поля
- Backend persists 5 из 6 заявленных полей (feed_request_id, position_in_feed, device_type, app_version, ab_bucket)
- scroll_depth и metadata просто ДРОПАЮТСЯ при записи в DB
- **Критичность**: scroll_depth — ключевой сигнал для CLOSE event quality (read vs skim); metadata — forward-compat для будущих полей
- **Memory**: `project_user_interactions_missing_cols.md`

**B2. Cached feed responses lack scoring_components**
- When `X-Feed-Source: cached` (Redis cache hit), feed_items rows пишутся но без scoring_components populated
- Training dataset теряет breakdown для cached responses
- Rate of cache hits в production может быть 20-40% → существенная потеря training data
- **Решение**: либо cache'ить breakdown JSONB вместе с item IDs, либо фильтровать training query только по source='personalized'

**B3. Partial feed_requests logging when timeout**
- Если rec-system отвечает > timeout, feed-service может не успеть написать feed_requests row до возврата response
- Async fire-and-forget pattern — loss at edges
- Нужна проверка: действительно ли dropped при timeout?

### Tier C — Observability gaps

**C1. Prometheus /metrics endpoint на Kotlin services**
- Сейчас только rec-system экспортирует кастомные метрики (feed_request_count, latency histogram, Kafka consumer lag)
- 6 Kotlin backend сервисов имеют только default Spring Boot Actuator metrics (JVM heap, requests, etc.)
- **Memory**: `project_prometheus_kotlin_deferred.md`
- Требуется: Micrometer + micrometer-registry-prometheus в зависимостях, настройка /actuator/prometheus endpoint, custom meters для (a) interaction batch count, (b) feed request count (feed-service), (c) Kafka producer count

**C2. Нет Grafana dashboard**
- /metrics есть но никто не scrap'ает
- Prometheus server не задеплоен
- Dashboard templates отсутствуют
- **Decision**: deploy Prometheus + Grafana или использовать внешний monitoring
- **Scope тут**: минимум — задеплоить Prometheus scrap + 1 Grafana dashboard с ключевыми panels

**C3. Нет alerting**
- Нет правил alerting (high error rate, consumer lag, p95 latency breach)
- **Scope тут**: defer — alerting имеет смысл после soft launch

### Tier D — Developer Experience / Tooling issues

**D1. E2E script accessToken camelCase bug**
- `scripts/e2e_data_capture_test.sh` step 1 ищет `accessToken` в login response
- Backend возвращает `access_token` (snake_case)
- Workaround: `TOKEN=<jwt>` env var
- **Memory**: `project_e2e_script_accesstoken_bug.md`
- Fix: 1 line `sed` или правильный jq query

**D2. nvcr.io TLS interception в dev environment**
- Docker pull `nvcr.io/nvidia/cuda:...` не проходит — MITM proxy возвращает certificate for `*.myseabreeze.com` / `*.tow.club`
- Workaround: retag `nvidia/cuda:...` из docker.io mirror
- **Scope тут**: документировать в CLAUDE.md + retag automatically в setup script

**D3. k3d bulk image import shell quote bug**
- `for svc in ...; do k3d image import skd-$svc:latest -c skd; done` трактует `:latest` как modifier
- Workaround: `"skd-${svc}:latest"` explicit quoting
- **Scope тут**: фиксировать в deploy scripts + CLAUDE.md guidance

**D4. Отсутствует email verification bypass для E2E tests**
- Auth-service dev-mode имеет verification code `000000`, но E2E script не использует
- Приходится руками UPDATE auth.users SET email_verified=true
- **Fix**: добавить в E2E script dev-mode email verification flow

### Tier E — Architecture / Technical debt

**E1. codebase-rag dev tool on production GPU**
- `codebase-rag` консьюмит **5.6 GB VRAM** на RTX 5060 Ti — больше чем rec-worker (1.1 GB) и dedup-worker (2.8 GB) вместе
- Это dev-tool для codebase indexing, не production дolga
- **Fix options**: (a) перенести codebase-rag на CPU-only (`CUDA_VISIBLE_DEVICES=""`), (b) остановить когда не нужен, (c) перенести на отдельную машину

**E2. dedup-worker и rec-worker делят одну GPU**
- Для текущей нагрузки (0 users) хватает
- При soft launch возможна contention
- Feature flags A/B/C требуют дополнительной VRAM если включить cross-encoder
- **Decision**: defer миграцию dedup на Quadro RTX 4000 до того как реально упрёмся. Пока документируем как known limit.

**E3. Liquibase не embedded в production jars**
- Backend services имеют `liquibase-core` только как `testImplementation`
- Runtime Spring Liquibase auto-config не срабатывает
- Migrations применяются через `scripts/apply-backend-migrations.sh` (Liquibase k8s Job)
- **Fix**: переключить в `runtimeOnly` в `build.gradle.kts` каждого сервиса — восстановит Spring Boot auto-config

**E4. rec-system Logging/Monitoring в CLAUDE.md написано "NOT IMPLEMENTED"**
- На самом деле теперь IMPLEMENTED (structured JSON + /metrics)
- Нужно обновить `rec-system/CLAUDE.md` implementation status table

**E5. Нет rec_config caching**
- DB read per feed request (rec-system `RecConfigLoader`)
- При 100 rps нагрузке — 100 DB reads/sec для static data
- **Fix**: 30-60s TTL in-memory cache + invalidation on update

**E6. PGVector без HNSW index**
- Упоминается как "needs HNSW index at >50K posts"
- Сейчас 8500 processed posts → ещё не нужен
- **Scope тут**: defer, но добавить follow-up ticket

### Tier F — Code quality / corpus limitations

Эти issues не ломают систему, но ограничивают качество:

**F1. NER без entity normalization**
- "Путин" vs "В. Путин" vs "Владимир Путин" = 3 entities
- Снижает entity_match scoring
- **Scope тут**: defer (большой отдельный проект)

**F2. "Международные новости" = 59.6% корпуса**
- Доминирует retrieval
- Решается через (a) rebalancing scoring, (b) topic distribution normalization, (c) corpus curation
- **Scope тут**: defer

**F3. Topic confidence ~0.658 avg**
- Zero-shot NLI noise
- Можно улучшить fine-tune classifier'ом
- Было в feature delivery как Phase C followup
- **Scope тут**: defer

**F4. `is_long_form` threshold 300 words**
- Срабатывает только на 0.9% content
- Скорее всего threshold должен быть ниже (200?)
- **Scope тут**: include в этом проекте — trivial tuning

### Tier G — Feature flag decision

**G1. Feature flags остаются OFF**
- `REC_FEATURE_LIVE_PROFILE=false` — ждёт Phase A 2.0 tuning
- `REC_FEATURE_HOT_ARRIVAL=false` — ждёт реальной breaking news load
- `REC_FEATURE_RERANK=false` — не рекомендуется (hurts broad users per M3 benchmark)
- **Scope тут**: не trogat, документировать в `docs/feature_flags.md` в rec-system

### Summary — что в scope этого проекта

**In scope (hard fixes)**:
- **Tier A**: A1 (action_type), A2 (frontend mocks) — blocker для users
- **Tier B**: B1 (missing cols), B2 (cached breakdown) — блокер для ML training
- **Tier C**: C1 (Kotlin metrics), C2 (minimal Grafana) — basic observability
- **Tier D**: D1 (e2e script), D2/D3 (deploy scripts), D4 (email verification) — developer experience
- **Tier E**: E3 (liquibase runtime), E4 (CLAUDE.md update), E5 (rec_config cache) — tech debt
- **Tier F**: F4 (is_long_form threshold) — trivial

**Out of scope (deferred)**:
- E1 (codebase-rag GPU) — не ломает, просто optimization
- E2 (dedup GPU migration) — hardware decision
- E6 (HNSW) — not yet needed
- F1 (NER normalization) — big ML project
- F2 ("Международные" dominance) — scoring redesign
- F3 (topic fine-tune) — weeks of ML work
- G1 (feature flags) — decision, не debt

---

## 1. Goals

1. Устранить все **Tier A+B** issues — система готова принимать real user interactions без потерь сигнала.
2. Закрыть **Tier C** — базовая observability во всех сервисах.
3. Сделать **Tier D** fixes — e2e tests run cleanly без manual workarounds.
4. Рефакторить **Tier E3-E5** — clean tech debt.
5. Обновить все **CLAUDE.md** в actual state.
6. E2E test проходит без вмешательств и ручных правок.

## 2. Non-goals (explicit)

- ❌ NOT changing scoring formula, retrieval algorithm, NLP pipeline
- ❌ NOT включать feature flags A/B/C (остаются OFF)
- ❌ NOT moving dedup to separate GPU (hardware decision)
- ❌ NOT entity normalization (separate ML project)
- ❌ NOT topic classifier fine-tune (separate ML project)
- ❌ NOT A/B testing user bucketing (post-launch)
- ❌ NOT Prometheus alerting rules (post-launch after metrics stabilize)
- ❌ NOT touching parser-service, dedup-system, content-aggregator, config-service
- ❌ NOT real email verification integration (dev-mode enough for MVP)

## 3. Success Criteria

- [ ] Все 10 фаз выполнены, коммиты verified
- [ ] Functional E2E acceptance test из §12 проходит **без ручных workaround'ов**:
  - No manual email verification в DB
  - No TOKEN env var override
  - No manual content_id selection
  - Скрипт выходит с `exit 0` end-to-end
- [ ] `send LIKE event → LIKE persisted with feed_request_id + scroll_depth`
- [ ] 5 frontend repositories заменены на ApiXxxRepository
- [ ] feed_items для cached responses **включают scoring_components**
- [ ] 6 Kotlin backend services экспонируют Prometheus metrics at `/actuator/prometheus`
- [ ] Все существующие unit + integration tests зелёные
- [ ] rec-system CLAUDE.md implementation status отражает actual state
- [ ] Memory обновлена: 5 issue files → статус RESOLVED (или частично)
- [ ] Final report в `.claude/artifacts/mvp-hardening/final_report.md`

## 4. Orchestration Model

Тот же паттерн что в трёх предыдущих проектах. Ссылки на детали:
- §5 `rec-system-eval-harness-spec.md`
- §4 `rec-system-feature-delivery-spec.md`
- §4 `data-capture-spec.md`

### 4.1. Progress tracker
`/home/mattew/SKD/.claude/artifacts/mvp-hardening/hardening_state.md` (append-only)

### 4.2. Fresh sub-claude per phase
Каждая фаза = один `claude -p` вызов из соответствующей project directory.

### 4.3. Cost budget
- Per phase: **$15** cap
- Total: **$90** cap (меньше чем data-capture $120 т.к. большинство фаз — targeted fixes не full features)
- Alert на 80% ($72)

### 4.4. Branch strategy

Каждый проект: ветка `feat/mvp-hardening` из master'а.
После каждой фазы — commit в ветку, НЕ merge.
Финальный merge всех веток — только после полного E2E pass + user approval.

---

## 5. Phases (ordered execution)

### Phase dependency graph

```
P0 (AS-IS verify) ─┐
                   ├→ P1 (action_type align) ─→ P4 (frontend mocks) ─┐
                   ├→ P2 (scroll_depth + metadata cols) ──────────────┤
                   ├→ P3 (cached breakdown) ──────────────────────────┤
                   ├→ P5 (e2e script fixes) ──────────────────────────┤
                   ├→ P6 (Kotlin prometheus) ─────────────────────────┤
                   ├→ P7 (liquibase runtime) ─────────────────────────┤
                   ├→ P8 (rec_config cache + is_long_form tune) ──────┤
                   └→ P9 (docs/CLAUDE.md updates) ─────────────────────┤
                                                                        ▼
                                                            P10 (E2E validation + final report)
```

**Parallelism opportunities**: P1, P2, P3, P5, P6, P7, P8, P9 все относительно независимы — можно запускать параллельно в разных проектах. P4 зависит от P1 (знает правильные action_types). P10 последняя.

---

### Phase P0: AS-IS Verification (0.3 day)

**Goal**: проверить все assumption'ы из §0 против реального кода.

**Executed from**: SKD root

**Checks**:
1. Action types enum в `backend/user-interactions-service/src/main/kotlin/com/skd/userinteractions/domain/ActionType.kt`
2. Action types в `rec-system/src/domain/services/signal_classifier.py`
3. Action types в `frontend-app/lib/features/feed/data/*` DTO
4. Frontend mocks: точно ли 5 repositories
5. user_interactions DDL: проверить отсутствие scroll_depth/metadata
6. Feed cache: `backend/feed-service/src/main/.../*cache*` — как cache populated
7. Kotlin backend `build.gradle.kts`: подтвердить liquibase-core как testImplementation
8. rec-system container.py: pgvector fix confirmed on line 94
9. Memory items: все 5 issue файлов present

**Deliverable**: `.claude/artifacts/mvp-hardening/as_is_report.md` — findings + any surprises

**No commits expected.**

**Max turns**: 40

---

### Phase P1: ActionType Contract Alignment (1.5 days)

**Executed from**: сначала `backend/user-interactions-service`, потом `rec-system`, потом `frontend-app`

**Goal**: единый enum action_type на трёх концах.

### P1.1. Decision — canonical enum

Из signal weights table (rec-system CLAUDE.md):
- IMPRESSION (with duration_ms)
- CLOSE (with duration, scroll %)
- LIKE
- DISLIKE
- BOOKMARK
- OPEN (paired с CLOSE)

**Decision**: использовать **rec-system canonical set** — 6 типов: `IMPRESSION, OPEN, CLOSE, LIKE, DISLIKE, BOOKMARK`.

Backend existing: VIEW/CLICK/SCROLL_PAST/SHARE/SAVE/HIDE/DISLIKE. Отображение старых на новые:
- VIEW → IMPRESSION
- CLICK → OPEN
- SCROLL_PAST → CLOSE (with scroll_depth)
- SHARE → (пока нет в rec-system; добавить как опциональный 7-й тип OR использовать BOOKMARK metadata)
- SAVE → BOOKMARK
- HIDE → DISLIKE (merge)
- DISLIKE → DISLIKE

### P1.2. Migration strategy

Т.к. старых users нет — безопасно просто переписать enum. Но для правильности:

1. **Backend**: добавить новые типы в enum, сохранить старые как deprecated (accepted в validator, mapped в canonical).
2. **Frontend**: отправлять canonical типы.
3. **rec-system**: SignalClassifier уже знает canonical — verify.
4. **Migration для existing user_interactions rows** (если такие есть): UPDATE old values → canonical. В нашем случае test rows можно truncate.

### P1.3. Implementation

#### Backend (user-interactions-service)

```kotlin
// backend/user-interactions-service/src/main/kotlin/com/skd/userinteractions/domain/ActionType.kt
enum class ActionType {
    IMPRESSION, OPEN, CLOSE, LIKE, DISLIKE, BOOKMARK;

    companion object {
        // Legacy names for backward compat during migration
        private val LEGACY_MAP = mapOf(
            "VIEW" to IMPRESSION,
            "CLICK" to OPEN,
            "SCROLL_PAST" to CLOSE,
            "SAVE" to BOOKMARK,
            "HIDE" to DISLIKE,
            "SHARE" to BOOKMARK  // Until SHARE added as separate type
        )

        fun fromString(input: String): ActionType {
            val normalized = input.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: LEGACY_MAP[normalized]
                ?: throw IllegalArgumentException("Unknown action type: '$input'")
        }
    }
}
```

#### rec-system signal classifier

Verify что `SignalClassifier.classify()` обрабатывает все 6 типов согласно weights table. Add tests.

#### frontend-app

```dart
// frontend-app/lib/features/feed/domain/models/interaction_action.dart
enum InteractionAction {
  impression('IMPRESSION'),
  open('OPEN'),
  close('CLOSE'),
  like('LIKE'),
  dislike('DISLIKE'),
  bookmark('BOOKMARK');

  const InteractionAction(this.apiValue);
  final String apiValue;
}
```

Update all call sites that create interaction events.

### P1.4. Tests

Backend:
- Unit: fromString accepts canonical + legacy, rejects garbage
- Integration: POST /api/interactions/batch with LIKE → persisted as LIKE in DB

rec-system:
- Unit: SignalClassifier weights for all 6 canonical types
- Integration: Kafka consumer v2 event with LIKE → profile EMA updated positively

frontend-app:
- Unit: InteractionAction.apiValue serialization
- Widget: like button sends LIKE event

### P1.5. Commits

Backend:
- `test(user-interactions): RED tests for canonical ActionType enum + legacy mapping`
- `feat(user-interactions): align ActionType to rec-system canonical (IMPRESSION/OPEN/CLOSE/LIKE/DISLIKE/BOOKMARK)`

rec-system:
- `test(application): RED tests for SignalClassifier on all 6 canonical action types`
- `feat(application): ensure SignalClassifier handles canonical action types`

frontend-app:
- `feat(domain): canonical InteractionAction enum + API serialization`
- `refactor(ui): use InteractionAction enum across feed card + collections + onboarding`

**Max turns**: 100

---

### Phase P2: user_interactions scroll_depth + metadata Columns (0.5 day)

**Executed from**: `backend/user-interactions-service`

**Goal**: добавить недостающие колонки в `interactions.user_interactions`; wire producer/consumer.

### P2.1. Migration

```sql
-- backend/user-interactions-service/src/main/resources/changelog/migrations/20260420__add_scroll_depth_metadata.sql
--liquibase formatted sql
--changeset mattew:20260420__add_scroll_depth_and_metadata
ALTER TABLE interactions.user_interactions
    ADD COLUMN IF NOT EXISTS scroll_depth REAL,
    ADD COLUMN IF NOT EXISTS metadata JSONB;

COMMENT ON COLUMN interactions.user_interactions.scroll_depth IS 
    '0.0–1.0 fraction, populated for CLOSE events';
COMMENT ON COLUMN interactions.user_interactions.metadata IS 
    'Forward-compat JSONB for client-specific fields';
```

### P2.2. Update entity + repository + processor

```kotlin
// UserInteraction data class
data class UserInteraction(
    // ... existing fields
    val scrollDepth: Float? = null,         // NEW
    val metadata: Map<String, Any>? = null   // NEW JSONB
)

// Processor
// In InteractionProcessor.processBatch(), include:
scrollDepth = event.scrollDepth,
metadata = event.metadata
```

### P2.3. Kafka payload extension

v2 schema уже имеет эти поля optional — verify producer actually writes them.

### P2.4. Tests

- Migration integration test: ALTER applies cleanly, existing rows have NULL in new cols
- Processor unit: event with scrollDepth=0.85 → row persists with column value
- Consumer regression: v2 event with scroll_depth → no crash, field captured

### P2.5. Commits

- `test(user-interactions): RED tests for scroll_depth + metadata persistence`
- `feat(migrations): ADD COLUMN scroll_depth + metadata in user_interactions`
- `feat(user-interactions): persist scroll_depth and metadata from batch events`

### P2.6. Memory update

После мёрджа: `project_user_interactions_missing_cols.md` → status RESOLVED, добавить commit hashes.

**Max turns**: 60

---

### Phase P3: Cached Feed Scoring_Components Capture (1 day)

**Executed from**: `backend/feed-service`

**Goal**: когда feed ответ возвращается из Redis cache, в feed_items всё равно populate scoring_components.

### P3.1. Diagnosis

Сейчас feed-service либо:
(a) cache'ит только response payload (items array), без breakdown
(b) cache'ит payload + breakdown но breakdown теряется при conversion в response

Phase P0 определит точный механизм.

### P3.2. Fix (вариант A — cache breakdown tоо)

При первом запросе (cache miss):
```
1. call rec-system with include_breakdown=true
2. получить itemsDetailed with scoring_components
3. cache = {"items": [ids], "itemsDetailed": [full objects], "source": "personalized"}
4. write feed_items with scoring_components
5. return response
```

При повторном (cache hit):
```
1. read cache → имеем itemsDetailed
2. write feed_items with scoring_components (same data as first)
3. return response (X-Feed-Source: cached)
```

Cached path пишет ту же самую breakdown. Training dataset сохраняется consistency.

### P3.3. Fix (вариант B — cache invalidation policy change)

Если объём cache breakdown слишком большой — можно вообще отключить cache для feed requests когда нужно логировать (feature flag).

**Decision в Phase P3**: выбрать A (cache breakdown) после измерения размера.

### P3.4. Tests

- Integration: второй identical feed request → X-Feed-Source: cached, но feed_items populated with breakdown
- Cache size metric: ensure cache entries < 100KB avg (log warn if bigger)

### P3.5. Commits

- `test(feed-service): RED test for cached feed preserving scoring_components`
- `feat(feed-service): cache itemsDetailed alongside items for training data consistency`

**Max turns**: 80

---

### Phase P4: Frontend Mock Replacement (2–3 days)

**Executed from**: `frontend-app`

**Goal**: 5 mock repositories → ApiXxxRepository implementations.

### P4.1. Mocks to replace

| Mock | Target |
|---|---|
| mock_feed_repository.dart | ApiFeedRepository |
| mock_collections_repository.dart | ApiCollectionsRepository |
| mock_onboarding_repository.dart | ApiOnboardingRepository |
| mock_profile_repository.dart | ApiProfileRepository |
| mock_settings_repository.dart | ApiSettingsRepository (if backend endpoint exists, else skip) |

### P4.2. Each replacement

По паттерну ApiAuthRepository (уже существует как reference):
1. Create `api_xxx_repository.dart` implementing same interface as mock
2. Use Dio (configured with AuthTokenInterceptor) для calls
3. DTO mapping per API_CONTRACTS.md
4. Error handling (401 → refresh, 404 → mapped, 5xx → retry)
5. Unit tests with mocked Dio
6. Widget tests — feature still works with Api impl
7. Delete mock file after migration

### P4.3. Dependency injection

Riverpod providers — swap mock for api in `lib/providers/repositories.dart`:
```dart
final feedRepositoryProvider = Provider<FeedRepository>((ref) {
  // return MockFeedRepository();
  return ApiFeedRepository(ref.read(dioProvider));
});
```

### P4.4. Capture X-Request-Id

Each repository that calls /api/feed должна capture `X-Request-Id` from response headers и пропагировать в последующие interaction events. Frontend-app code уже имеет это в `ApiFeedRepository` per prior commits — verify working.

### P4.5. Tests

- Unit: each ApiXxxRepository — mock Dio, verify call + parsing
- Widget: feed screen renders items from API, like button sends event with feed_request_id

### P4.6. Commits

Per repository (5 × 2 commits = 10):
- `test(feature/X): RED tests for ApiXRepository`
- `feat(feature/X): ApiXRepository replaces MockXRepository`
- `chore(feature/X): remove mock_x_repository.dart`

**Max turns**: 160 (larger — 5 repositories)

---

### Phase P5: E2E Script Hardening (0.5 day)

**Executed from**: SKD root

**Goal**: `scripts/e2e_data_capture_test.sh` проходит end-to-end без manual интервенции.

### P5.1. Fixes

**Fix 1 — accessToken snake_case**:
```bash
# Current (BROKEN):
TOKEN=$(echo "$LOGIN_BODY" | jq -r '.accessToken')

# Fixed:
TOKEN=$(echo "$LOGIN_BODY" | jq -r '.access_token')
```

**Fix 2 — auto email verification in dev mode**:
After registration, before login:
```bash
# Dev-mode: auto-verify email by UPDATE in DB
$PSQL -c "UPDATE auth.users SET email_verified=true WHERE email='$TEST_EMAIL';" >/dev/null
```
Gated behind `--dev-mode` flag (default on if AUTH_DEV_MODE=true detected).

**Fix 3 — port-forward management**:
- Script uses GATEWAY=http://localhost:8080 but k3d default listens on node port 30080 OR through LB on a different port
- Add port-forward lifecycle (start → wait → cleanup trap)
- OR detect gateway URL automatically

**Fix 4 — cleanup on exit**:
Trap EXIT: kill port-forward PID, cleanup test user (DELETE FROM auth.users WHERE email LIKE 'e2e-test-%').

### P5.2. Tests

- Script runs successfully from clean state (fresh DB, fresh stack)
- All 10+ assertions pass
- Exit code 0
- No dangling port-forward processes after exit

### P5.3. Commits

- `fix(scripts): correct accessToken → access_token JSON key in e2e_data_capture_test.sh`
- `feat(scripts): add dev-mode auto email verification in E2E test`
- `feat(scripts): auto-manage kubectl port-forward lifecycle + cleanup trap`

### P5.4. Memory update

`project_e2e_script_accesstoken_bug.md` → RESOLVED.

**Max turns**: 50

---

### Phase P6: Kotlin Prometheus Metrics (1.5 days)

**Executed from**: `backend/` (orchestrator delegates per service)

**Goal**: все 6 Kotlin backend services экспонируют `/actuator/prometheus`.

### P6.1. Per service changes

1. Add dependency in `build.gradle.kts`:
   ```kotlin
   implementation("io.micrometer:micrometer-registry-prometheus")
   ```

2. Expose endpoint в `application.yml`:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: "health,info,prometheus,metrics"
     endpoint:
       prometheus:
         enabled: true
     metrics:
       tags:
         service: ${spring.application.name}
   ```

3. Custom meters for key operations:
   - **user-interactions-service**: `interactions_batch_count` (counter), `interactions_batch_duration` (timer)
   - **feed-service**: `feed_request_count{source="personalized|cached"}` (counter), `feed_request_duration` (timer)
   - **auth-service**: `auth_login_attempts{result="success|fail"}` (counter)
   - **api-gateway**: `gateway_requests_total{service,status}` (counter)

### P6.2. Tests

- Integration: GET /actuator/prometheus returns valid exposition
- Custom meter registered + incremented on expected operation

### P6.3. Commits per service

- `feat(observability): add micrometer-registry-prometheus + custom meters`

### P6.4. Memory update

`project_prometheus_kotlin_deferred.md` → RESOLVED.

**Max turns**: 120 (6 services)

---

### Phase P7: Liquibase Runtime Dependency (0.5 day)

**Executed from**: `backend/` (per-service)

**Goal**: переключить `liquibase-core` из `testImplementation` в `runtimeOnly` — Spring Liquibase auto-config заработает, migrations применяются at app startup, `scripts/apply-backend-migrations.sh` становится не обязательным.

### P7.1. Diagnose — правда ли testImplementation?

Phase P0 проверит. Если нет — scope P7 другой.

### P7.2. Change

```kotlin
// backend/<service>/build.gradle.kts
dependencies {
    // OLD: testImplementation("org.liquibase:liquibase-core")
    runtimeOnly("org.liquibase:liquibase-core")
    testImplementation("org.liquibase:liquibase-core")  // keep for tests too
}
```

Повторить для всех 5 services с миграциями (auth, users, interactions, subscription, feed).

api-gateway не имеет миграций — пропустить.

### P7.3. Verify

После rebuild: `unzip -l <app.jar> | grep liquibase-core` → должно найти.

### P7.4. Regression test

Start fresh service against empty DB → Spring Liquibase должен применить changelog автоматически, без `apply-backend-migrations.sh`.

### P7.5. Decision

- Если Spring Liquibase работает — script можно оставить как backup, но по default runs through Spring
- Document в CLAUDE.md

### P7.6. Commits per service

- `fix(build): liquibase-core as runtimeOnly for embedded Spring Liquibase`

**Max turns**: 60

---

### Phase P8: rec_config Caching + is_long_form Tune (0.5 day)

**Executed from**: `rec-system`

**Goal**: (a) cache rec_config в памяти с TTL, (b) скорректировать is_long_form threshold.

### P8.1. rec_config caching

```python
# rec-system/src/infrastructure/config/rec_config_loader.py
class RecConfigLoader:
    def __init__(self, session_factory, cache_ttl_seconds=60):
        self._session_factory = session_factory
        self._cache: dict[str, tuple[float, Any]] = {}  # key → (expiry_ts, value)
        self._ttl = cache_ttl_seconds

    async def get(self, key: str) -> Any:
        now = time.time()
        if key in self._cache:
            expiry, value = self._cache[key]
            if now < expiry:
                return value
        # fetch from DB
        value = await self._fetch(key)
        self._cache[key] = (now + self._ttl, value)
        return value

    def invalidate(self, key: str | None = None):
        if key: self._cache.pop(key, None)
        else: self._cache.clear()
```

### P8.2. is_long_form threshold

Current 300 words → only 0.9% content. Target: 5–10% of content should be long_form for меaningful filtering.

Analysis из corpus:
```sql
SELECT 
  percentile_cont(0.5) WITHIN GROUP (ORDER BY word_count) AS p50,
  percentile_cont(0.9) WITHIN GROUP (ORDER BY word_count) AS p90,
  percentile_cont(0.95) WITHIN GROUP (ORDER BY word_count) AS p95
FROM data_flow.posts_features;
```

Set threshold to p90 — ~10% of content becomes long_form. Likely 150-250 words for Telegram news.

### P8.3. Implementation

`src/domain/services/text_analyzer.py`:
```python
LONG_FORM_WORD_THRESHOLD = int(os.environ.get("REC_LONG_FORM_THRESHOLD", "200"))
```

### P8.4. Tests

- Unit: cache TTL expiry invalidates
- Unit: is_long_form at new threshold

### P8.5. Commits

- `feat(application): in-memory rec_config cache with 60s TTL`
- `fix(domain): lower is_long_form threshold from 300 to 200 words`

**Max turns**: 70

---

### Phase P9: Documentation Sync (1 day)

**Executed from**: каждый проект отдельно

**Goal**: все CLAUDE.md и API_CONTRACTS отражают actual state post-MVP.

### P9.1. rec-system/CLAUDE.md

- Implementation status table: "Logging / Monitoring" → DONE
- Add note: "/metrics endpoint available"
- Action types section — align with canonical 6

### P9.2. backend/CLAUDE.md

- Add "Data Capture Foundation" section (копировать из SKD root CLAUDE.md если там есть)
- Update feature ActionType canonical 6
- Add Prometheus endpoint info per service

### P9.3. frontend-app/API_CONTRACTS.md

- ActionType canonical 6
- X-Request-Id response header documented
- v2 interaction event fields

### P9.4. SKD root CLAUDE.md

- Уже обновлён с Data Capture Foundation section ✅
- Add hardening section

### P9.5. docs/feature_flags.md (rec-system)

Document политику флагов:
- LIVE_PROFILE: OFF — requires tuning (lower α + skip stable profiles)
- HOT_ARRIVAL: OFF — enable when parser supports priority='breaking'
- RERANK: OFF — NOT recommended (hurts broad users per M3 benchmark)

### P9.6. Commits per project

- `docs(CLAUDE): reflect data-capture + hardening deliverables`

**Max turns**: 50

---

### Phase P10: E2E Validation + Final Report (0.5 day)

**Executed from**: SKD root

**Goal**: убедиться что всё работает end-to-end; написать final report.

### P10.1. Build + deploy stack

```bash
cd /home/mattew/SKD
docker compose build --parallel api-gateway auth-service user-service \
  user-interactions-service subscription-service feed-service rec-worker

for svc in api-gateway auth-service user-service user-interactions-service \
           subscription-service feed-service; do
  k3d image import "skd-${svc}:latest" -c skd
  kubectl rollout restart deployment/$svc -n skd
done
docker compose -f docker-compose.ml.yml up -d --force-recreate rec-worker

# migrations
./scripts/apply-backend-migrations.sh  # still works as backup
# OR: after P7, Spring Liquibase does it automatically on pod startup
```

### P10.2. Run E2E

```bash
./scripts/e2e_data_capture_test.sh
# Expect: exit 0, no manual interventions needed
```

All assertions должны pass:
- Registration → verified user (dev-mode auto)
- Login → JWT token
- Feed request → X-Request-Id + X-Feed-Source headers
- feed_requests row written
- feed_items × 10 with scoring_components
- LIKE event → persisted with feed_request_id + scroll_depth + metadata
- Money JOIN → returns training pair row with LIKE

### P10.3. Regression sanity

- rec-system persona benchmark smoke (3 personas × 1 run × no LLM) — make sure nothing broke
- Check no existing tests regressed

### P10.4. Update memory

All 5 known issue files:
- `project_interactions_consumer_bug.md` → already RESOLVED (from data-capture)
- `project_user_interactions_missing_cols.md` → RESOLVED (P2)
- `project_action_type_contract_mismatch.md` → RESOLVED (P1)
- `project_e2e_script_accesstoken_bug.md` → RESOLVED (P5)
- `project_prometheus_kotlin_deferred.md` → RESOLVED (P6)

MEMORY.md reflect статусы.

### P10.5. Final report

`.claude/artifacts/mvp-hardening/final_report.md`:
- Per-phase summary: commits, tests, cost
- Before/after state table
- All 5 memory items resolved
- Known remaining debt (Tier F, G deferred items)
- Recommended next: soft launch с real users

**Max turns**: 70

---

## 6. Artifacts Structure

```
/home/mattew/SKD/.claude/artifacts/mvp-hardening/
├── hardening_state.md            # append-only tracker
├── as_is_report.md               # P0 output
├── phase_{P0..P10}_prompt.txt    # dispatched prompts
├── final_report.md               # P10 synthesis
└── e2e_test_output.txt           # P10 test run
```

---

## 7. Testing Strategy

Identical к data-capture project:
- rec-system: `unit | integration | llm | slow` markers
- backend: `@Test | @IntegrationTest`
- frontend-app: `unit | widget | integration`

CI gate before any commit merging:
- Project-specific test suite green
- No regression in existing tests
- New test coverage ≥ 85%

---

## 8. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ActionType migration breaks existing test data | Low | Medium | Legacy mapping in enum; test data truncate if needed |
| Frontend mock replacement introduces UI regression | Medium | Medium | Widget tests; manual QA checklist per feature |
| Cache breakdown increases Redis memory | Medium | Low | Monitor size; TTL shorter if needed |
| Liquibase runtime vs external Job conflict | Low | High | Keep external script as backup; Spring Liquibase first, fallback to script |
| rec_config cache stale after config update | Low | Low | 60s TTL — acceptable for non-realtime config |
| E2E script fix breaks on non-dev-mode environment | Low | Medium | dev-mode flag explicit, defaults to detection |
| Kotlin Prometheus metrics add JVM overhead | Low | Low | Micrometer is mature, overhead < 1% CPU |

---

## 9. Launch Command

```bash
cd /home/mattew/SKD && claude
```

Внутри сессии — промпт из §10 ниже.

---

## 10. Orchestrator Launch Prompt

Передать orchestrator'у точно этот текст:

```
Запускаем проект "MVP Hardening & Cleanup".

📋 Спека: /home/mattew/SKD/mvp-hardening-spec.md
📋 Предыдущие проекты (reference):
   - /home/mattew/SKD/rec-system-eval-harness-spec.md
   - /home/mattew/SKD/rec-system-feature-delivery-spec.md
   - /home/mattew/SKD/data-capture-spec.md

Прочти всю спеку hardening, особое внимание:
- §0 Context (tier разбивка всех known issues)
- §4 Orchestration Model (тот же паттерн что прошлых трёх проектов)
- §5 Phases (P0–P10, 10 фаз)
- §5.0 Phase dependency graph (что можно параллелить)

Протокол:

1. Инициализируй tracker в /home/mattew/SKD/.claude/artifacts/mvp-hardening/hardening_state.md
2. Создай ветки feat/mvp-hardening в трёх проектах (rec-system, frontend-app, backend/<каждый service>)
3. Phase P0 первой — AS-IS verification. Подтверди 9 предположений из спеки.
4. Далее выполняй фазы по dependency graph:
   - P1 (action_type) → затем P4 (frontend mocks зависят от P1 canonical set)
   - P2, P3, P5, P6, P7, P8, P9 — независимы, можно параллелить
   - P10 — последней, E2E + final report
5. После каждой phase: verify per §5.5 из eval-harness spec (7 шагов):
   - parse JSON
   - verify commits via git log
   - smoke test project tests green
   - artifacts exist
   - update hardening_state.md
   - commit tracker в SKD root
   - show summary
6. НЕ мёрджь ветки автономно — только с моего явного approval.
7. Stop на:
   - BLOCKED / FAILED с >3 retry
   - Regression в existing tests
   - Cost budget 80% ($72)
   - Любой риск data loss

Автономно per autonomous_to_done memory. Я мониторю tracker между фазами.

КРИТИЧНО для P1 (ActionType):
- Canonical set: IMPRESSION, OPEN, CLOSE, LIKE, DISLIKE, BOOKMARK
- Backend keeps legacy mapping (VIEW→IMPRESSION, CLICK→OPEN, SCROLL_PAST→CLOSE, SAVE→BOOKMARK, HIDE→DISLIKE, SHARE→BOOKMARK)
- Frontend эмит только canonical
- rec-system signal weights unchanged (уже на canonical)

КРИТИЧНО для P4 (Frontend mocks):
- Паттерн по ApiAuthRepository — уже существует как reference
- Каждый repo: separate RED/GREEN commits + delete mock file
- Capture X-Request-Id в ApiFeedRepository (уже частично сделано)

КРИТИЧНО для P7 (Liquibase):
- НЕ ломать существующий apply-backend-migrations.sh — он остаётся как backup
- Добавить Spring Liquibase как primary, skрипт как fallback
- Verify в regression: fresh DB start → migrations apply automatically

После P10:
- Покажи мне final_report.md
- Жди моё "мёрджим" перед merge всех feat/mvp-hardening веток
- Порядок merge (после approval):
  1. backend user-interactions + feed-service (P2 migration + P3 cache + P6 metrics + P7 liquibase)
  2. backend api-gateway + auth + user + subscription (P6 metrics + P7 liquibase)
  3. rec-system (P1 canonical action types + P8 cache + threshold)
  4. frontend-app (P1 InteractionAction + P4 repositories)
  5. SKD root (P5 e2e script)

Финальные memory updates (после merge):
- project_interactions_consumer_bug.md → уже RESOLVED
- project_user_interactions_missing_cols.md → RESOLVED (P2)
- project_action_type_contract_mismatch.md → RESOLVED (P1)
- project_e2e_script_accesstoken_bug.md → RESOLVED (P5)
- project_prometheus_kotlin_deferred.md → RESOLVED (P6)

Post-merge regression:
1. Rebuild всех 7 сервисов (6 backend + rec-worker)
2. k3d image import + rollout restart
3. Run E2E test — MUST pass без manual interventions
4. Если E2E fail — spawn targeted fix phase, НЕ откатывать мёрджи

Поехали. Старт с Phase P0.
```

---

## 11. Expected Outcomes

После завершения проекта:

### Tier A — полностью закрыт
- Real user LIKE → persisted, rec-system EMA updates, recommendations evolve
- Frontend 5 repositories → real API calls через gateway

### Tier B — полностью закрыт
- scroll_depth + metadata columns present, populated
- Cached feed responses retain scoring_components — training data complete

### Tier C — частично закрыт
- 6 Kotlin services экспортят Prometheus /actuator/prometheus
- Grafana deploy — defer (post-launch)
- Alerting — defer (post-launch)

### Tier D — полностью закрыт
- E2E script runs clean
- Deploy scripts documented with known workarounds

### Tier E — частично закрыт
- E3 (Liquibase runtime) ✅
- E4 (CLAUDE.md sync) ✅
- E5 (rec_config cache) ✅
- E1, E2, E6 — deferred

### Tier F — минимально закрыт
- F4 (is_long_form threshold) ✅
- F1, F2, F3 — deferred (ML projects)

### Готовность к soft launch

После этого проекта система **готова принять real users**:
- Все training signals корректно capture'ятся
- No data loss через контрактные mismatch'и
- Observability достаточная чтобы видеть что происходит
- E2E test автоматизирован — можно gate deployments
- Tech debt очищен

---

## 12. Functional Acceptance Test (Phase P10)

```bash
#!/bin/bash
# scripts/e2e_full_stack_test.sh — comprehensive post-hardening E2E

set -e
GATEWAY="${GATEWAY:-http://localhost:28080}"
PSQL="kubectl exec -n skd postgres-0 -- psql -U postgres -d content_agg_db -tA"

# Setup: auto port-forward
kubectl port-forward -n skd svc/api-gateway 28080:8080 >/dev/null 2>&1 &
PF=$!
trap "kill $PF 2>/dev/null" EXIT
sleep 3

# 1. Registration (dev-mode auto-verify)
EMAIL="e2e-$(date +%s)@test.local"
curl -sf -X POST "$GATEWAY/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Pass123!\"}" >/dev/null
$PSQL -c "UPDATE auth.users SET email_verified=true WHERE email='$EMAIL';" >/dev/null
echo "✓ User registered + verified (dev-mode)"

# 2. Login
TOKEN=$(curl -sf -X POST "$GATEWAY/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Pass123!\"}" | jq -r '.access_token')
[ -n "$TOKEN" ] || { echo "✗ Login failed"; exit 1; }
echo "✓ JWT obtained"

# 3. Onboarding
curl -sf -X POST "$GATEWAY/api/onboarding" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"categories":["технологии","наука","бизнес"]}' >/dev/null
echo "✓ Onboarding complete"

# 4. Feed with X-Request-Id
RESP=$(curl -sf -i "$GATEWAY/api/feed?count=10" -H "Authorization: Bearer $TOKEN")
REQ_ID=$(echo "$RESP" | grep -i 'X-Request-Id:' | awk '{print $2}' | tr -d '\r')
FEED_SOURCE=$(echo "$RESP" | grep -i 'X-Feed-Source:' | awk '{print $2}' | tr -d '\r')
[ -n "$REQ_ID" ] || { echo "✗ No X-Request-Id"; exit 1; }
echo "✓ Feed returned ($FEED_SOURCE, request_id=$REQ_ID)"

# 5. Verify logging
CNT=$($PSQL -c "SELECT COUNT(*) FROM feed.feed_requests WHERE request_id='$REQ_ID';")
[ "$CNT" = "1" ] || { echo "✗ feed_requests not written"; exit 1; }
CNT=$($PSQL -c "SELECT COUNT(*) FROM feed.feed_items WHERE request_id='$REQ_ID' AND scoring_components IS NOT NULL;")
[ "$CNT" -ge "1" ] || { echo "✗ feed_items without scoring_components"; exit 1; }
echo "✓ feed_requests + feed_items persisted with scoring_components"

# 6. Pick item at position 3
POS3=$($PSQL -c "SELECT content_id FROM feed.feed_items WHERE request_id='$REQ_ID' AND position=3;")

# 7. Send LIKE event (CANONICAL action type)
EVID=$(uuidgen)
curl -sf -X POST "$GATEWAY/api/interactions/batch" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"events\":[{\"event_id\":\"$EVID\",\"content_id\":\"$POS3\",\"action_type\":\"LIKE\",\"timestamp\":\"$(date -u +%FT%TZ)\",\"feed_request_id\":\"$REQ_ID\",\"position_in_feed\":3,\"scroll_depth\":0.85,\"metadata\":{\"source\":\"e2e\"}}]}" >/dev/null
sleep 3

# 8. Verify persistence with new fields
ROW=$($PSQL -c "SELECT action_type || '|' || scroll_depth || '|' || (metadata->>'source') FROM interactions.user_interactions WHERE feed_request_id='$REQ_ID' AND content_id='$POS3';")
[ "$ROW" = "LIKE|0.85|e2e" ] || { echo "✗ Interaction not persisted correctly: '$ROW'"; exit 1; }
echo "✓ LIKE event persisted with scroll_depth=0.85 and metadata.source=e2e"

# 9. The money JOIN
COUNT=$($PSQL -c "
  SELECT COUNT(*) FROM feed.feed_requests fr
  JOIN feed.feed_items fi ON fi.request_id = fr.request_id
  JOIN interactions.user_interactions ui 
    ON ui.feed_request_id = fr.request_id AND ui.content_id = fi.content_id
  WHERE fr.request_id = '$REQ_ID' AND ui.action_type = 'LIKE';
")
[ "$COUNT" = "1" ] || { echo "✗ Money JOIN failed"; exit 1; }
echo "✓ Money JOIN successful — training pair captured"

# 10. Cleanup
$PSQL -c "DELETE FROM auth.users WHERE email='$EMAIL';" >/dev/null
echo ""
echo "✓✓✓ ALL ASSERTIONS PASSED"
```

Этот тест проходит без manual интервенций после Phase P10.

---

## End of specification

**SKD orchestrator**: прочти §4 Orchestration Model + §5 Phases end-to-end, затем:

1. Создай tracker и ветки
2. Phase P0 — AS-IS verify
3. Выполни P1–P9 (можно параллельно где графа позволяет)
4. Phase P10 — E2E validation + final report
5. Жди user approval на merge

Принципы:
- **Backward compat**: legacy ActionType names accepted by backend; existing feed_items без scoring_components не ломаются
- **Zero downtime**: rolling restart per service; migrations safe (ADD COLUMN IF NOT EXISTS NULLABLE)
- **Reversible**: каждая миграция имеет downgrade path
- **Testable**: E2E script из §12 MUST pass в финале

Build stable. Ship confidently. Technical debt erased.
