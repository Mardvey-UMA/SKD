# Diagrams — Content Platform Architecture

**Date:** 2026-04-04 (revised)  
**Format:** Mermaid (render in any Mermaid-compatible viewer, IDE plugin, or GitHub)

---

## 1. System Overview (C4 Container Level)

```mermaid
graph TB
    subgraph External["External"]
        User(["User / SPA"])
        YooKassa(["YooKassa\nPayment API"])
        SMTP(["SMTP\nEmail"])
        Sources(["Content Sources\nHabr / VC.ru / Telegram"])
    end

    subgraph Platform["Content Platform"]
        GW["API Gateway :8080\n---\nSpring Boot WebFlux\nJWT + CORS + Rate Limit\nHMAC signing"]

        AUTH["auth-service :8080\n---\nSpring Authorization Server\nJWT issuance, JWKS"]
        USR["user-service :8080\n---\nProfiles, Onboarding"]
        FEED["feed-service :8080\n---\nFeed assembly\nCollections (likes/bookmarks)"]
        INT["interactions-svc :8080\n---\nEvent batching"]
        SUB["subscription-svc :8080\n---\nYooKassa integration"]

        REC["rec-system :8000\n---\nFastAPI / Python\nML ranking"]
        CAS["content-aggregator :8086\n---\nRead-only REST API"]
        DEDUP["dedup-system\n---\nGPU, BGE-M3"]
        PARSER["parser-service\n---\nContent ingestion"]

        KAFKA{{"Kafka"}}
        REDIS[("Redis")]
        AUTH_DB[("auth-db")]
        USR_DB[("user-db")]
        FEED_DB[("feed-db")]
        INT_DB[("interactions-db")]
        SUB_DB[("subscription-db")]
        SHARED_DB[("content_agg_db\nshared")]
    end

    User -->|HTTPS| GW
    YooKassa -->|webhook| GW
    GW --> AUTH
    GW --> USR
    GW --> FEED
    GW --> INT
    GW --> SUB
    AUTH -->|SMTP| SMTP
    AUTH --- AUTH_DB
    AUTH -->|revoked:jti| REDIS
    USR --- USR_DB
    USR -->|HTTP| REC
    FEED --- FEED_DB
    FEED -->|cache| REDIS
    FEED -->|HTTP| REC
    FEED -->|HTTP| CAS
    INT --- INT_DB
    SUB --- SUB_DB
    SUB -->|HTTPS| YooKassa
    REC --- SHARED_DB
    REC -->|cold-start + debounce| REDIS
    CAS --- SHARED_DB
    DEDUP --- SHARED_DB
    PARSER --- SHARED_DB
    PARSER -->|Kafka| DEDUP
    Sources --> PARSER

    AUTH --> KAFKA
    USR --> KAFKA
    INT --> KAFKA
    SUB --> KAFKA
    KAFKA --> AUTH
    KAFKA --> USR
    KAFKA --> REC
    KAFKA --> FEED

    GW -->|rate + revocation| REDIS
```

---

## 2. Registration + Email Verification + Onboarding

