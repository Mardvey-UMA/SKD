# Technical Specification: MVP Data Capture Foundation

**Project**: cross-service (backend + rec-system + frontend-app)
**Version**: 1.0
**Created**: 2026-04-19
**Target root**: `/home/mattew/SKD/`
**Estimated effort**: 10–14 рабочих дней (1 engineer, with parallelism 6–8 calendar days)
**Execution mode**: SKD orchestrator → per-phase fresh `claude -p` sub-sessions
**Goal**: сбор логов и контекста достаточных для будущего дообучения рекомендательной системы на **реальных данных** пользователей. MVP-ready без over-engineering.

---

## 0. Context (MUST READ before starting)

### Связь с предыдущими проектами

Два проекта уже завершены на этой платформе:

1. **Eval harness** (`/home/mattew/SKD/rec-system-eval-harness-spec.md`) — построен benchmark фреймворк с 20 персонами, LLM-as-judge, 8 proxy metrics. Merged в master.
2. **Feature delivery** (`/home/mattew/SKD/rec-system-feature-delivery-spec.md`) — реализованы Phase A (live profile), Phase B (hot arrival), Phase C (cross-encoder). Все merge'нуты с default флагами OFF. Результат: aggregate gains не статзначимы на persona benchmark, поэтому production defaults = OFF.

Этот проект — **инфраструктурная база** для того чтобы следующие итерации оптимизации работали на **реальных пользовательских данных**, а не на синтетических персонах.

### AS-IS состояние (будет проверено в Phase P0)

Предположения, основанные на CLAUDE.md файлах и кодовой базе. Phase P0 подтверждает или опровергает:

#### Backend платформа (`/home/mattew/SKD/backend/`)
- api-gateway (WebFlux Kotlin, :8080) — JWT проверка, HMAC signature, rate limiting. **Предположение**: X-Request-Id middleware отсутствует или не пропагирует в downstream.
- auth-service :8081, user-service :8082, subscription-service :8084 — работают с outbox pattern для Kafka events.
- user-interactions-service :8083 — принимает `POST /api/interactions/batch`, persist'ит в `interactions.user_interactions` (monthly partitioned), публикует в Kafka topic `user.interactions.batch`.
- feed-service :8085 — принимает `POST /api/feed`, вызывает rec-system `/recommendations`, кэширует в Redis/Valkey.

#### rec-system (`/home/mattew/SKD/rec-system/`)
- FastAPI :8000. Clean Architecture (domain / application / infrastructure / presentation).
- Consumer group `rec-system-interactions` читает `user.interactions.batch` → `HandleInteractionsBatchUseCase`.
- **Известный bug** (в memory `project_interactions_consumer_bug.md`): float vs ISO string mismatch в timestamp — консьюмер падает при определённом формате. **Must fix в Phase P1.**
- `scoring_service.py` вычисляет 6-component scoring. Компоненты НЕ экспонируются наружу (только final_score).
- `ScoringExplainer` из Phase 8 eval-harness проекта УЖЕ умеет вернуть breakdown через `/recommendations/explain` dev-endpoint. Это можно переиспользовать.

#### frontend-app (`/home/mattew/SKD/frontend-app/`)
- Flutter. Использует mock-репозитории, но часть уже подключена к API (per user statement).
- `POST /api/interactions/batch` — contract в `frontend-app/API_CONTRACTS.md`. Текущий payload включает event_id, content_id, action_type, duration_ms.
- **Предположение**: отсутствует поле `feed_request_id` и `position_in_feed` в interaction payload.

#### База данных (shared `content_agg_db`)
- Schema `interactions.user_interactions` (monthly partitioned by `created_at`)
- Schema `feed.user_bookmarks`, `user_likes`, `user_dislikes` (NO `feed_requests` table yet)
- Schema `data_flow.*` — rec-system owned
- **Предположение**: ни `feed.feed_requests`, ни `feed.feed_items` не существуют.

### Что ломать нельзя

Контракты которые сейчас работают и МЕНЯТЬ их стоимостью обратной совместимости:

- Kafka topic `user.interactions.batch` schema — используется rec-system consumer'ом. Новые поля только OPTIONAL.
- `POST /api/interactions/batch` body contract — используется frontend'ом. Новые поля только OPTIONAL.
- `POST /recommendations` response — используется feed-service. НЕ МЕНЯТЬ формат, только extend заголовками.
- Existing API контракты в `frontend-app/API_CONTRACTS.md` — только дополнять, не переопределять.
- DB schemas — ALTER TABLE с NULLABLE + DEFAULT, никаких NOT NULL без миграции данных.
- Existing scoring formula и её outputs — НЕ ТРОГАТЬ. Только `ScoringExplainer` экспонирует breakdown опционально.
- Background jobs rec-worker'а (content_processing, profile_update, cold_start_refresh, history_cleanup) — не изменять кадансы и логику.

---

## 1. Goals

1. **Chain tracing end-to-end**: request_id проходит через gateway → feed-service → rec-system и возвращается в interaction events чтобы link'ать recommendation ↔ behavior.
2. **Full recommendation context capture**: для каждого feed request сохранить profile snapshot + scoring breakdown per item + retrieval stats в persistent storage.
3. **Extended interaction events**: frontend отправляет `feed_request_id` + `position_in_feed` в каждом interaction event, consumer персистит.
4. **Fix blocker bug**: InteractionsBatchConsumer timestamp crash.
5. **Structured logging** в JSON format во всех сервисах для debugging в production.
6. **Health + basic metrics endpoints** в rec-system и feed-service.
7. **Backward compatibility strict**: ни один существующий контракт не ломается. Флаги feature-flag'ованные если behavior меняется.
8. **Post-delivery**: будущий `scripts/eval_on_real_data.py` сможет JOIN'ом собрать полный training dataset.

## 2. Non-goals (explicit)

- ❌ NOT реализуем Grafana dashboards / Prometheus scraping infrastructure — minimal `/metrics` endpoint достаточно.
- ❌ NOT меняем scoring logic, retrieval algorithm, NLP pipeline.
- ❌ NOT включаем никакой feature flag из предыдущего проекта (LIVE_PROFILE, HOT_ARRIVAL, RERANK остаются OFF).
- ❌ NOT создаём generic event_log таблицу для всех событий — специфичные outbox patterns уже есть и работают.
- ❌ NOT логируем full candidate pool (500 items per request = bloat). Только top-N возвращённых.
- ❌ NOT делаем A/B testing infrastructure как отдельный проект — но оставляем поле `ab_bucket` (default 0) в схемах для future use.
- ❌ NOT трогаем parser-service, dedup-system, config-service, content-aggregator-service — не по scope'у логов.
- ❌ NOT внедряем APM / tracing (Jaeger, Zipkin) — слишком сложно для MVP. Только structured logs + request_id.
- ❌ NOT переписываем outbox pattern в существующих сервисах (auth, user, subscription) — работает, не трогаем.

