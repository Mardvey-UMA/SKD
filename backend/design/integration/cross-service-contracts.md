# Cross-Service Contract Verification

**Date:** 2026-04-03  
**Purpose:** Verify that all service interfaces match — what one service produces, another correctly consumes.

---

## 1. Kafka Event Contracts

### 1.1 `user.registered`

| Aspect | Producer (auth-service) | Consumer (user-service) |
|--------|------------------------|------------------------|
| Topic | `user.registered` | `user.registered` |
| Key | `users.id` (UUID) | — |
| Consumer group | — | `user-service-auth-events` |
| `user_id` | auth-service `users.id` | Maps to `profiles.id` (same UUID) |
| `email` | From `users.email` | Stored in `profiles.email` |
| `timestamp` | `now()` | Informational, not used for logic |
| Idempotency | — | `ON CONFLICT (id) DO NOTHING` |

**Status: MATCHED**

### 1.2 `user.created`

| Aspect | Producer (user-service) | Consumer (rec-system) |
|--------|------------------------|----------------------|
| Topic | `user.created` | `user.created` |
| Key | `profiles.id` (UUID) | — |
| Consumer group | — | `rec-system-user-events` |
| `user_id` | `profiles.id` (= auth-service `users.id`) | Used as rec-system's user identifier |
| `email` | From `profiles.email` | Not used by rec-system |
| Idempotency | — | If profile exists → ignore |

**Status: MATCHED**

### 1.3 `user.interactions.batch`

| Aspect | Producer (user-interactions-service) | Consumer (rec-system) |
|--------|-------------------------------------|----------------------|
| Topic | `user.interactions.batch` | `user.interactions.batch` |
| Key | `user_id` (UUID) | — |
| Consumer group | — | `rec-system-interactions` |
| `user_id` | From `X-User-Id` header (= profiles.id) | Matches rec-system's user profile |
| `interactions[].content_id` | UUID from frontend | Must exist in rec-system's content store |
| `interactions[].action_type` | Validated enum: view, click, scroll_past, share, save, hide | Same enum expected |
| `interactions[].duration_sec` | int or null | Nullable, used for view signal strength |
| `interactions[].timestamp` | ISO8601, validated (not future, not >24h old) | Client timestamp |
| `batch_ts` | Instant.now() at flush time | Informational |
| Idempotency | — | Dedup by (user_id, content_id, action_type, timestamp) |

**Status: MATCHED**

### 1.4 `subscription.changed`

| Aspect | Producer (subscription-service) | Consumer (user-service) | Consumer (auth-service) |
|--------|-------------------------------|------------------------|------------------------|
| Topic | `subscription.changed` | `subscription.changed` | `subscription.changed` |
| Key | `user_id` (UUID) | — | — |
| Consumer group | — | `user-service-subscriptions` | `auth-service-subscriptions` |
| `user_id` | `subscriptions.user_id` (= profiles.id) | Updates `profiles.subscription_tier` | Updates `users.subscription_tier` |
| `tier` | "premium" or "free" | Stored directly | Stored directly, included in next JWT |
| `expires_at` | ISO8601 | Informational | Informational |
| `status` | "active", "expired", "cancelled" | Informational | Informational |
| Idempotency | — | UPDATE is idempotent | UPDATE is idempotent |

**Status: MATCHED**

### 1.5 `recommendations.updated`

| Aspect | Producer (rec-system) | Consumer (feed-service) |
|--------|----------------------|------------------------|
| Topic | `recommendations.updated` | `recommendations.updated` |
| Key | `user_id` (UUID) | — |
| Consumer group | — | `feed-service-invalidation` |
| `user_id` | Same UUID as received in /onboarding and user.created | Maps to `feed:user:{userId}` Redis key |
| `reason` | "onboarding_complete", "interactions_processed", "manual" | Informational — feed-service doesn't branch on reason |
| Debounce | 15 min per user for interactions; none for onboarding | — |
| Action | — | `DEL feed:user:{userId}` from Redis |

**Status: MATCHED**

### 1.6 `content.updated`

| Aspect | Producer (content-aggregation-system) | Consumer (feed-service) |
|--------|--------------------------------------|------------------------|
| Topic | `content.updated` | `content.updated` |
| Key | `content_id` (UUID) | — |
| Consumer group | — | `feed-service-content-invalidation` |
| `content_id` | From `published_content.id` | Maps to `content:{contentId}` Redis key |
| `action` | "updated" or "deleted" | Both trigger `DEL content:{contentId}` |