Полный путь нового пользователя от регистрации до первой персонализированной ленты.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as Frontend SPA
    participant GW as API Gateway
    participant AUTH as auth-service
    participant EMAIL as SMTP
    participant KAFKA as Kafka
    participant USR as user-service
    participant REC as rec-system

    Note over User,REC: === Phase 1: Registration ===

    User->>SPA: Заполняет email + password
    SPA->>GW: POST /api/auth/register
    GW->>AUTH: POST /auth/register (public)
    AUTH->>AUTH: BCrypt(password), generate verify token
    AUTH->>AUTH: INSERT users + INSERT outbox (single tx)
    AUTH->>EMAIL: Send verification email
    AUTH-->>GW: 201 "Check your email"
    GW-->>SPA: 201
    SPA->>User: "Проверьте почту"

    Note over User,REC: === Phase 2: Email Verification ===

    User->>SPA: Кликает ссылку из письма
    SPA->>GW: GET /api/auth/verify?token=abc123
    GW->>AUTH: GET /auth/verify?token=abc123 (public)
    AUTH->>AUTH: Validate token, UPDATE email_verified=true
    AUTH->>AUTH: INSERT outbox (user.registered)
    AUTH-->>GW: 200 "Email verified"
    GW-->>SPA: 200

    Note over AUTH,KAFKA: Outbox poller (every 5s)
    AUTH->>KAFKA: user.registered {user_id, email}
    KAFKA->>USR: consume user.registered
    USR->>USR: INSERT profiles (id = user_id)
    USR->>KAFKA: user.created {user_id, email}
    KAFKA->>REC: consume user.created
    REC->>REC: INSERT rec_profiles (cold_start=true)

    Note over User,REC: === Phase 3: Login ===

    User->>SPA: Вводит email + password
    SPA->>GW: POST /api/auth/login
    GW->>AUTH: POST /auth/login (public)
    AUTH->>AUTH: Verify password, generate JWT + refresh
    AUTH-->>GW: 200 {access_token, refresh_token}
    GW-->>SPA: 200
    SPA->>SPA: Store tokens

    Note over User,REC: === Phase 4: Onboarding (mandatory) ===

    SPA->>GW: GET /api/users/me/categories
    GW->>USR: GET /api/users/me/categories
    USR->>REC: GET /categories?locale=ru
    REC-->>USR: 18 categories
    USR-->>GW: 18 categories
    GW-->>SPA: categories + min_select:3, max_select:5

    User->>SPA: Выбирает 3+ категории
    SPA->>GW: POST /api/users/me/onboarding {categories}
    GW->>USR: POST /api/users/me/onboarding
    USR->>USR: UPDATE profiles SET onboarding_completed=true
    USR->>REC: POST /onboarding {user_id, categories}
    REC->>REC: Init topic_vector from categories, cold_start=false
    REC->>KAFKA: recommendations.updated (onboarding_complete)
    REC-->>USR: 200 {profile_initialized: true}
    USR-->>GW: 200
    GW-->>SPA: 200 "Feed is ready"

    Note over User,REC: === Phase 5: First Feed ===

    SPA->>GW: GET /api/feed
    Note right of SPA: See Diagram 3 for full feed flow
```

---

## 3. Feed Browsing (pagination, prefetch, refresh)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as Frontend SPA
    participant GW as API Gateway
    participant FEED as feed-service
    participant REDIS as Redis
    participant REC as rec-system
    participant CAS as content-aggregator

    Note over User,CAS: === First Page (cache miss) ===

    User->>SPA: Открывает ленту
    SPA->>GW: GET /api/feed
    GW->>FEED: GET /feed (X-User-Id: uuid)
    FEED->>REDIS: LLEN feed:user:uuid
    REDIS-->>FEED: 0 (miss)

    FEED->>REC: POST /recommendations {user_id, count:120}
    REC->>REC: Score 500+ candidates, diversity filter
    REC->>REC: Write recommendation_history
    REC-->>FEED: {items: [id1..id120]}

    FEED->>REDIS: RPUSH feed:user:uuid:tmp id1..id120
    FEED->>REDIS: RENAME tmp → feed:user:uuid
    FEED->>REDIS: EXPIRE feed:user:uuid 1800

    FEED->>REDIS: LRANGE feed:user:uuid 0 19
    REDIS-->>FEED: [id1..id20]

    FEED->>REDIS: MGET content:id1..content:id20
    REDIS-->>FEED: all nulls (first time)

    FEED->>CAS: POST /api/v1/content/batch {ids, include_related:true}
    CAS-->>FEED: {items: {id1: {...}, ...}, not_found: []}

    FEED->>REDIS: SET content:id1 {...} EX 86400
    Note over REDIS: 24h TTL per content item

    FEED-->>GW: {items: [20 items], cursor: "eyJvIjoyMH0", has_next: true}
    GW-->>SPA: 200
    SPA->>User: Показывает 20 карточек

    Note over User,CAS: === Page 2 (cache hit) ===

    User->>SPA: Скроллит вниз
    SPA->>GW: GET /api/feed?cursor=eyJvIjoyMH0
    GW->>FEED: GET /feed?cursor=eyJvIjoyMH0
    FEED->>REDIS: LRANGE feed:user:uuid 20 39
    REDIS-->>FEED: [id21..id40]
    FEED->>REDIS: MGET content:id21..id40
    REDIS-->>FEED: cached items (24h TTL)
    FEED-->>GW: {items, cursor, has_next: true}
    GW-->>SPA: 200 (fast, all from cache)

    Note over User,CAS: === Page 4 (prefetch trigger at 50%) ===

    User->>SPA: Скроллит до offset=60
    SPA->>GW: GET /api/feed?cursor=eyJvIjo2MH0
    GW->>FEED: GET /feed?cursor=...
    FEED->>FEED: offset(60) >= total(120) * 0.5 → PREFETCH

    par Serve current page
        FEED->>REDIS: LRANGE 60 79
        FEED-->>GW: {items, cursor, has_next: true}
    and Async prefetch
        FEED->>REC: POST /recommendations {user_id, count:120}
        REC-->>FEED: new batch
        FEED->>REDIS: RPUSH+RENAME (atomic replace)
    end

    Note over User,CAS: === Pull-to-refresh ===

    User->>SPA: Тянет ленту вниз
    SPA->>GW: GET /api/feed?refresh=true
    GW->>FEED: GET /feed?refresh=true
    FEED->>REDIS: DEL feed:user:uuid
    FEED->>REC: POST /recommendations {count:120}
    REC-->>FEED: fresh rankings
    FEED->>REDIS: cache new list
    FEED-->>GW: {items: fresh first page}
    GW-->>SPA: 200
```