## 3. Success Criteria (acceptance)

Orchestrator MUST deliver:

- [ ] Все 12 фаз выполнены, коммиты verified
- [ ] **Functional E2E acceptance test проходит** (см. Phase P12)
- [ ] `data_flow.posts_features` и production rec-system продолжают работать идентично master'у (backward compat)
- [ ] Существующие test suites зелёные во всех трёх проектах:
  - rec-system: `uv run pytest` — 94+ tests green
  - backend services: `./gradlew test` — все service test suites green
  - frontend-app: `flutter test` — все widget/unit tests green
- [ ] Новые tests добавлены:
  - rec-system: ≥ 15 unit + 3 integration
  - backend services: ≥ 20 unit + 5 integration (суммарно)
  - frontend-app: ≥ 8 unit + 2 widget
- [ ] InteractionsBatchConsumer bug **больше не воспроизводим** — regression test присутствует.
- [ ] E2E: feed request → item click → SQL JOIN по request_id успешно возвращает full training pair.
- [ ] Документация обновлена: `frontend-app/API_CONTRACTS.md`, `backend/CLAUDE.md`, `rec-system/CLAUDE.md` отражают новые поля.
- [ ] Final report: `.claude/artifacts/data-capture/final_report.md` описывает что сделано, deployed config, какие followups остались.

### Functional acceptance test

Must pass at end of Phase P12:

```bash
# 1. Spin up stack (assume all services deployed)
# 2. Create test user via API
curl -X POST $GATEWAY/api/auth/register -d '{"email":"test@test","password":"..."}'
# 3. Onboard
curl -X POST $GATEWAY/api/onboarding -d '{"categories":["технологии","наука"]}' -H "Authorization: Bearer $TOKEN"

# 4. Request feed — response includes X-Request-Id header
RESPONSE=$(curl -i $GATEWAY/api/feed?count=10 -H "Authorization: Bearer $TOKEN")
REQUEST_ID=$(echo "$RESPONSE" | grep -i 'X-Request-Id:' | awk '{print $2}' | tr -d '\r')
ITEM_AT_POSITION_3=$(echo "$RESPONSE" | jq -r '.items[2].content_id')

# 5. Verify feed_requests + feed_items were written
psql -c "SELECT source, count_returned, profile_snapshot->>'interaction_count'
         FROM feed.feed_requests WHERE request_id='$REQUEST_ID';"
# expect: 1 row with source='personalized' and count_returned=10
psql -c "SELECT position, scoring_components FROM feed.feed_items
         WHERE request_id='$REQUEST_ID' ORDER BY position;"
# expect: 10 rows with scoring_components JSONB populated

# 6. Simulate interaction — user clicks item at position 3
curl -X POST $GATEWAY/api/interactions/batch -H "Authorization: Bearer $TOKEN" -d "{
  \"events\": [{
    \"event_id\": \"$(uuidgen)\",
    \"content_id\": \"$ITEM_AT_POSITION_3\",
    \"action_type\": \"LIKE\",
    \"timestamp\": \"$(date -u +%FT%TZ)\",
    \"feed_request_id\": \"$REQUEST_ID\",
    \"position_in_feed\": 3
  }]
}"

# 7. Wait for consumer to process
sleep 5

# 8. Verify JOIN works — the money query
psql -c "SELECT fr.user_id, fi.position, fi.content_id, fi.scoring_components,
                ui.event_type
         FROM feed.feed_requests fr
         JOIN feed.feed_items fi ON fi.request_id = fr.request_id
         LEFT JOIN interactions.user_interactions ui
              ON ui.feed_request_id = fr.request_id
              AND ui.content_id = fi.content_id
         WHERE fr.request_id = '$REQUEST_ID'
         ORDER BY fi.position;"
# expect: 10 rows, one with event_type='LIKE' at position=3, others NULL
```

Если этот запрос возвращает связанные данные — проект успешен.

## 4. Orchestration Model

Паттерн тот же что и в предыдущих двух проектах. См. §5 `rec-system-eval-harness-spec.md` и §4 `rec-system-feature-delivery-spec.md` для полных деталей. Краткая сводка:

### 4.1. Progress tracker
`/home/mattew/SKD/.claude/artifacts/data-capture/orchestration_state.md` (append-only).

### 4.2. Fresh sub-claude per phase
Каждая фаза = один `claude -p` вызов из **соответствующей project directory**:

- Phases P0, P2 (design), P12 (validation) — из SKD root (`/home/mattew/SKD/`)
- Phases касающиеся rec-system — из `/home/mattew/SKD/rec-system/`
- Phases касающиеся backend — из `/home/mattew/SKD/backend/` (two-level delegation: backend orchestrator → per-service sub-claude)
- Phases касающиеся frontend — из `/home/mattew/SKD/frontend-app/`

### 4.3. Prompt template
Identical to previous projects (§5.3 of eval-harness spec). Каждый prompt содержит:
1. Progress summary (что сделано)
2. Phase section из этого spec (дословно)
3. Execution rules (TDD, uv run / gradle / flutter, branch)
4. References
5. Return format `### PHASE {X} RESULT`

### 4.4. Cost budget
- Per phase: **$20** cap
- Total: **$120** cap
- Alert на 80% ($96)

### 4.5. Branch strategy

Каждый проект имеет свою ветку:
- `rec-system`: `feat/mvp-data-capture`
- `backend`: `feat/mvp-data-capture` (в каждом service отдельный submodule если нужно)
- `frontend-app`: `feat/mvp-data-capture`

Все три merge'атся на master независимо после approval пользователя. Никаких auto-merge.

### 4.6. Phase dependency graph

```
                        ┌──────────────────┐
                        │ P0 (AS-IS verify)│
                        └────────┬─────────┘
                                 │
                  ┌──────────────┴──────────────┐
                  ▼                             ▼
       ┌──────────────────┐         ┌──────────────────────┐
       │ P1 (consumer bug)│         │ P2 (design schemas)  │
       │   [rec-system]   │         │      [SKD root]      │
       └────────┬─────────┘         └──────────┬───────────┘
                │                              │
                │         ┌────────────────────┼────────────────────┐
                │         ▼                    ▼                    ▼
                │  ┌──────────────┐  ┌────────────────────┐  ┌──────────────────┐
                │  │ P3 gateway   │  │ P4 feed.feed_reqs  │  │ P5 ui extensions │
                │  │ request_id   │  │   migration        │  │   migration      │
                │  │[api-gateway] │  │  [feed-service]    │  │[user-interact]   │
                │  └──────┬───────┘  └──────────┬─────────┘  └──────────┬───────┘
                │         │                     │                       │
                │         │                     ▼                       │
                │         │          ┌──────────────────────┐           │
                │         │          │ P6 feed-service log  │           │
                │         │          │  [feed-service]      │           │
                │         │          └──────────┬───────────┘           │
                │         │                     │                       │
                │         │                     ▼                       │
                │         │          ┌──────────────────────┐           │
                │         │          │ P7 rec-system log    │           │
                │         │          │   [rec-system]       │           │
                │         │          └──────────┬───────────┘           │
                │         │                     │                       │
                │         └─────────┐           │                       │
                │                   ▼           │                       │
                │         ┌──────────────────────┐           ┌──────────▼───────────┐
                │         │ P8 Kafka schema ext  │           │ P9 frontend batch    │
                │         │ [user-int + rec]     │           │  [frontend-app]      │
                │         └──────────┬───────────┘           └──────────┬───────────┘
                │                    │                                  │
                │                    └──────────────┬───────────────────┘
                │                                   │
                │                                   ▼
                │                    ┌────────────────────────────┐
                │                    │ P10 structured JSON logs   │
                │                    │  [all services, parallel]  │
                │                    └────────────┬───────────────┘
                │                                 │
                │                                 ▼
                │                    ┌────────────────────────────┐
                │                    │ P11 health/metrics endpts  │
                │                    │ [rec-system + feed-service]│
                │                    └────────────┬───────────────┘
                │                                 │
                └─────────────────────────────────┤
                                                  ▼
                                       ┌───────────────────────┐
                                       │ P12 E2E validation    │
                                       │   [SKD root]          │
                                       └───────────────────────┘
```

