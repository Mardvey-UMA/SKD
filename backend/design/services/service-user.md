# user-service — Service Design Document

**Status:** Accepted  
**Date:** 2026-04-03  
**Technology:** Kotlin, Spring Boot 3.x, Spring Data JDBC

---

## 1. Overview

Manages user profiles and acts as the bridge between auth domain and the rest of the platform. Creates profiles from auth events, stores preferences, handles onboarding, and propagates subscription status changes.

**Responsibilities:**
- Create user profile on `user.registered` Kafka event
- CRUD user profile (display name, avatar, preferences)
- Onboarding flow (accept categories → forward to rec-system)
- Store subscription status (consumed from Kafka)
- Publish `user.created` event for rec-system

**Not responsible for:**
- Authentication/credentials (→ auth-service)
- Subscription payments (→ subscription-service)
- Content recommendations (→ rec-system)

---

## 2. Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Runtime | Kotlin + Spring Boot 3.x | Application framework |
| Database | PostgreSQL 16 + Spring Data JDBC | User profiles storage |
| Kafka | Spring Kafka | Consume events, produce via outbox |
| HTTP Client | RestClient (Spring 6.1+) | Call rec-system for onboarding |
| Scheduler | Quartz (spring-boot-starter-quartz) | Periodic jobs |
| Migrations | Flyway | Schema versioning |
| Build | Gradle (Kotlin DSL) | Build system |
| Container | Docker | Deployment |

---

## 3. API Endpoints

Base path: `/api/users`  
All endpoints are **authenticated** — require X-User-Id header from gateway.

### 3.1 GET /api/users/me

Get current user's profile.

```
GET /api/users/me
X-User-Id: 550e8400-...
```

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "display_name": "Иван Петров",
  "avatar_url": null,
  "subscription_tier": "free",
  "onboarding_completed": false,
  "created_at": "2026-04-03T12:00:00Z"
}
```

**Response 404:** Profile not found (user.registered event not yet consumed)

---

### 3.2 PUT /api/users/me

Update profile.

```
PUT /api/users/me
X-User-Id: 550e8400-...
Content-Type: application/json

{
  "display_name": "Иван Петров",
  "avatar_url": "https://cdn.example.com/avatars/123.jpg"
}
```

**Response 200:** Updated profile (same format as GET)  
**Response 422:** Validation failed

---

### 3.3 POST /api/users/me/onboarding

Complete onboarding. Forwards categories to rec-system.

```
POST /api/users/me/onboarding
X-User-Id: 550e8400-...
Content-Type: application/json

{
  "categories": ["технологии", "наука", "бизнес"],
  "source_content_ids": ["a1b2c3d4-..."]
}
```

**Validation:**
- `categories`: min 3 items, must exist in rec-system taxonomy
- `source_content_ids`: optional, max 10 items

**Response 200:**
```json
{
  "onboarding_completed": true,
  "message": "Preferences saved. Your feed is being personalized."
}
```

**Response 409:** Onboarding already completed  
**Response 422:** Fewer than 3 categories selected

**Side effects:**
1. UPDATE `profiles` SET onboarding_completed = true, categories = :categories
2. HTTP call to rec-system: `POST /onboarding {user_id, categories, source_content_ids}`
3. If rec-system returns 404 (profile not ready) → retry up to 3 times with 1s delay

---

### 3.4 GET /api/users/me/categories

Get available categories for onboarding UI. Proxies to rec-system.

```
GET /api/users/me/categories
```

**Response 200:** Proxied response from rec-system `GET /categories?locale=ru`

---

### 3.5 GET /health

```
GET /health
Response 200: {"status": "ok", "service": "user-service", "checks": {"database": "connected", "kafka": "connected"}}
Response 503: {"status": "degraded", ...}
```

---

## 4. Database Schema (user-db)

```sql
-- profiles.id = auth-service users.id (same UUID, no mapping)
-- See section 9 "User ID Mapping" for rationale
CREATE TABLE profiles (
    id                    UUID PRIMARY KEY,           -- = auth-service users.id (JWT sub claim)
    email                 VARCHAR(255) NOT NULL,
    display_name          VARCHAR(100),
    avatar_url            VARCHAR(500),
    subscription_tier     VARCHAR(20) DEFAULT 'free',
    onboarding_completed  BOOLEAN DEFAULT FALSE,
    categories            JSONB,                     -- ["технологии", "наука", ...]
    created_at            TIMESTAMPTZ DEFAULT now(),
    updated_at            TIMESTAMPTZ DEFAULT now()
);