---

## 4. Content Detail + Related Content

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as Frontend SPA
    participant GW as API Gateway
    participant FEED as feed-service
    participant REDIS as Redis
    participant CAS as content-aggregator

    Note over User,CAS: === Open article (data from batch) ===

    User->>SPA: Кликает на карточку в ленте
    SPA->>SPA: Данные уже есть из GET /feed batch
    SPA->>User: Показывает статью (full HTML) + related_ids

    Note over User,CAS: === Click related article ===

    User->>SPA: Кликает на related article
    SPA->>GW: GET /api/feed/content/{relatedId}
    GW->>FEED: GET /feed/content/{relatedId}
    FEED->>REDIS: GET content:{relatedId}

    alt Cache hit (24h TTL)
        REDIS-->>FEED: cached item
        FEED-->>GW: 200 ContentBatchItem
    else Cache miss
        FEED->>CAS: POST /api/v1/content/batch {ids:[relatedId], include_related:true}
        CAS-->>FEED: {items: {relatedId: {...}}}
        FEED->>REDIS: SET content:{relatedId} EX 86400
        FEED-->>GW: 200 ContentBatchItem
    end

    GW-->>SPA: 200
    SPA->>User: Показывает related статью

    Note over User,CAS: === Deep link (direct URL) ===

    User->>SPA: Открывает ссылку domain.com/article/{id}
    SPA->>GW: GET /api/feed/content/{id}
    Note over FEED: Same flow as related click
    GW-->>SPA: 200 or 404
```

---

## 5. Like / Dislike / Bookmark

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as Frontend SPA
    participant GW as API Gateway
    participant FEED as feed-service
    participant FEED_DB as feed-db
    participant INT as interactions-svc
    participant KAFKA as Kafka
    participant REC as rec-system

    Note over User,REC: === User likes an article ===

    User->>SPA: Нажимает ❤️ на статье

    par Save to collections (feed-service)
        SPA->>GW: POST /api/feed/likes/{contentId}
        GW->>FEED: POST /feed/likes/{contentId}
        FEED->>FEED_DB: INSERT user_likes (user_id, content_id)
        FEED->>FEED_DB: DELETE user_dislikes WHERE same (mutual exclusion)
        FEED-->>GW: 201 {action: "liked"}
        GW-->>SPA: 201
    and Send interaction signal (interactions-svc)
        SPA->>SPA: Add to interaction buffer
        Note over SPA: Batched with other events
        SPA->>GW: POST /api/interactions/batch {events: [{content_id, action: "click"}]}
        GW->>INT: POST /interactions/batch
        INT->>INT: Validate, store in interactions-db
        INT->>INT: Buffer in ConcurrentLinkedQueue
    end

    Note over INT,REC: Every 30s or 50 events
    INT->>KAFKA: user.interactions.batch
    KAFKA->>REC: consume batch
    REC->>REC: EMA profile update (click = +0.60)
    REC->>REC: Check debounce (15 min)
    REC->>KAFKA: recommendations.updated (if debounce passed)
    KAFKA->>FEED: consume → DEL feed:user:{userId}

    Note over User,REC: === User views bookmarks ===

    User->>SPA: Открывает "Закладки"
    SPA->>GW: GET /api/feed/bookmarks
    GW->>FEED: GET /feed/bookmarks
    FEED->>FEED_DB: SELECT content_id FROM user_bookmarks ORDER BY created_at DESC LIMIT 21
    FEED->>FEED: getContentBatch(ids) — same cache logic as feed
    FEED-->>GW: {items: [ContentBatchItem], cursor, has_next}
    GW-->>SPA: 200

    Note over User,REC: === Check button states ===

    SPA->>GW: GET /api/feed/content/{id}/status
    GW->>FEED: GET /feed/content/{id}/status
    FEED->>FEED_DB: 3x EXISTS (likes, dislikes, bookmarks)
    FEED-->>GW: {liked: true, disliked: false, bookmarked: true}
    GW-->>SPA: 200
```