Parallelization opportunities (orchestrator decides):
- P1 параллельно с P2, P3
- P3, P4, P5 все трое параллельно (разные сервисы)
- P6, P7 последовательно (P7 требует сериализации через P6)
- P10 параллельно всему (independent)
- P11 параллельно P8/P9/P10

Для простоты можно **выполнять последовательно** без parallelism — тратим лишние 2 дня, но меньше cognitive load.

---

## 5. Phases (ordered execution)

### Phase P0: AS-IS Verification (0.5 day)

**Goal**: подтвердить или опровергнуть все предположения из §0. Создать AS-IS отчёт чтобы следующие фазы работали по фактам.

**Executed from**: SKD root `/home/mattew/SKD/`

**Steps** (выполняются orchestrator'ом через sub-claude запросы в каждый проект):

1. **api-gateway**: проверить существует ли любой request-id middleware. Проверить пропагацию X-Request-Id или X-Correlation-Id в downstream. Проверить response headers.
   ```bash
   grep -rn -i 'request.?id\|correlation.?id\|trace.?id' backend/api-gateway/src/main/
   ```

2. **user-interactions-service**: проверить schema `interactions.user_interactions` — какие колонки уже есть.
   ```sql
   SELECT column_name, data_type, is_nullable FROM information_schema.columns 
   WHERE table_schema='interactions' AND table_name='user_interactions' ORDER BY ordinal_position;
   ```
   Найти Kafka publisher — что пишет в `user.interactions.batch` topic. Проверить Avro/Protobuf/JSON schema.

3. **feed-service**: проверить текущий flow `POST /api/feed` — вызов rec-system, Redis cache логика. Найти где можно hook'нуть запись в `feed_requests`.
   ```bash
   find backend/feed-service/src/main/ -name '*.kt' | xargs grep -l 'recommend\|feed'
   ```

4. **rec-system**: проверить где в `GenerateFeedUseCase` можно вызвать ScoringExplainer для получения breakdown. Проверить DI wiring `src/infrastructure/container.py`. Проверить что Kafka consumer (`handle_interactions_batch`) корректно обрабатывает новые optional fields в payload (должен быть лояльным).

5. **frontend-app**: 
   - Прочитать `frontend-app/API_CONTRACTS.md` секцию про `POST /api/interactions/batch`
   - Найти DTO для interaction event
   - Проверить где батчинг (каждые 30s / 50 events) — существует ли реальная реализация или TODO?
   - Проверить: вызывается ли уже `/api/feed` или всё ещё mock?

6. **Consumer bug reproduction**:
   - Прочитать memory `project_interactions_consumer_bug.md`
   - Воспроизвести bug в unit test (RED) — этот тест затем станет регрессионным в Phase P1
   - Документировать точное место падения (файл, строка, трейс)

**Deliverables**:
- `.claude/artifacts/data-capture/as_is_report.md` — **живой документ**, заполняется sub-claude'ами. Содержит:
  - Точные schema definitions
  - Найденные gaps vs предположениями
  - Любые сюрпризы (например, X-Request-Id уже частично реализован, или user_interactions уже имеет одно из нужных полей)
  - Путь к consumer bug reproduction test

**Acceptance**:
- Отчёт существует, покрывает все 6 пунктов выше
- Consumer bug воспроизведён (failing test) в unit tests rec-system

**No commits expected** (discovery only).

**Max turns**: 80

---

### Phase P1: Fix InteractionsBatchConsumer Timestamp Bug (1–2 days)

**Executed from**: `/home/mattew/SKD/rec-system/`

**Goal**: починить краш консьюмера при float timestamp'ах. Обеспечить обратную совместимость (и ISO string, и float должны парситься).

### P1.1. Context

Known bug (per user memory):
- Producer (user-interactions-service, Kotlin) отправляет timestamp в Kafka payload.
- Consumer (rec-system, Python) парсит как ISO 8601 string.
- **Когда producer отправляет float** (epoch seconds или milliseconds) — consumer падает при `datetime.fromisoformat()`.
- Bug был reproduced в Phase P0.

### P1.2. Fix strategy

**Robust parser** в consumer: принимать и ISO string, и float/int.

```python
# src/infrastructure/messaging/timestamp_parser.py (NEW)
from datetime import datetime, timezone

def parse_event_timestamp(value: str | int | float) -> datetime:
    """Parse timestamp from Kafka payload. Accepts:
    - ISO 8601 string: '2026-04-19T12:34:56.789Z'
    - Epoch seconds as float/int: 1745066096.789
    - Epoch milliseconds as float/int: 1745066096789.0
    """
    if isinstance(value, str):
        return datetime.fromisoformat(value.replace('Z', '+00:00'))
    if isinstance(value, (int, float)):
        # Heuristic: if > 10^12, it's milliseconds; else seconds
        if value > 10**12:
            return datetime.fromtimestamp(value / 1000, tz=timezone.utc)
        return datetime.fromtimestamp(value, tz=timezone.utc)
    raise ValueError(f"Cannot parse timestamp: {value!r}")
```

Заменить все `datetime.fromisoformat(...)` calls в `src/presentation/consumers/` и `src/application/use_cases/handle_interactions_batch.py` на вызов этого парсера.

### P1.3. Tests

**Unit** (`tests/unit/infrastructure/messaging/test_timestamp_parser.py`):
- ISO string with Z suffix
- ISO string with +00:00 offset
- Epoch seconds int (e.g. 1745066096)
- Epoch seconds float (e.g. 1745066096.789)
- Epoch milliseconds (e.g. 1745066096789)
- Invalid input → ValueError

**Integration** (`tests/integration/messaging/test_interactions_consumer_robustness.py`):
- Publish Kafka message with float timestamp → consumer does NOT crash, processes event
- Publish Kafka message with ISO string → consumer processes event (backward compat)
- Publish with invalid timestamp → consumer logs error, skips message, does NOT crash

### P1.4. Acceptance

- All new tests pass
- Existing `test_handle_interactions_batch.py` tests still pass
- Manual: publish both formats in dev environment — consumer processes both
- Memory artifact `project_interactions_consumer_bug.md` can be archived as resolved

### P1.5. Commits

- `test(messaging): RED test reproducing float timestamp crash`
- `feat(messaging): robust timestamp parser accepting ISO string and epoch float/int`
- `fix(consumers): use robust timestamp parser in interactions batch consumer`
- `test(integration): end-to-end consumer robustness test for timestamp formats`

**Max turns**: 70

---

### Phase P2: Design Event Schemas + Migrations (1 day)

**Executed from**: SKD root (design-only, no code)

**Goal**: зафиксировать **точные схемы** для всех новых артефактов, чтобы реализующие фазы не изобретали несовместимые форматы. Deliverable — один документ с canonical SQL/JSON schemas.

### P2.1. Deliverables

`.claude/artifacts/data-capture/contracts.md` содержащий:

#### Schema A: `feed.feed_requests` (Liquibase changeset для backend/feed-service)

```sql
CREATE TABLE feed.feed_requests (
    request_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    page_number SMALLINT NOT NULL DEFAULT 1,
    source VARCHAR(32) NOT NULL CHECK (source IN ('personalized','cold_start','cached','fallback')),
    count_requested SMALLINT NOT NULL,
    count_returned SMALLINT NOT NULL,
    latency_ms INTEGER,
    latency_breakdown JSONB,
    feature_flags JSONB,
    ab_bucket SMALLINT NOT NULL DEFAULT 0,
    app_version VARCHAR(32),
    device_type VARCHAR(32)
) PARTITION BY RANGE (requested_at);

-- Initial monthly partition + trigger для auto-create (reuse user_interactions pattern)
CREATE INDEX idx_feed_requests_user_requested ON feed.feed_requests (user_id, requested_at DESC);
```

#### Schema B: `feed.feed_items`

```sql
CREATE TABLE feed.feed_items (
    request_id UUID NOT NULL,
    position SMALLINT NOT NULL,
    content_id UUID NOT NULL,
    raw_content_id UUID,
    final_score REAL,
    scoring_components JSONB,
    rerank_score REAL,
    filtered_out_by VARCHAR(32),  -- NULL если вошёл в финальную ленту
    PRIMARY KEY (request_id, position)
) PARTITION BY RANGE (request_id);  -- partition key aligned with feed_requests если нужно

-- Или простой (без партиционирования на старте), с retention политикой позже:
CREATE TABLE feed.feed_items (
    request_id UUID NOT NULL REFERENCES feed.feed_requests(request_id) ON DELETE CASCADE,
    position SMALLINT NOT NULL,
    content_id UUID NOT NULL,
    raw_content_id UUID,
    final_score REAL,
    scoring_components JSONB,
    rerank_score REAL,
    filtered_out_by VARCHAR(32),
    PRIMARY KEY (request_id, position)
);
CREATE INDEX idx_feed_items_content ON feed.feed_items (content_id);
```

**Orchestrator решает** partitioning approach в P2. Рекомендация: начать без partitioning (feed_items), retention через `DELETE WHERE request_id IN (SELECT ... FROM feed_requests WHERE requested_at < NOW() - INTERVAL '12 months')` раз в сутки.

**profile_snapshot removed**: вместо отдельной колонки `profile_snapshot` в feed_requests — кладём в JSONB `feature_flags`:
```json
{
  "live_profile": false,
  "rerank": false,
  "hot_arrival": false,
  "profile_state": {
    "interaction_count": 42,
    "cold_start": false,
    "topic_top3": ["технологии", "наука", "бизнес"],
    "embedding_norm_first_20": [0.12, -0.05, ...]   // first 20 dims of 312 for drift analysis
  }
}
```

Rationale: embedding (312 floats × millions requests) = too much. Top-3 topics + первые 20 dims достаточно для drift analysis. Full embedding можно recompute'ить из interaction_count и момента запроса.

#### Schema C: Extensions to `interactions.user_interactions`

```sql
ALTER TABLE interactions.user_interactions
    ADD COLUMN IF NOT EXISTS feed_request_id UUID,
    ADD COLUMN IF NOT EXISTS position_in_feed SMALLINT,
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS app_version VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ab_bucket SMALLINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_ui_feed_request ON interactions.user_interactions (feed_request_id)
    WHERE feed_request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ui_user_time ON interactions.user_interactions (user_id, created_at DESC);
```

**Важно**: все ALTER'ы NULLABLE with DEFAULT. Old events без этих полей остаются валидными (backward compat).

#### Schema D: Kafka topic `user.interactions.batch` event extension

Current event (example):
```json
{
  "event_id": "uuid",
  "user_id": "uuid",
  "content_id": "uuid",
  "action_type": "LIKE",
  "duration_ms": 1500,
  "timestamp": "2026-04-19T12:34:56.789Z"
}
```

Extended (all new fields OPTIONAL):
```json
{
  "event_id": "uuid",
  "user_id": "uuid",
  "content_id": "uuid",
  "action_type": "LIKE",
  "duration_ms": 1500,
  "timestamp": "2026-04-19T12:34:56.789Z",
  "schema_version": 2,
  "feed_request_id": "uuid",         // NEW, optional
  "position_in_feed": 3,              // NEW, optional
  "device_type": "android",           // NEW, optional
  "app_version": "1.2.3",             // NEW, optional
  "ab_bucket": 0,                     // NEW, optional, default 0
  "scroll_depth": 0.85,               // NEW, optional (для CLOSE events)
  "metadata": {}                      // NEW, optional, ext point
}
```

`schema_version` (INT, default 1) позволяет consumer'у branch'еваться.

#### Schema E: HTTP `POST /api/interactions/batch` request body

Matches Kafka event schema. Backend просто proxy'ит в Kafka с добавлением user_id из JWT.

#### Schema F: HTTP `POST /api/feed` response header

Все ответы `/api/feed` должны содержать:
```
X-Request-Id: <UUID>
X-Feed-Source: personalized|cold_start|cached|fallback
```

Frontend сохраняет X-Request-Id и прикрепляет к последующим interaction events как `feed_request_id`.

### P2.2. Migration order

Каждый ALTER/CREATE TABLE запускается в правильном порядке (Liquibase changesets в backend/feed-service, backend/user-interactions-service):

1. P4 → создаёт `feed.feed_requests` и `feed.feed_items`
2. P5 → ALTER `interactions.user_interactions`

**Rollback plan**: каждая миграция reversible. Если что-то пошло не так на проде — DROP TABLE для новых, DROP COLUMN для альтеров.

### P2.3. Acceptance

- `contracts.md` существует, все схемы описаны
- Нет противоречий между schemas (e.g. JSONB поля имеют одинаковую структуру в разных местах)

**No commits** (design document only).

**Max turns**: 60

---

### Phase P3: api-gateway Request ID Middleware (1 day)

**Executed from**: `/home/mattew/SKD/backend/api-gateway/`

**Goal**: gateway генерирует или принимает `X-Request-Id`, пропагирует в downstream сервисы через header, возвращает в response.

### P3.1. Implementation

```kotlin
// backend/api-gateway/src/main/kotlin/.../filters/RequestIdFilter.kt
@Component
@Order(-100)  // Very early in the chain
class RequestIdFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = exchange.request.headers.getFirst("X-Request-Id")
            ?: UUID.randomUUID().toString()
        
        val mutatedRequest = exchange.request.mutate()
            .header("X-Request-Id", requestId)
            .build()
        
        exchange.response.headers.add("X-Request-Id", requestId)
        
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
            .contextWrite { it.put("requestId", requestId) }
    }
}
```

Downstream сервисы читают `X-Request-Id` header и логируют.

### P3.2. Tests

- Unit: filter order, header generated if missing, header propagated if present, MDC context populated
- Integration: e2e через gateway → mock downstream → response имеет X-Request-Id

### P3.3. Acceptance

- Любой запрос к gateway получает X-Request-Id в response
- Downstream сервисы (feed-service, user-interactions-service) видят его в request headers
- Regression: existing gateway tests (auth, rate limit, HMAC) всё ещё pass

### P3.4. Commits

- `test(gateway): RED tests for RequestIdFilter propagation`
- `feat(gateway): add RequestIdFilter generating X-Request-Id and propagating downstream`

**Max turns**: 50

---

### Phase P4: Migration `feed.feed_requests` + `feed.feed_items` (1 day)

**Executed from**: `/home/mattew/SKD/backend/feed-service/`

**Goal**: Liquibase changeset создающий две таблицы per schema в P2.

### P4.1. Deliverables

1. Liquibase changeset file `backend/feed-service/src/main/resources/db/changelog/XXX-create-feed-requests-items.xml`
2. JPA/Hibernate entities `FeedRequestEntity`, `FeedItemEntity` (Kotlin)
3. Repository interfaces: `FeedRequestRepository`, `FeedItemRepository` (Spring Data JDBC или Hibernate в зависимости от проекта)
4. DTO: `FeedRequestLog`, `FeedItemLog` в service layer

### P4.2. Tests

- Integration (Testcontainers PG): migration applies cleanly, insert/select работают, FK constraint работает, индексы созданы
- Unit: repository CRUD операции

### P4.3. Acceptance

- `./gradlew test` в feed-service: green
- Schema проверен через `\d feed.feed_requests` — совпадает с P2 contracts

### P4.4. Commits

- `test(feed-service): RED tests for FeedRequestRepository + migrations`
- `feat(feed-service): create feed_requests and feed_items tables via Liquibase`
- `feat(feed-service): add JPA entities and repositories for feed logging`

**Max turns**: 60

---

### Phase P5: Migration `user_interactions` Extensions (0.5 day)

**Executed from**: `/home/mattew/SKD/backend/user-interactions-service/`

**Goal**: Liquibase changeset добавляющий 5 NULLABLE колонок в `interactions.user_interactions` + 2 index'а.

### P5.1. Deliverables

1. Liquibase changeset (ALTER TABLE .. ADD COLUMN IF NOT EXISTS)
2. Update JPA entity — добавить поля
3. Update repository queries чтобы воспользоваться новыми полями где нужно

### P5.2. Tests

- Migration integration test: applied to fresh PG, columns exist with correct types + defaults
- Existing repository tests всё ещё pass (backward compat)

### P5.3. Acceptance

- `interactions.user_interactions` has new columns, old rows имеют NULL в них
- Old inserts (без новых полей) продолжают работать

### P5.4. Commits

- `test(user-interactions): RED test for new column migrations`
- `feat(user-interactions): add feed_request_id, position_in_feed, device_type, app_version, ab_bucket columns`

**Max turns**: 40

---

### Phase P6: feed-service Logs Feed Requests (1.5 days)

**Executed from**: `/home/mattew/SKD/backend/feed-service/`

**Goal**: каждый `POST /api/feed` пишет `feed_requests` row. Ответ возвращает `X-Request-Id` header.

### P6.1. Implementation

```kotlin
// backend/feed-service/src/main/kotlin/.../controller/FeedController.kt — existing
// Added step: after recommendations received, persist feed_request log

@PostMapping("/api/feed")
suspend fun getFeed(
    @RequestHeader("X-Request-Id") requestId: String,
    @RequestHeader("X-User-Id") userId: String,
    @RequestBody request: FeedRequestDto
): ResponseEntity<FeedResponseDto> {
    val start = System.currentTimeMillis()
    val result = feedService.generate(UUID.fromString(userId), request)
    val latency = System.currentTimeMillis() - start
    
    // NEW: log feed request
    feedLogService.logRequest(
        FeedRequestLog(
            requestId = UUID.fromString(requestId),
            userId = UUID.fromString(userId),
            source = result.source,
            countRequested = request.count,
            countReturned = result.items.size,
            latencyMs = latency.toInt(),
            latencyBreakdown = result.latencyBreakdown,
            featureFlags = result.featureFlags,
            abBucket = 0,  // TODO future A/B
            appVersion = request.appVersion,
            deviceType = request.deviceType
        ),
        result.items.mapIndexed { idx, item ->
            FeedItemLog(
                requestId = UUID.fromString(requestId),
                position = (idx + 1).toShort(),
                contentId = item.contentId,
                rawContentId = item.rawContentId,
                finalScore = item.finalScore,
                scoringComponents = item.scoringComponents,  // JSONB
                rerankScore = item.rerankScore,
                filteredOutBy = null  // null = made it to feed
            )
        }
    )
    
    return ResponseEntity.ok()
        .header("X-Request-Id", requestId)
        .header("X-Feed-Source", result.source)
        .body(FeedResponseDto(items = result.items.map { it.contentId }))
}
```

`FeedLogService` — новый service, пишет в оба table'а в одной транзакции (или async через executor если latency критично).

### P6.2. rec-system response extension

rec-system сейчас возвращает `{items: [uuid1, uuid2, ...]}` (чистые ID). Нужно чтобы feed-service получал **scoring components + rerank_score**. 

Варианта два:
- **Option A**: расширить `POST /recommendations` response в rec-system чтобы возвращать extended items с опциональными полями. Backward compat для feed-service старых версий. Описано в Phase P7.
- **Option B**: feed-service отдельным вызовом запрашивает explain breakdown через `/recommendations/explain` per item. Dev-only endpoint, слишком медленно для production.

Выбираем **Option A** — rec-system возвращает расширенный response с новым optional полем `items_detailed` (если feed-service просит через header/flag). Legacy `items` остаётся для обратной совместимости.

### P6.3. Tests

- Unit: `FeedLogService.logRequest` вызывает правильные repositories
- Integration: end-to-end feed request → записи в feed_requests + feed_items → response header X-Request-Id correct

### P6.4. Acceptance

- `POST /api/feed` пишет row в `feed_requests` и N rows в `feed_items`
- Response содержит `X-Request-Id` header
- Latency не выросла больше чем на 10ms (измерить — logging async если нужно)
- Existing `/api/feed` тесты green

### P6.5. Commits

- `test(feed-service): RED tests for FeedLogService integration`
- `feat(feed-service): implement FeedLogService persisting feed_requests and feed_items`
- `feat(feed-service): extend /api/feed handler to log request context and return X-Request-Id header`

**Max turns**: 100

---

### Phase P7: rec-system Exposes Scoring Breakdown (1.5 days)

**Executed from**: `/home/mattew/SKD/rec-system/`

**Goal**: `POST /recommendations` возвращает scoring_components per item (optional, gated).

### P7.1. Implementation

Расширить existing response schema `src/presentation/schemas/recommendations.py`:

```python
# Existing (keep unchanged):
class RecommendationResponse(BaseModel):
    user_id: UUID
    items: list[UUID]                # backward compat
    count: int
    generated_at: datetime

# NEW optional extension — controlled by query param ?include_breakdown=true OR Accept: application/vnd.rec+json;detailed
class RecommendationResponseDetailed(RecommendationResponse):
    items_detailed: list[FeedItemDetail] | None = None
    latency_breakdown: dict[str, int] | None = None
    profile_snapshot: dict | None = None
    feature_flags: dict | None = None

class FeedItemDetail(BaseModel):
    content_id: UUID                     # published_content.id
    raw_content_id: UUID                 # raw_content.id
    final_score: float
    scoring_components: dict             # {topic_match, embedding_sim, entity_match, sentiment, freshness, format}
    rerank_score: float | None           # None if REC_FEATURE_RERANK off
```

Модификация use case: добавить flag `include_breakdown` в input DTO. Когда true:
- Invoke ScoringExplainer (уже существует из Phase 8 предыдущего проекта) для каждого returned item
- Populate items_detailed

### P7.2. feed-service запрашивает расширенный формат

В `FeedService.generate()`:
```kotlin
val response = recSystemClient.recommend(
    userId = userId,
    count = request.count,
    includeBreakdown = true  // always true из feed-service
)
```

### P7.3. Tests

- Unit: response schema serialization с и без items_detailed
- Integration: use case возвращает items_detailed когда флаг set, скоринг components совпадают с scoring_service output
- Regression: без флага response идентичен master'у

### P7.4. Acceptance

- Backward compat: legacy `GET /recommendations` без флага возвращает `items` как раньше
- Extended: с флагом возвращает items_detailed со всеми компонентами
- Latency overhead с флагом — measured and documented (expected < 20 ms extra)

### P7.5. Commits

- `test(recommendations): RED tests for items_detailed in response`
- `feat(application): extend GenerateFeedUseCase to return items_detailed when requested`
- `feat(presentation): add items_detailed schema and query param include_breakdown`

**Max turns**: 80

---

### Phase P8: Kafka Contract Extension + Consumer Updates (1.5 days)

**Executed from**: две субсессии — `/home/mattew/SKD/backend/user-interactions-service/` затем `/home/mattew/SKD/rec-system/`.

**Goal**: event payload в Kafka `user.interactions.batch` имеет новые optional поля; consumer их сохраняет в DB.

### P8.1. Producer (user-interactions-service)

- Extended DTO `InteractionEvent` содержит новые optional поля
- Kafka publisher пишет их в payload
- Contract: `schema_version: 2` (добавляется в каждое событие)

### P8.2. Consumer (rec-system)

- `HandleInteractionsBatchUseCase` читает новые поля
- UPSERT в existing storage **не требуется**: эти поля хранятся в `interactions.user_interactions` которое пишет user-interactions-service напрямую, НЕ rec-system.
- Consumer **может использовать** feed_request_id / position_in_feed для будущего feature engineering (сейчас просто логирует).

**Важно**: producer (user-interactions-service) пишет в БД ДО публикации в Kafka (outbox pattern). Consumer (rec-system) **не пишет в user_interactions table** — он её читает. Поэтому SQL persistence новых полей происходит в user-interactions-service.

```kotlin
// backend/user-interactions-service/src/main/kotlin/.../service/InteractionService.kt
@Transactional
fun saveBatch(userId: UUID, events: List<InteractionEventDto>) {
    events.forEach { event ->
        // NEW: save all new fields to DB
        interactionsRepo.save(UserInteractionEntity(
            eventId = event.eventId,
            userId = userId,
            contentId = event.contentId,
            actionType = event.actionType,
            durationMs = event.durationMs,
            createdAt = event.timestamp,
            feedRequestId = event.feedRequestId,       // NEW
            positionInFeed = event.positionInFeed,     // NEW
            deviceType = event.deviceType,             // NEW
            appVersion = event.appVersion,             // NEW
            abBucket = event.abBucket ?: 0             // NEW
        ))
        // outbox publish
        outboxRepo.save(OutboxEntry(...))
    }
}
```

### P8.3. Tests

- Unit (Kotlin): producer serializer включает новые поля
- Unit (Python): consumer deserializer принимает и v1 (без новых полей), и v2
- Integration (Testcontainers Kafka + PG): e2e от HTTP POST → DB persist + Kafka publish → rec-system consume → no crash
- Regression: старые события без новых полей всё ещё обрабатываются

### P8.4. Acceptance

- Backward compat: producer по-прежнему принимает и пишет старый формат (без новых полей → NULL в DB)
- Forward: новые события с новыми полями корректно сохраняются
- rec-system consumer не падает на обоих форматах (regression после P1)

### P8.5. Commits (на двух ветках — backend и rec-system)

Backend:
- `test(user-interactions): RED test for extended InteractionEvent with new optional fields`
- `feat(user-interactions): accept and persist feed_request_id, position_in_feed, device_type, app_version, ab_bucket`
- `feat(user-interactions): publish extended event schema with schema_version=2 to Kafka`

rec-system:
- `test(consumers): RED test for handling v2 events with new optional fields`
- `feat(consumers): handle v2 InteractionEvent schema in HandleInteractionsBatchUseCase (pass-through, no SQL changes)`

**Max turns**: 120 (two sessions)

---

### Phase P9: Frontend Interaction Batch Extension (1 day)

**Executed from**: `/home/mattew/SKD/frontend-app/`

**Goal**: frontend сохраняет `feed_request_id` при получении ленты и отправляет его + `position_in_feed` в каждом interaction event.

### P9.1. Implementation

1. `FeedRepository` (Flutter) — ответ API уже содержит `X-Request-Id` header. Captur'ить в state.
2. `InteractionEvent` DTO — добавить optional поля `feedRequestId`, `positionInFeed`, `deviceType`, `appVersion`, `abBucket`.
3. `InteractionBatchUseCase` или его Flutter-эквивалент — при regist'рации события получает текущий feed_request_id и position из UI context.

```dart
// frontend-app/lib/data/models/interaction_event.dart
class InteractionEvent {
  final String eventId;
  final String contentId;
  final String actionType;
  final int? durationMs;
  final DateTime timestamp;
  // NEW fields
  final String? feedRequestId;
  final int? positionInFeed;
  final String? deviceType;  // "android" | "web"
  final String? appVersion;
  final int abBucket;
  final double? scrollDepth;
  
  Map<String, dynamic> toJson() => {
    'event_id': eventId,
    'content_id': contentId,
    'action_type': actionType,
    if (durationMs != null) 'duration_ms': durationMs,
    'timestamp': timestamp.toIso8601String(),
    'schema_version': 2,
    if (feedRequestId != null) 'feed_request_id': feedRequestId,
    if (positionInFeed != null) 'position_in_feed': positionInFeed,
    if (deviceType != null) 'device_type': deviceType,
    if (appVersion != null) 'app_version': appVersion,
    'ab_bucket': abBucket,
    if (scrollDepth != null) 'scroll_depth': scrollDepth,
  };
}
```

4. `FeedCard` widget — при IMPRESSION/LIKE/CLOSE вытаскивает `feed_request_id` и `position` из parent context.
5. `InteractionBatchScheduler` — 30s/50 events batching. Проверить что работает, если нет — реализовать.

### P9.2. Update `API_CONTRACTS.md`

- Секция "POST /api/interactions/batch" — добавить все новые поля с `optional` пометкой.
- Секция "POST /api/feed response" — добавить `X-Request-Id` header.

### P9.3. Tests

- Unit: serialization InteractionEvent с новыми полями — JSON matches schema D из P2
- Widget: при tap Like в feed карточке на позиции 3, создаётся event с positionInFeed=3 и feedRequestId из state
- Integration: mocked batch endpoint принимает correct payload

### P9.4. Acceptance

- Interaction events FROM этой версии frontend содержат feedRequestId и positionInFeed
- Existing widget tests pass
- API_CONTRACTS.md обновлён

### P9.5. Commits

- `test(data): RED tests for extended InteractionEvent serialization`
- `feat(data): add feedRequestId, positionInFeed, deviceType, appVersion, abBucket to InteractionEvent`
- `feat(ui): feed card captures feed_request_id and position for interaction tracking`
- `docs(api): update API_CONTRACTS.md with new optional interaction fields and X-Request-Id response header`

**Max turns**: 80

---

### Phase P10: Structured JSON Logging (1 day, parallelizable)

**Executed from**: каждый сервис отдельно (rec-system, feed-service, user-interactions-service, auth-service, user-service, subscription-service, api-gateway). 7 subsessions.

**Goal**: все logs в едином JSON формате с обязательными полями.

### P10.1. Required fields per log entry

```json
{
  "timestamp": "2026-04-19T12:34:56.789Z",
  "level": "INFO",
  "logger": "rec.system.generate_feed",
  "service": "rec-system",
  "environment": "production",
  "request_id": "abc-123",                // если доступен из MDC/context
  "user_id": "xyz-456",                   // если доступен
  "message": "feed_generated",
  "extra": {                               // свободный payload
    "count_returned": 30,
    "latency_ms": 145
  }
}
```

### P10.2. Python (rec-system)

Использовать `structlog` или `python-json-logger` с единой конфигурацией в `src/infrastructure/logging.py`:

```python
import logging
import json
import os
from pythonjsonlogger import jsonlogger

def configure_logging():
    handler = logging.StreamHandler()
    formatter = jsonlogger.JsonFormatter(
        '%(timestamp)s %(level)s %(name)s %(message)s',
        rename_fields={"levelname": "level", "asctime": "timestamp", "name": "logger"}
    )
    handler.setFormatter(formatter)
    root = logging.getLogger()
    root.addHandler(handler)
    root.setLevel(os.environ.get("LOG_LEVEL", "INFO"))
    # add service name filter for all records
```

Добавить FastAPI middleware для populate `request_id` в contextvars/MDC.

### P10.3. Kotlin (backend services)

Использовать `logback-logstash-encoder` или аналогичный:

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"feed-service","environment":"${ENV:-development}"}</customFields>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