-- Transactional Outbox
CREATE TABLE outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

**Note:** `profiles.id` = `auth-service users.id` (same UUID, no mapping). All services use this UUID as `user_id`.

---

## 5. Kafka Integration

### 5.1 Consumed Events

#### `user.registered`

**Producer:** auth-service (via outbox)  
**Consumer group:** `user-service-auth-events`

```json
{
  "payload": {
    "user_id": "550e8400-...",
    "email": "user@example.com",
    "timestamp": "2026-04-03T12:00:00Z"
  }
}
```

**Behavior:**
1. INSERT INTO profiles (id = event.user_id, email = event.email)
2. INSERT INTO outbox (event: user.created with profiles.id as user_id)
3. Single transaction
4. **Idempotent:** `ON CONFLICT (id) DO NOTHING`

#### `subscription.changed`

**Producer:** subscription-service (via outbox)  
**Consumer group:** `user-service-subscriptions`

```json
{
  "user_id": "profiles-uuid-...",
  "tier": "premium",
  "expires_at": "2026-05-03T12:00:00Z",
  "timestamp": "2026-04-03T12:00:00Z"
}
```

**Behavior:** `UPDATE profiles SET subscription_tier = :tier, updated_at = now() WHERE id = :userId`

### 5.2 Produced Events (via Outbox)

#### `user.created`

Published after profile creation from `user.registered` event.

```json
{
  "event_type": "user.created",
  "user_id": "profiles-uuid-...",
  "email": "user@example.com",
  "timestamp": "2026-04-03T12:00:05Z"
}
```

**Topic:** `user.created`  
**Key:** `user_id` (profiles.id)  
**Consumers:** rec-system

**Important:** `user_id` here is `profiles.id` (user-service's UUID), NOT auth-service's user_id. This is the canonical user identifier used by all downstream services (rec-system, feed-service, subscription-service).

---

## 6. Redis Usage

**None for MVP.** user-service has low read volume and simple queries. Profile reads go directly to PostgreSQL. Caching can be added later if GET /me becomes a bottleneck.

---

## 7. Scheduled Jobs (Quartz)

| Job | Schedule | Description |
|-----|----------|-------------|
| `CleanupPublishedOutboxJob` | Daily 03:00 UTC | DELETE FROM outbox WHERE published_at < now() - INTERVAL '3 days' |

**Outbox poller:** `@Scheduled(fixedDelay = 5000)` — same pattern as auth-service.

---

## 8. Service Dependencies

| Dependency | Protocol | Purpose | Failure Mode |
|-----------|----------|---------|--------------|
| PostgreSQL (user-db) | JDBC | Profiles, outbox | Service unavailable |
| Kafka | TCP | Consume auth/subscription events, produce user.created | Outbox buffers. Consumers paused, retried |
| rec-system | HTTP | POST /onboarding, GET /categories | Onboarding: retry 3x, then 500 to client. Categories: 502 to client |

---

## 9. User ID Mapping

**Single UUID everywhere.** `auth-service users.id` = `profiles.id` = JWT `sub` = `X-User-Id` header = user_id in all services.

```
auth-service users.id (generated at registration)
    = JWT sub claim
    = X-User-Id header (gateway extracts from JWT)
    = user-service profiles.id
    = feed-service user_bookmarks.user_id
    = subscription-service subscriptions.user_id
    = rec-system rec_profiles.user_id
```

No mapping, no `auth_provider_id` indirection. DDL in section 4 uses `profiles.id UUID PRIMARY KEY` = auth-service UUID directly.

---

## 10. Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:5432/user_db
    username: ${DB_USER:user_svc}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 15
      connection-timeout: 5000
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:kafka:9092}
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
    producer:
      acks: all
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always

services:
  rec-system:
    url: http://rec-system:8000
    timeout-ms: 5000
    retry-max-attempts: 3
    retry-delay-ms: 1000

gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}
```

---

**Краткое резюме (RU):** user-service — мост между auth и платформой. 4 HTTP-эндпоинта (profile CRUD, onboarding, categories). PostgreSQL для профилей. Kafka: потребляет `user.registered` и `subscription.changed`, публикует `user.created` через outbox. HTTP-вызов к rec-system при онбординге. UUID из auth-service используется напрямую как canonical user_id.