---

## 6. Subscription Purchase (YooKassa)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as Frontend SPA
    participant GW as API Gateway
    participant SUB as subscription-svc
    participant SUB_DB as subscription-db
    participant YK as YooKassa API
    participant KAFKA as Kafka
    participant AUTH as auth-service
    participant USR as user-service

    Note over User,USR: === Phase 1: Checkout ===

    User->>SPA: Нажимает "Подписаться" (premium_monthly)
    SPA->>GW: POST /api/subscription/checkout {plan: "premium_monthly"}
    GW->>SUB: POST /subscription/checkout

    SUB->>SUB_DB: Check active subscription → none
    SUB->>SUB_DB: Check pending payment (last 30 min) → none
    SUB->>SUB_DB: INSERT payment (status=pending, idempotency_key=UUID)
    SUB->>YK: POST /v3/payments {amount:299, save_payment_method:true}
    YK-->>SUB: {id: "yk-123", confirmation: {confirmation_url: "https://..."}}
    SUB->>SUB_DB: UPDATE payment SET external_id, confirmation_url
    SUB-->>GW: 200 {confirmation_url}
    GW-->>SPA: 200
    SPA->>User: Redirect to YooKassa checkout page

    Note over User,USR: === Phase 2: Payment ===

    User->>YK: Вводит данные карты, оплачивает
    YK->>YK: Process payment → succeeded
    YK-->>SPA: Redirect back to return_url

    Note over User,USR: === Phase 3: Webhook ===

    YK->>GW: POST /webhook/yookassa {event: payment.succeeded, object: {...}}
    GW->>SUB: POST /webhook/yookassa (public route)
    SUB->>SUB: Check source IP (X-Forwarded-For) against whitelist
    SUB->>YK: GET /v3/payments/yk-123 (verify status)
    YK-->>SUB: {status: "succeeded"}

    SUB->>SUB_DB: UPDATE payment status=succeeded
    SUB->>SUB_DB: INSERT saved_payment_methods (for recurring)
    SUB->>SUB_DB: UPSERT subscription (tier=premium, expires_at=+30d)
    SUB->>SUB_DB: INSERT outbox (subscription.changed)
    Note over SUB: All in single transaction

    SUB-->>GW: 200 (always)

    Note over SUB,KAFKA: Outbox poller
    SUB->>KAFKA: subscription.changed {user_id, tier:premium}

    par Update auth claims
        KAFKA->>AUTH: consume → UPDATE users.subscription_tier=premium
    and Update profile
        KAFKA->>USR: consume → UPDATE profiles.subscription_tier=premium
    end

    Note over User,USR: === Phase 4: Token Refresh ===

    SPA->>GW: POST /api/auth/refresh {refresh_token}
    GW->>AUTH: POST /auth/refresh
    AUTH->>AUTH: Load user → subscription_tier=premium
    AUTH-->>GW: {access_token: JWT with tier:premium}
    GW-->>SPA: 200

    SPA->>GW: GET /api/config/... (subscriber-only)
    GW->>GW: JWT tier=premium → SUBSCRIBER role → allowed
    GW->>GW: Route to content-aggregation:8086