**Status: MATCHED**

---

## 2. HTTP Contracts

### 2.1 Gateway → auth-service

| Gateway Route | auth-service Endpoint | Auth | Notes |
|--------------|----------------------|------|-------|
| `POST /api/auth/register` | `POST /auth/register` | Public | No JWT |
| `GET /api/auth/verify` | `GET /auth/verify` | Public | No JWT |
| `POST /api/auth/login` | `POST /auth/login` | Public | No JWT |
| `POST /api/auth/refresh` | `POST /auth/refresh` | Public | Refresh token in body |
| `POST /api/auth/logout` | `POST /auth/logout` | JWT required | X-User-Id + HMAC |
| `POST /api/auth/password/*` | `POST /auth/password/*` | Mixed | reset-request=public, change=JWT |

**Status: MATCHED**

### 2.2 Gateway → user-service

| Gateway Route | user-service Endpoint | Auth |
|--------------|----------------------|------|
| `GET /api/users/me` | `GET /api/users/me` | JWT required |
| `PUT /api/users/me` | `PUT /api/users/me` | JWT required |
| `POST /api/users/me/onboarding` | `POST /api/users/me/onboarding` | JWT required |
| `GET /api/users/me/categories` | `GET /api/users/me/categories` | JWT required |

**Status: MATCHED**

### 2.3 Gateway → feed-service

| Gateway Route | feed-service Endpoint | Auth |
|--------------|----------------------|------|
| `GET /api/feed` | `GET /api/feed` | JWT required |
| `GET /api/feed?cursor=X` | `GET /api/feed?cursor=X` | JWT required |

**Status: MATCHED**

### 2.4 Gateway → user-interactions-service

| Gateway Route | Endpoint | Auth |
|--------------|----------|------|
| `POST /api/interactions/batch` | `POST /api/interactions/batch` | JWT required |

**Status: MATCHED**

### 2.5 Gateway → subscription-service

| Gateway Route | Endpoint | Auth |
|--------------|----------|------|
| `GET /api/subscription/status` | `GET /api/subscription/status` | JWT required |
| `POST /api/subscription/checkout` | `POST /api/subscription/checkout` | JWT required |
| `POST /api/subscription/cancel` | `POST /api/subscription/cancel` | JWT required |
| `POST /webhook/yookassa` | `POST /webhook/yookassa` | Public (IP whitelist in subscription-service) |

**Status: MATCHED**

### 2.6 Gateway → content-aggregation-system

| Gateway Route | Endpoint | Auth |
|--------------|----------|------|
| `/api/config/**` | content-aggregation config endpoints | JWT required, SUBSCRIBER role |

**Status: MATCHED**

### 2.7 user-service → rec-system (internal HTTP, no gateway)

| user-service Call | rec-system Endpoint | Contract |
|-------------------|---------------------|----------|
| Onboarding | `POST /onboarding` | `{user_id, categories, source_content_ids}` |
| Categories | `GET /categories?locale=ru` | Returns category list with min/max select |

**Status: MATCHED** — user_id is profiles.id = auth users.id (canonical UUID).

### 2.8 feed-service → rec-system (internal HTTP, no gateway)

| feed-service Call | rec-system Endpoint | Contract |
|-------------------|---------------------|----------|
| Get recommendations | `POST /recommendations` | `{user_id, count: 120}` → ordered list of content_ids |
| Cold-start | `GET /recommendations/cold-start?count=30` | → ordered list of trending content_ids |

**Status: MATCHED**

### 2.9 feed-service → content-aggregation-system (internal HTTP, port 8086)

| feed-service Call | Endpoint | Contract |
|-------------------|----------|----------|
| Batch content fetch | `POST /api/v1/content/batch` | `{ids, include_related, related_limit}` → `{items: Map<id, Content>, not_found: [UUID]}` |

**Already implemented** in content-aggregator-service. See `contracts/content-aggregator-design.md`.

**Key:** Response `items` is a **Map** (not List), field is `not_found` (not `missing_ids`).

**Status: MATCHED**

---

## 3. User ID Consistency

All services use the same UUID as user identifier:

