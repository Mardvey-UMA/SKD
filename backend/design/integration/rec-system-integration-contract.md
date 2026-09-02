# rec-system Integration Contract

**Date:** 2026-04-03 (revised)  
**Status:** Draft  
**Owner:** rec-system team  
**Consumers:** feed-service, user-service

This document describes the **external contract** of rec-system — what it must expose (HTTP API, Kafka topics) for the platform services to function. It does NOT prescribe internal implementation (scoring formula, EMA parameters, diversity filters — those are rec-system's internal concern).

**Content access:** rec-system reads content data directly from the shared `published_content` table (content-aggregation-system's DB). It does NOT receive content via Kafka events.

---

## 1. HTTP API

rec-system is a **Python FastAPI** service. All endpoints are **internal-only** — called by other backend services, never directly by the frontend. Requests come with trusted headers from the API Gateway (X-User-Id, X-Gateway-Signature).

### Base URL
```
http://rec-system:8000
```

---

### 1.1 GET /health

Health check for load balancer / Docker / k8s probes.

```
GET /health

Response 200:
{
  "status": "ok",
  "version": "1.2.0"
}

Response 503:
{
  "status": "degraded",
  "reason": "model_loading"
}
```

---

### 1.2 POST /recommendations

**Called by:** feed-service  
**When:** Cache miss (no feed in Redis for this user) or prefetch trigger (user scrolled past 50% of current batch).  
**SLA:** p99 < 2000ms (feed-service has TimeLimiter 2s + circuit breaker)

#### Request

```
POST /recommendations
Content-Type: application/json

{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "count": 120
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | UUID | yes | User requesting recommendations. Must exist in rec-system's profile store |
| `count` | int | yes | Number of content IDs to return. Typical: 120 (100 + 20 buffer) |

**No `exclude` parameter.** rec-system internally tracks which content IDs it has already recommended to each user and automatically excludes them. This is rec-system's responsibility — it owns the "recommendation history" for each user.

#### Response — Success (200)

```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    "a1b2c3d4-0000-0000-0000-000000000001",
    "a1b2c3d4-0000-0000-0000-000000000002",
    "a1b2c3d4-0000-0000-0000-000000000003"
  ],
  "count": 120,
  "generated_at": "2026-04-03T12:00:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `items` | list[UUID] | **Ordered list** of content IDs. **Order = display order.** First item = most relevant. No scores exposed |
| `count` | int | Actual number of items returned (may be < requested if catalog is small) |
| `generated_at` | ISO8601 | Timestamp of ranking computation |

**Contract guarantees:**
- `items` ordered by relevance (most relevant first). Ordering is rec-system's internal concern
- `items` contains only IDs that exist in `published_content` at time of computation
- **`items` never contains content already recommended to this user** (rec-system tracks recommendation history internally — see "Recommendation History" below)
- No duplicate IDs in `items`
- If `count` > available candidates → returns all available (fewer items, no error)

#### Recommendation History (rec-system internal responsibility)

rec-system records every item it returns via `POST /recommendations` in a per-user history:

```sql
-- rec-system internal table (not part of external contract, shown for clarity)
CREATE TABLE recommendation_history (
    user_id       UUID NOT NULL,
    content_id    UUID NOT NULL,
    recommended_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);
```

**Behavior:**
- On each `POST /recommendations` call → rec-system records all returned content_ids in history
- Next call for same user → these IDs are excluded from candidates
- **Re-recommendation policy** (rec-system decides internally): e.g., allow re-recommendation after 7 days, or after content is significantly updated, or never. This is NOT part of the external contract
- **History cleanup**: rec-system may prune entries older than N days to prevent unbounded growth

**Why rec-system owns this, not feed-service:**
1. feed-service only knows the current cached batch (120 items). It has no cross-session history
2. "What to show again vs. what to never repeat" is a recommendation domain decision
3. This matches production patterns (YouTube, Netflix — recommendation service tracks impressions internally)

#### Response — User Not Found (404)

```json
{
  "error": "user_not_found",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "No recommendation profile exists for this user"
}
```

feed-service falls back to cold-start feed (see 1.3).

#### Response — Service Overloaded (503)

```json
{
  "error": "overloaded",
  "retry_after_ms": 5000,
  "message": "Model inference queue full"
}
```

feed-service circuit breaker → fallback to cached/cold-start content.

---

### 1.3 GET /recommendations/cold-start

**Called by:** feed-service  
**When:** rec-system returned 404 for user (profile not created yet) OR rec-system unavailable (circuit breaker open).

**This endpoint is a fallback, not the primary path.** The normal flow for new users is:

```
1. User completes mandatory onboarding → POST /onboarding → profile created
2. First GET /feed → POST /recommendations → personalized feed based on onboarding categories
```

Cold-start is needed when:
- `user.created` Kafka event hasn't been consumed yet (race condition: user finishes onboarding before rec-system processes user.created)
- rec-system is down

**SLA:** p99 < 100ms (pre-computed, served from cache)

#### Request

```
GET /recommendations/cold-start?count=30
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `count` | int | no | Number of IDs to return. Default: 30, max: 50 |

**No categories, no user context.** This is a global pre-computed list of trending/popular content that rec-system refreshes periodically.

#### Response — Success (200)

```json
{
  "items": [
    "a1b2c3d4-0000-0000-0000-000000000001",
    "a1b2c3d4-0000-0000-0000-000000000002"
  ],
  "count": 30,
  "generated_at": "2026-04-03T11:00:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `items` | list[UUID] | Ordered list of trending content. Same ordering contract as `/recommendations` |
| `count` | int | Actual number returned |
| `generated_at` | ISO8601 | When this trending list was last recomputed |

**rec-system behavior:**
- Maintains a pre-computed trending list in memory/Redis
- Refreshes periodically (e.g., every 1 hour)
- Based on recent popularity across all users (views, clicks, shares)
- Fast to serve: no per-user computation, just return the cached list

**Why 30 items:** This is a temporary feed shown until personalized recommendations are available. User will scroll through ~10-20 items before their personalized feed kicks in after onboarding. 30 is enough buffer.

**feed-service caching:** feed-service MAY cache this globally (not per-user) in Redis as `feed:cold-start:default` with TTL matching `generated_at` freshness.

---

### 1.4 POST /onboarding

**Called by:** user-service (via internal HTTP, no gateway)  
**When:** User completes mandatory onboarding — selects preferred categories/interests.  
**Purpose:** rec-system bootstraps a warm user profile so the very first `POST /recommendations` returns personalized results.

**Important:** Onboarding is mandatory. User cannot access the feed without completing it. This means `POST /recommendations` should almost always find a warm profile (edge case: Kafka lag on `user.created`).

#### Request

```
POST /onboarding
Content-Type: application/json

{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "categories": ["технологии", "наука", "бизнес"],
  "source_content_ids": [
    "a1b2c3d4-0000-0000-0000-000000000005"
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_id` | UUID | yes | User who completed onboarding |
| `categories` | list[string] | yes | Category IDs user selected (from `GET /categories` taxonomy). Min 3 |
| `source_content_ids` | list[UUID] | no | Content items user liked during onboarding ("pick 5 articles you find interesting"). Helps warm up the embedding profile |

#### Response — Success (200)

```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "profile_initialized": true
}
```

#### Response — User Not Found (404)

`user.created` Kafka event not yet consumed. user-service should retry after 1-2 seconds.

**Side effect:** After successful onboarding, rec-system MUST publish `recommendations.updated` Kafka event (see section 3.1) with `reason: "onboarding_complete"`. This invalidates the cold-start cache for this user so the next feed request gets personalized results.

---

### 1.5 GET /categories

**Called by:** user-service (to render onboarding UI)  
**When:** Onboarding screen loads.  
**SLA:** p99 < 100ms (static data, cached)

#### Request

```
GET /categories?locale=ru
```

#### Response — Success (200)

```json
{
  "categories": [
    {"id": "политика", "name": "Политика", "icon": "landmark"},
    {"id": "экономика", "name": "Экономика", "icon": "chart-line"},
    {"id": "технологии", "name": "Технологии", "icon": "laptop"},
    {"id": "наука", "name": "Наука", "icon": "flask"},
    {"id": "спорт", "name": "Спорт", "icon": "trophy"},
    {"id": "культура", "name": "Культура", "icon": "palette"},
    {"id": "общество", "name": "Общество", "icon": "users"},
    {"id": "происшествия", "name": "Происшествия", "icon": "alert-triangle"},
    {"id": "международные новости", "name": "Международные новости", "icon": "globe"},
    {"id": "бизнес", "name": "Бизнес", "icon": "briefcase"},
    {"id": "финансы", "name": "Финансы", "icon": "dollar-sign"},
    {"id": "образование", "name": "Образование", "icon": "book-open"},
    {"id": "здоровье", "name": "Здоровье", "icon": "heart"},
    {"id": "развлечения", "name": "Развлечения", "icon": "film"},
    {"id": "криминал", "name": "Криминал", "icon": "shield"},
    {"id": "армия", "name": "Армия", "icon": "shield-alert"},
    {"id": "природа", "name": "Природа", "icon": "leaf"},
    {"id": "транспорт", "name": "Транспорт", "icon": "car"}
  ],
  "min_select": 3,
  "max_select": 5
}
```

**Why rec-system owns this:** Category taxonomy is part of the recommendation model. rec-system controls the source of truth.

---

## 2. Kafka Topics — Consumed by rec-system

### 2.1 `user.created`

**Producer:** user-service  
**Purpose:** rec-system creates an empty recommendation profile for the new user.

```json
{
  "event_type": "user.created",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "timestamp": "2026-04-03T12:00:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | UUID | Unique user identifier (from user-service, NOT auth_provider_id) |
| `email` | string | Not used by rec-system, included for event completeness |
| `timestamp` | ISO8601 | When user was created |

**rec-system behavior:**
1. Create empty profile: zero vector (312-dim), interaction_count = 0, cold_start = true
2. Idempotent: if profile exists → ignore

**Topic config:** 6 partitions, key = `user_id`, retention = 7 days  
**Consumer group:** `rec-system-user-events`

---

### 2.2 `user.interactions.batch`

**Producer:** user-interactions-service  
**Purpose:** rec-system updates user preference profile based on interaction signals.

```json
{
  "event_type": "user.interactions.batch",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "interactions": [
    {
      "content_id": "a1b2c3d4-0000-0000-0000-000000000001",
      "action_type": "view",
      "duration_sec": 45,
      "timestamp": "2026-04-03T12:05:00Z"
    },
    {
      "content_id": "a1b2c3d4-0000-0000-0000-000000000002",
      "action_type": "click",
      "duration_sec": null,
      "timestamp": "2026-04-03T12:05:30Z"
    },
    {
      "content_id": "a1b2c3d4-0000-0000-0000-000000000003",
      "action_type": "hide",
      "duration_sec": null,
      "timestamp": "2026-04-03T12:06:00Z"
    }
  ],
  "batch_ts": "2026-04-03T12:06:30Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | UUID | User who performed interactions |
| `interactions` | list[object] | Chronologically ordered batch |
| `interactions[].content_id` | UUID | Content item ID |
| `interactions[].action_type` | enum | `view`, `click`, `scroll_past`, `share`, `save`, `hide` |
| `interactions[].duration_sec` | int? | View duration in seconds (only for `view`, null otherwise) |
| `interactions[].timestamp` | ISO8601 | Client timestamp of the interaction |
| `batch_ts` | ISO8601 | When user-interactions-service sent the batch |

**Action type → rec-system internal signal mapping:**

| Kafka action_type | rec-system event_type | Signal weight | Strong signal? |
|-------------------|----------------------|---------------|----------------|
| `view` (duration >= 2s) | IMPRESSION (read) | **+0.15** | no |
| `view` (duration < 2s) | IMPRESSION (skip) | **-0.05** | no |
| `click` | LIKE | **+0.60** | **yes** |
| `save` | BOOKMARK | **+0.80** | **yes** |
| `share` | BOOKMARK | **+0.80** | **yes** |
| `hide` | DISLIKE | **-0.70** | **yes** |
| `scroll_past` | IMPRESSION (skip) | **-0.05** | no |

**Note:** View threshold is **2 seconds** (not 5s or 15s). Views under 2s are treated as negative (skip). "Strong signal" = triggers potential `recommendations.updated` publish (subject to 15-min debounce).

**rec-system behavior:**
1. Look up content embeddings for each interaction
2. Update user profile (EMA or internal method)
3. Update interaction counters
4. Check debounce timer → optionally publish `recommendations.updated` (see section 3.1)

**Idempotency:** dedup by `(user_id, content_id, action_type, timestamp)`.

**Topic config:** 12 partitions, key = `user_id`, retention = 7 days  
**Consumer group:** `rec-system-interactions`

---

## 3. Kafka Topics — Produced by rec-system

### 3.1 `recommendations.updated`

**Consumer:** feed-service  
**Purpose:** Tell feed-service to invalidate this user's cached feed in Redis, so the next feed request gets fresh recommendations.

#### Message Schema

```json
{
  "event_type": "recommendations.updated",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "reason": "onboarding_complete",
  "timestamp": "2026-04-03T12:10:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | UUID | User whose recommendations should be refreshed |
| `reason` | enum | What triggered this event (see table below) |
| `timestamp` | ISO8601 | When rec-system published this event |

#### When rec-system publishes this event — concrete rules

| Trigger | Condition | `reason` value | Debounce |
|---------|-----------|----------------|----------|
| **Onboarding complete** | `POST /onboarding` succeeded | `"onboarding_complete"` | None — always publish immediately |
| **Interaction batch processed** | After processing `user.interactions.batch` AND batch contains at least one strong signal (`click`, `share`, `save`, or `hide`) | `"interactions_processed"` | **Max once per 15 minutes per user.** If last publish for this user was < 15 min ago → skip |
| **Manual refresh** | Admin/debug trigger | `"manual"` | None |

**Why 15-minute debounce on interactions:**
- user-interactions-service sends batches every 30 seconds for active users
- Without debounce: cache invalidated every 30s → every feed request hits rec-system → excessive load
- With 15-min debounce: at most 4 invalidations per hour per active user
- Between invalidations, the 30-min cache TTL provides natural refresh anyway
- After onboarding: no debounce, because the user's first personalized feed should appear immediately

**Implementation in rec-system:**
```python
# Simple debounce with Redis
DEBOUNCE_KEY = "rec:invalidation:last:{user_id}"
DEBOUNCE_TTL = 900  # 15 minutes

async def maybe_publish_update(user_id: str, reason: str):
    if reason == "onboarding_complete" or reason == "manual":
        # Always publish, no debounce
        await publish_recommendations_updated(user_id, reason)
        return

    # Check debounce
    last_published = await redis.get(DEBOUNCE_KEY.format(user_id=user_id))
    if last_published is not None:
        return  # Skip — published recently

    await publish_recommendations_updated(user_id, reason)
    await redis.setex(DEBOUNCE_KEY.format(user_id=user_id), DEBOUNCE_TTL, "1")
```

#### What feed-service does when it receives this event

```
1. Receive recommendations.updated {user_id: "123", reason: "onboarding_complete"}
2. DEL feed:user:123           ← delete cached feed from Redis
3. Done. No proactive re-computation.
4. ...
5. User opens app → GET /feed → feed-service sees cache miss
6. feed-service → POST /recommendations {user_id: "123", count: 120}
7. rec-system returns fresh personalized list
8. feed-service caches in Redis LIST, serves to user
```

**Key point: feed-service does NOT call rec-system proactively.** Invalidation is lazy. The feed is only recomputed when the user actually requests it. This prevents wasting compute on users who aren't actively browsing.

**If feed-service receives this for a user who has no cached feed** (e.g., user hasn't opened the app recently) → nothing to delete, event is a no-op. This is expected and not an error.

**Topic config:** 12 partitions, key = `user_id`, retention = 3 days  
**Consumer group:** `feed-service-invalidation`

---

## 4. Service Dependencies Diagram

```
                    ┌────────────────────────────────┐
                    │          rec-system             │
                    │        (FastAPI, Python)         │
                    ├────────────────────────────────┤
                    │                                │
   HTTP IN          │  POST /recommendations         │◄── feed-service
                    │  GET  /recommendations/         │
                    │       cold-start               │◄── feed-service
                    │  POST /onboarding              │◄── user-service
                    │  GET  /categories              │◄── user-service
                    │  GET  /health                  │◄── load balancer
                    │                                │
   KAFKA IN         │  user.created             ◄────│─── user-service
                    │  user.interactions.batch   ◄────│─── user-interactions-service
                    │                                │
   KAFKA OUT        │  recommendations.updated  ─────│──► feed-service
                    │                                │
   SHARED DB        │  published_content (read-only) │◄── content-aggregation-system
                    │                                │
   INTERNAL DB      │  PostgreSQL + PGVector         │
                    │  (user profiles, embeddings,    │
                    │   candidate scores)             │
                    └────────────────────────────────┘
```

---

## 5. Error Handling & Resilience

### HTTP Errors

| Status | Meaning | Caller Action |
|--------|---------|---------------|
| 200 | Success | Use response |
| 404 | User not found | feed-service → fall back to `GET /recommendations/cold-start` |
| 422 | Validation error | Fix request, don't retry |
| 429 | Rate limited | Retry after `Retry-After` header |
| 500 | Internal error | Retry once, then circuit breaker |
| 503 | Overloaded | Retry after `retry_after_ms`, then circuit breaker |

### Kafka Consumer Errors

| Scenario | Handling |
|----------|----------|
| Malformed message | Log + skip → DLQ (`{topic}.DLQ`) |
| Content ID not found | Skip interaction (content may be deleted) |
| User ID not found in profiles | For `user.interactions.batch`: create empty profile first, then process |
| Database unavailable | Pause consumer, retry with backoff |

### Timeouts (from caller perspective)

| Endpoint | Expected p50 | Expected p99 | Caller TimeLimiter |
|----------|-------------|-------------|---------------------|
| POST /recommendations | 200ms | 1500ms | 2000ms (feed-service) |
| GET /recommendations/cold-start | 5ms | 50ms | 1000ms (feed-service) |
| POST /onboarding | 100ms | 500ms | 5000ms (user-service) |
| GET /categories | 5ms | 50ms | 1000ms (user-service) |

---

## 6. Data Flow Scenarios

### Scenario 1: New user → onboarding → first personalized feed

```
1. User registers
     → auth-service → Kafka: user.registered
     → user-service consumes → creates profile → Kafka: user.created
     → rec-system consumes user.created → creates empty profile (cold_start=true)

2. User completes mandatory onboarding (selects 3+ categories)
     → Frontend: POST /api/users/onboarding {categories: ["tech", "science", "business"]}
     → user-service stores preferences
     → user-service: POST /onboarding to rec-system {user_id, categories}
     → rec-system initializes warm profile from category embeddings
     → rec-system publishes Kafka: recommendations.updated {reason: "onboarding_complete"}
     → feed-service consumes → DEL feed:user:{userId} (probably no-op, first time)

3. User enters feed (immediately after onboarding)
     → Frontend: GET /api/feed
     → feed-service: cache miss → POST /recommendations {user_id, count: 120}
     → rec-system: returns personalized list based on onboarding categories
     → feed-service: caches in Redis LIST, returns first 20 items
     → User sees personalized feed
```

**Edge case: rec-system hasn't processed `user.created` yet when `POST /onboarding` arrives → 404.**  
Resolution: user-service retries `POST /onboarding` after 1-2 seconds (at most 3 retries). Kafka lag for `user.created` is typically < 1 second.

**Edge case: rec-system hasn't processed onboarding when first `GET /feed` arrives → `/recommendations` returns 404.**  
Resolution: feed-service falls back to `GET /recommendations/cold-start` → user sees 30 trending posts. Next feed refresh (user pulls-to-refresh or TTL expires) → personalized feed.

### Scenario 2: Active user → interactions → improved recommendations

```
1. User browses feed, interacts with content
     → Frontend batches: [{content_id, action: "click"}, {content_id, action: "view", duration: 45s}]
     → POST /api/interactions/batch → user-interactions-service

2. user-interactions-service buffers (30s or 50 events)
     → Kafka: user.interactions.batch {user_id, interactions: [...]}

3. rec-system consumes batch
     → Updates user profile (EMA update with content embeddings)
     → Checks debounce: last publish for this user was 20 min ago → OK to publish
     → Kafka: recommendations.updated {user_id, reason: "interactions_processed"}

4. feed-service consumes → DEL feed:user:{userId}

5. User scrolls to end of current feed or pulls to refresh
     → GET /feed → cache miss → POST /recommendations
     → rec-system: returns fresh rankings reflecting updated interests
     → User sees improved feed
```

**If debounce blocks (last publish < 15 min ago):**  
rec-system skips publishing. User continues seeing current cached feed until cache TTL (30 min) expires naturally. Then next request triggers fresh `/recommendations`.

### Scenario 3: rec-system down → graceful degradation

```
1. User requests feed → feed-service → cache miss
2. feed-service → POST /recommendations → timeout (2s) → circuit breaker opens
3. feed-service → GET /recommendations/cold-start → also fails (rec-system fully down)
4. feed-service checks Redis: feed:cold-start:default (globally cached trending list)
5. If exists → serve cached trending (may be stale, up to 1 hour old)
6. If not → return empty feed with message "feed temporarily unavailable"
7. Circuit breaker half-opens after 10s → next request retries rec-system
```

---

## 7. Summary Tables

### HTTP Endpoints

| Method | Path | Called by | Purpose | Latency SLA |
|--------|------|-----------|---------|-------------|
| POST | /recommendations | feed-service | Personalized ranked feed | p99 < 2s |
| GET | /recommendations/cold-start | feed-service | Trending fallback (30 items) | p99 < 100ms |
| POST | /onboarding | user-service | Initialize profile from categories | p99 < 500ms |
| GET | /categories | user-service | Category taxonomy for onboarding UI | p99 < 100ms |
| GET | /health | infra | Liveness/readiness probe | p99 < 50ms |

### Kafka Consumed

| Topic | Producer | Consumer Group | Purpose |
|-------|----------|----------------|---------|
| `user.created` | user-service | `rec-system-user-events` | Create empty profile |
| `user.interactions.batch` | user-interactions-service | `rec-system-interactions` | Update user preferences |

### Kafka Produced

| Topic | Consumer | Trigger | Debounce |
|-------|----------|---------|----------|
| `recommendations.updated` | feed-service | Onboarding complete, interaction batch with strong signals | None for onboarding; 15 min per user for interactions |

### Content Access

| Source | Method | Access Pattern |
|--------|--------|----------------|
| `published_content` table | Shared DB (read-only) | rec-system reads for candidate pool, embeddings, metadata |

---

## 8. Open Questions for rec-system Team

1. **Recommendation history growth** — How long to keep per-user recommendation history? Prune after 30 days? At what history size does candidate filtering impact scoring performance?

2. **Profile staleness** — If user hasn't interacted in 30+ days, reset to cold start or keep stale profile?

3. **Content expiry** — Does rec-system auto-remove content older than N days from candidate pool? Or content-aggregation-system handles lifecycle?

4. **Cold-start refresh frequency** — How often is the trending list recomputed? Every hour? On new content arrival?

5. **Category taxonomy versioning** — When rec-system changes categories, how are existing user profiles migrated?

---

**Краткое резюме (RU):** Интеграционный контракт rec-system. 5 HTTP-эндпоинтов, 2 Kafka-топика на входе (`user.created`, `user.interactions.batch`), 1 на выходе (`recommendations.updated` с debounce 15 мин). Контент — через shared таблицу `published_content`. Cold-start = 30 trending постов (fallback). Онбординг обязателен → после него сразу персонализированная лента. `recommendations.updated` триггерит lazy invalidation: feed-service удаляет кэш, пересчёт только при следующем запросе пользователя.