```

---

## 7. Auto-Renewal + Expiration

```mermaid
sequenceDiagram
    autonumber
    participant QZ_RENEW as Quartz: AutoRenewalJob
    participant SUB as subscription-svc
    participant SUB_DB as subscription-db
    participant YK as YooKassa API
    participant KAFKA as Kafka
    participant QZ_EXPIRE as Quartz: CheckExpiredJob

    Note over QZ_RENEW,QZ_EXPIRE: === Happy path: auto-renewal succeeds ===

    QZ_RENEW->>SUB_DB: Find: expires_at < now()+1day AND auto_renew=true AND status=active
    SUB_DB-->>QZ_RENEW: [subscription user_id=123]
    QZ_RENEW->>SUB_DB: Find saved_payment_methods WHERE user_id=123
    SUB_DB-->>QZ_RENEW: {yookassa_method_id: "pm-456"}

    QZ_RENEW->>SUB: createAutopayment(amount, payment_method_id)
    SUB->>YK: POST /v3/payments {payment_method_id: "pm-456", amount: 299}
    Note over YK: No user confirmation needed
    YK-->>SUB: {status: "succeeded"} or webhook later

    Note over QZ_RENEW,QZ_EXPIRE: ... webhook arrives → same flow as diagram 6 ...

    Note over QZ_RENEW,QZ_EXPIRE: === Sad path: payment fails, grace period ===

    QZ_RENEW->>SUB: createAutopayment(...)
    SUB->>YK: POST /v3/payments
    YK-->>SUB: ERROR (card declined)
    QZ_RENEW->>QZ_RENEW: log.error, will retry next hour

    Note over QZ_RENEW,QZ_EXPIRE: 1 hour later — subscription expired

    QZ_EXPIRE->>SUB_DB: Find: expires_at < now() AND status=active
    SUB_DB-->>QZ_EXPIRE: [subscription user_id=123, auto_renew=true, expired 1h ago]
    QZ_EXPIRE->>QZ_EXPIRE: auto_renew=true AND expired < 3h ago → SKIP (grace period)

    Note over QZ_RENEW,QZ_EXPIRE: AutoRenewalJob retries

    QZ_RENEW->>SUB_DB: Find: expires_at < now()+1day AND auto_renew=true AND status=active
    SUB_DB-->>QZ_RENEW: [same subscription]
    QZ_RENEW->>SUB: createAutopayment(...)
    SUB->>YK: POST /v3/payments
    YK-->>SUB: {status: "succeeded"}
    Note over QZ_RENEW: Subscription renewed!

    Note over QZ_RENEW,QZ_EXPIRE: === If 3 hours pass without success ===

    QZ_EXPIRE->>SUB_DB: Find expired, auto_renew=true, expired > 3h ago
    QZ_EXPIRE->>SUB_DB: UPDATE status=expired, tier=free
    QZ_EXPIRE->>SUB_DB: INSERT outbox (subscription.changed, tier:free)
    QZ_EXPIRE->>KAFKA: subscription.changed → auth + user-service
```

---

## 8. Logout + Token Revocation

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SPA as Frontend SPA
    participant GW as API Gateway
    participant AUTH as auth-service
    participant REDIS as Redis

    User->>SPA: Нажимает "Выйти"
    SPA->>GW: POST /api/auth/logout (Authorization: Bearer eyJ...)
    GW->>GW: Validate JWT, extract jti
    GW->>REDIS: GET revoked:{jti}
    REDIS-->>GW: null (not revoked yet)
    GW->>AUTH: POST /auth/logout (X-User-Id, X-Gateway-Signature)

    AUTH->>AUTH: Extract jti and exp from JWT
    AUTH->>REDIS: SETEX revoked:{jti} {remaining_seconds} "1"
    AUTH->>AUTH: DELETE refresh_token FROM DB
    AUTH-->>GW: 200 "Logged out"
    GW-->>SPA: 200
    SPA->>SPA: Clear stored tokens

    Note over User,REDIS: === Subsequent request with old token ===

    SPA->>GW: GET /api/feed (Authorization: Bearer eyJ... same token)
    GW->>GW: Validate JWT signature → OK (not expired yet)
    GW->>REDIS: GET revoked:{jti}
    REDIS-->>GW: "1" → REVOKED
    GW-->>SPA: 401 "token_revoked"

    Note over REDIS: After 15 min: token expires naturally
    Note over REDIS: revoked:{jti} key also expires (same TTL)
    Note over REDIS: No cleanup needed — self-expiring
```

---

## 9. Kafka Event Flow Map