```
auth-service users.id (generated at registration)
    = JWT sub claim
    = X-User-Id header (set by gateway)
    = user-service profiles.id
    = subscription-service subscriptions.user_id
    = user-interactions-service user_interactions.user_id
    = rec-system user profiles user_id
    = feed-service Redis key feed:user:{userId}
```

**Status: CONSISTENT** — single UUID flows through the entire system.

---

## 4. HMAC Signature Consistency

All internal services verify the same HMAC:

| Component | Config Key | Usage |
|-----------|-----------|-------|
| API Gateway | `gateway.hmac-secret` | Computes signature |
| auth-service | `gateway.hmac-secret` | Verifies on authenticated endpoints |
| user-service | `gateway.hmac-secret` | Verifies on all endpoints |
| feed-service | `gateway.hmac-secret` | Verifies on all endpoints |
| user-interactions-service | `gateway.hmac-secret` | Verifies on all endpoints |
| subscription-service | `gateway.hmac-secret` | Verifies on authenticated endpoints |

**Signature payload:** `"$userId|$roles|$subscriptionTier|$requestId"`

**Status: CONSISTENT** — same secret, same algorithm, same payload format.

---

## 5. Shared Infrastructure

| Infrastructure | Used By | Purpose |
|---------------|---------|---------|
| **PostgreSQL** (auth-db) | auth-service | Credentials, tokens, outbox |
| **PostgreSQL** (user-db) | user-service | Profiles, outbox |
| **PostgreSQL** (interactions-db) | user-interactions-service | Events (partitioned) |
| **PostgreSQL** (subscription-db) | subscription-service | Subscriptions, payments, outbox |
| **PostgreSQL** (`content_agg_db`, schema `data_flow`) | content-aggregator (SELECT), rec-system (SELECT + own tables), parser (INSERT/UPDATE), dedup (INSERT/UPDATE) | **Shared DB** — all existing systems |
| **Redis** | gateway (revocation, rate limits), feed-service (feed+content cache), auth-service (revocation write), rec-system (cold-start cache, debounce) | Distributed cache |
| **Kafka** | All services except gateway and content-aggregator | Async messaging |

**Database isolation: PARTIAL.** New services (auth, user, interactions, subscription) each have isolated databases. Existing systems (content-aggregator, rec-system, dedup, parser) share `content_agg_db` — accepted for MVP. content-aggregator does NOT use Kafka or Redis.

---

## 6. Required New Endpoints in Existing Systems

### content-aggregation-system

**Already implemented.** See `contracts/content-aggregator-design.md` for full specification.

```
POST /api/v1/content/batch   (port 8086)
Body: {"ids": [...], "include_related": true, "related_limit": 5}
Response: {"items": {Map<id, Content>}, "not_found": [...]}
```

### rec-system

**Already implemented.** See `contracts/rec-system-design.md` and `contracts/rec-system-as-is.md`.

All endpoints:
- POST /recommendations (port 8000)
- GET /recommendations/cold-start
- POST /onboarding
- GET /categories
- GET /health

---

## 7. Kafka Topics Master Registry

| Topic | Partitions | Retention | Key | Producer | Consumers | Status |
|-------|-----------|-----------|-----|----------|-----------|--------|
| `user.registered` | 6 | 7d | user_id | auth-service | user-service | **planned** |
| `user.created` | 6 | 7d | user_id | user-service | rec-system | **planned** |
| `user.interactions.batch` | 12 | 7d | user_id | user-interactions-service | rec-system | **planned** |
| `subscription.changed` | 6 | 7d | user_id | subscription-service | user-service, auth-service | **planned** |
| `content.published` | 12 | 7d | content_id | parser-service | dedup-system | **exists** |
| `recommendations.updated` | 12 | 3d | user_id | rec-system | feed-service | **planned** |

**Total: 6 topics.** `content.updated` removed — content-aggregator does NOT produce Kafka events. Content cache invalidation handled by TTL (24h).

**Note:** `content.published` is produced by parser-service (not content-aggregator). content-aggregator is a pure read-only REST API.

---

**Краткое резюме (RU):** Контракты проверены и скорректированы после сверки с `contracts/`. 6 Kafka-топиков (content-aggregator не участвует в Kafka). `POST /api/v1/content/batch` уже реализован (порт 8086, Map-формат ответа). Существующие системы разделяют одну БД `content_agg_db`.