MDC populate через WebFilter (request_id, user_id).

### P10.4. Acceptance

- Один запрос через gateway → всё downstream сервисы логируют с одним request_id
- Логи machine-readable (можно парсить jq'ом)
- Default log level = INFO, сенситивные данные (tokens, passwords) — NEVER in logs

### P10.5. Commits per service

- `feat(logging): structured JSON logging with request_id propagation`

**Max turns**: 60 per service (or run them parallel from orchestrator)

---

### Phase P11: Health + Metrics Endpoints (0.5 day)

**Executed from**: `/home/mattew/SKD/rec-system/` затем `/home/mattew/SKD/backend/feed-service/`

**Goal**: `/health` detailed endpoint и `/metrics` в Prometheus формате.

### P11.1. rec-system

Existing `/health` — вернуть расширенный JSON:
```json
{
  "status": "ok",
  "version": "1.2.0",
  "checks": {
    "database": "ok",
    "redis": "ok",
    "kafka_consumer": "ok"
  }
}
```

NEW `/metrics` endpoint (Prometheus format):
```
# HELP rec_feed_request_latency_ms Latency of feed requests
# TYPE rec_feed_request_latency_ms histogram
rec_feed_request_latency_ms_bucket{le="100"} 123
...
# HELP rec_feed_requests_total Total feed requests
# TYPE rec_feed_requests_total counter
rec_feed_requests_total{source="personalized"} 456
rec_feed_requests_total{source="cold_start"} 78
...
# HELP rec_content_processing_duration_seconds NLP processing tick duration
# TYPE rec_content_processing_duration_seconds histogram
...
```

Use `prometheus-client` library.

### P11.2. feed-service

Existing Spring Boot Actuator `/actuator/health` — расширить с checks для rec-system availability, Redis, Kafka.

### P11.3. Acceptance

- `curl /metrics` возвращает valid Prometheus exposition
- `curl /health` включает check-by-check status
- k8s readiness/liveness probes настроены на эти endpoints

### P11.4. Commits

- `feat(observability): add /metrics endpoint with key histograms and counters`
- `feat(observability): expand /health with per-dependency checks`

**Max turns**: 50

---

### Phase P12: E2E Validation + Docs (1 day)

**Executed from**: SKD root

**Goal**: запустить functional acceptance test из §3, убедиться что всё работает, задокументировать deliverable.

### P12.1. E2E test script

`scripts/e2e_data_capture_test.sh` реализующий шаги из §3 Functional acceptance test.

### P12.2. Post-deploy verification

Пересобрать контейнеры:
```bash
cd /home/mattew/SKD
docker compose -f docker-compose.yml build api-gateway auth-service user-service \
  user-interactions-service feed-service subscription-service
docker compose -f docker-compose.ml.yml build rec-worker
# redeploy stack
```

Запустить E2E тест → убедиться pass.

### P12.3. Update CLAUDE.md files

- `rec-system/CLAUDE.md`: добавить "Logging / Monitoring" = DONE. Upgrade section про endpoints.
- `backend/CLAUDE.md`: добавить info про X-Request-Id propagation, feed_requests таблицы.
- `frontend-app/API_CONTRACTS.md`: уже обновлён в P9.

### P12.4. Final report

`.claude/artifacts/data-capture/final_report.md`:
- Что shipped (список фаз с commits)
- Total cost
- Что работает end-to-end
- Sample SQL query возвращающая full training pair
- Known follow-ups (retention policy, partitioning feed_items, analytics dashboards)

### P12.5. Acceptance

- E2E test passes
- All three project test suites green
- Docs обновлены
- Final report написан

**Max turns**: 80

---

## 6. Artifacts Structure

```
/home/mattew/SKD/.claude/artifacts/data-capture/
├── orchestration_state.md          # append-only tracker
├── as_is_report.md                 # Phase P0 output
├── contracts.md                    # Phase P2 canonical schemas
├── phase{P0..P12}_prompt.txt       # exact prompts dispatched
├── final_report.md                 # Phase P12 synthesis
└── e2e_test_output.txt             # Phase P12 test run output
```

---

## 7. Testing Strategy

Каждый project имеет свои markers:

**rec-system**: `unit`, `integration`, `llm`, `slow`
**backend**: `@Test`, `@IntegrationTest` (Testcontainers)
**frontend-app**: `widget`, `unit`, `integration`

### CI gates per phase

Prior to ANY commit merging to project master:
- rec-system: `uv run pytest -m "unit or (integration and not slow)"` fully green
- backend service: `./gradlew test` green
- frontend-app: `flutter test` green

### Cross-service E2E (Phase P12 only)

- Single test: full chain feed request → interaction → SQL JOIN returns training pair
- Runs against docker-compose stack OR testcontainers stack

---

## 8. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Migration breaks production DB | Low | High | ALL migrations NULLABLE + DEFAULT, reversible, tested on staging first |
| Kafka schema mismatch between producer (Kotlin) and consumer (Python) | Medium | High | `schema_version` field + robust parser in consumer (P1 pattern extended) |
| Latency regression from logging feed_requests + feed_items | Medium | Medium | Async batch write, off hot path. Measure before/after |
| Frontend sends wrong field types | Medium | Medium | P9 unit tests + schema validation in gateway |
| Request ID lost between gateway and rec-system | Medium | High | P3 tests cover propagation. Backend services log it, если не видят — bug |
| Consumer bug fix (P1) breaks existing tests | Low | Medium | RED test reproduces bug, GREEN fix, full regression suite must pass |
| feed_requests table grows unbounded | Medium | Medium (in 6 months) | Partitioning or retention job. Defer to follow-up — at MVP scale fine |
| Docker rebuild breaks stack | Medium | High | Backup volumes before redeploy, rollback plan documented |
| Orchestrator budget exceeded | Low | Low | Per-phase $20 cap + total $120 cap + 80% alert |
| Scope creep: sub-claude adds observability beyond spec | Medium | Low | Clear non-goals in prompt + post-phase diff review |

---

## 9. Launch Command

```bash
cd /home/mattew/SKD && claude
```

Внутри сессии:

```
Запускаем проект "MVP Data Capture Foundation".

📋 Спека: /home/mattew/SKD/data-capture-spec.md
📋 Предыдущие проекты (для reference orchestration pattern):
   - /home/mattew/SKD/rec-system-eval-harness-spec.md
   - /home/mattew/SKD/rec-system-feature-delivery-spec.md

Прочти спеку целиком. Особое внимание:
- §0 Context (AS-IS предположения)
- §4 Orchestration Model
- §4.6 Phase dependency graph
- §5 Phases (12 фаз, разные проекты)

Протокол:

1. Инициализируй tracker в /home/mattew/SKD/.claude/artifacts/data-capture/orchestration_state.md
2. Phase P0: AS-IS verification из SKD root. Sub-claude идёт в каждый проект и отвечает на 6 вопросов.
3. Создай ветки feat/mvp-data-capture во всех трёх проектах (rec-system, backend, frontend-app)
4. Выполняй фазы в порядке dependency graph. Параллелизм опционален (можно sequential — дольше но проще).
5. После каждой фазы:
   - verify (7 steps из eval-harness §5.5): JSON parse, commits, tests green, artifacts exist, update tracker, commit tracker, show summary
   - НЕ мёрджь никакие ветки без моего явного approval
6. Stop на:
   - BLOCKED / FAILED с >3 retry
   - Регрессия в existing tests любого из проектов
   - Cost budget 80% ($96)
   - Любая неожиданная миграция которая может потерять данные
7. После P12: final_report.md + жди моего approval на merge трёх feat/mvp-data-capture веток.

Автономно per autonomous_to_done memory. Я мониторю tracker + comparison отчёты между фазами.

Поехали. Старт с Phase P0.
```

---

## 10. Expected outcomes

После завершения этого проекта система будет:

- **Logged**: каждый feed request полностью capture'н в DB (profile state, scoring breakdown, returned items)
- **Chained**: request_id связывает gateway → feed-service → rec-system → interaction events
- **Debuggable**: structured JSON logs со всех сервисов, можно grep'ать через service/request_id/user_id
- **Observable**: health и metrics endpoints отвечают
- **Stable**: consumer bug fixed, все existing tests green, backward compat соблюдена
- **MVP-ready**: можно соф-лончить к первым юзерам и собирать реальные данные

Ожидаемо через **4-6 недель после MVP launch** с реальными юзерами:
- Дата сет `feed_requests` + `feed_items` + `user_interactions` joined = полный training dataset
- Можно построить real-data offline eval harness (replace persona-based)
- Phase A 2.0 tuning на реальных adaptive scenarios
- Решение по Phase C (enable / drop / fine-tune) на основе real cohort analysis
- Все оптимизации — на data-driven, не speculative

---

## End of specification

**SKD orchestrator**: прочти §4 Orchestration Model и §5 Phases, затем:

1. AS-IS verification (Phase P0) первым делом. Не угадывай — читай реальный код.
2. Создай ветки feat/mvp-data-capture в трёх проектах.
3. Выполняй 12 фаз по dependency graph.
4. Каждая фаза — фреш `claude -p` сессия в правильном project directory.
5. verify после каждой, update tracker, commit tracker.
6. Final: E2E test + final_report + жди моего approval на merges.

Backward compat — главный принцип. Ничего existing не должно сломаться. Новые поля optional, новые схемы additive, feature flags где риск.

Build stable. Measure twice. Ship once.