```mermaid
graph LR
    subgraph Producers
        AUTH_P["auth-service"]
        USR_P["user-service"]
        INT_P["interactions-svc"]
        SUB_P["subscription-svc"]
        REC_P["rec-system"]
        PARSER_P["parser-service"]
    end

    subgraph Topics
        T1["user.registered"]
        T2["user.created"]
        T3["user.interactions.batch"]
        T4["subscription.changed"]
        T5["recommendations.updated"]
        T6["content.published"]
    end

    subgraph Consumers
        USR_C["user-service"]
        REC_C["rec-system"]
        AUTH_C["auth-service"]
        FEED_C["feed-service"]
        DEDUP_C["dedup-system"]
    end

    AUTH_P -->|outbox| T1
    USR_P -->|outbox| T2
    INT_P -->|buffer flush| T3
    SUB_P -->|outbox| T4
    REC_P -->|debounce 15min| T5
    PARSER_P --> T6

    T1 --> USR_C
    T2 --> REC_C
    T3 --> REC_C
    T4 --> AUTH_C
    T4 --> USR_C
    T5 --> FEED_C
    T6 --> DEDUP_C
```

---

## 10. Redis Key Ownership Map

```mermaid
graph TB
    subgraph Redis["Redis (single node, maxmemory 1gb, allkeys-lru)"]
        R1["revoked:{jti}\nSTRING, TTL ≤ 900s"]
        R2["rate:{userId}\nSTRING (Bucket4j)"]
        R3["feed:user:{userId}\nLIST, TTL 30min"]
        R4["content:{contentId}\nSTRING JSON, TTL 24h"]
        R5["related:{contentId}\nSTRING JSON, TTL 2h"]
        R6["content:miss:{contentId}\nSTRING, TTL 5min"]
        R7["rec:cold-start:trending\nSTRING JSON, TTL 1h"]
        R8["rec:invalidation:last:{userId}\nSTRING, TTL 15min"]
    end

    AUTH["auth-service"] -->|WRITE| R1
    GW["API Gateway"] -->|READ| R1
    GW -->|READ/WRITE| R2
    FEED["feed-service"] -->|WRITE| R3
    FEED -->|WRITE| R4
    FEED -->|WRITE| R5
    FEED -->|WRITE| R6
    FEED -->|READ| R7
    REC["rec-system"] -->|WRITE| R7
    REC -->|READ/WRITE| R8
```

---

## 11. Database Ownership Map

```mermaid
graph TB
    subgraph Isolated["Isolated Databases (new services)"]
        AUTH_DB["auth-db\n---\nusers\nemail_verification_tokens\npassword_reset_tokens\nrefresh_tokens\noutbox"]
        USR_DB["user-db\n---\nprofiles\noutbox"]
        FEED_DB["feed-db\n---\nuser_bookmarks\nuser_likes\nuser_dislikes"]
        INT_DB["interactions-db\n---\nuser_interactions\n(partitioned monthly\n+ DEFAULT partition)"]
        SUB_DB["subscription-db\n---\nplans\nsubscriptions\npayments\nsaved_payment_methods\nwebhook_log\noutbox"]
    end

    subgraph Shared["Shared Database: content_agg_db (schema data_flow)"]
        T_RAW["raw_content\n(parser writes)"]
        T_PUB["published_content\n(parser writes)"]
        T_ART["articles\n(dedup writes)"]
        T_SIM["similarities\n(dedup writes)"]
        T_PF["posts_features\n(rec writes)"]
        T_RP["rec_profiles\n(rec writes)"]
        T_RH["recommendation_history\n(rec writes)"]
        T_CAT["categories\n(rec writes)"]
    end

    AUTH["auth-service"] --- AUTH_DB
    USR["user-service"] --- USR_DB
    FEED["feed-service"] --- FEED_DB
    INT["interactions-svc"] --- INT_DB
    SUB["subscription-svc"] --- SUB_DB

    REC["rec-system"] -->|read| T_RAW
    REC -->|read| T_ART
    REC -->|read| T_SIM
    REC -->|read| T_PUB
    REC -->|write| T_PF
    REC -->|write| T_RP
    REC -->|write| T_RH
    REC -->|write| T_CAT

    CAS["content-aggregator"] -->|read only| T_PUB
    CAS -->|read only| T_ART
    CAS -->|read only| T_SIM
```
