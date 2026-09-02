# feed-service — Service Design Document

**Status:** Accepted  
**Date:** 2026-04-03  
**Technology:** Kotlin, Spring Boot 3.x, Spring Data Redis, Resilience4j

---

## 1. Overview

Assembles and serves the user's content feed. Orchestrates calls to rec-system (rankings) and content-aggregation-system (content data), manages caching in Redis, handles pagination.

**Responsibilities:**
- Serve paginated feed (GET /feed with opaque cursor)
- Cache feed lists in Redis LIST per user
- Cache content objects in Redis STRING
- Prefetch next batch when user reaches 50% of current
- Cold-start fallback for new users / rec-system unavailable
- Invalidate caches on Kafka events
- Circuit breaker + fallback for rec-system and content-service

**Not responsible for:**
- Ranking/scoring (→ rec-system)
- Content storage (→ content-aggregation-system)
- User profiles (→ user-service)

---

## 2. Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Runtime | Kotlin + Spring Boot 3.x | Application framework |
| Database | PostgreSQL 16 + Spring Data JDBC | User collections (bookmarks, likes, dislikes) |
| Redis | Spring Data Redis (Lettuce) | Feed LIST cache, content STRING cache, cold-start cache |
| HTTP Client | RestClient (Spring 6.1+) | Calls to rec-system and content-aggregation-system |
| Resilience | Resilience4j (circuit breaker, time limiter, retry) | Fault tolerance for sync HTTP calls |
| Kafka | Spring Kafka | Consume cache invalidation events |
| Serialization | Jackson (Kotlin module) | JSON serialization for Redis and HTTP |
| Build | Gradle (Kotlin DSL) | Build system |
| Container | Docker | Deployment |

---

## 3. API Endpoints

Base path: `/api/feed`  
All endpoints require authentication (X-User-Id from gateway).

### 3.1 GET /api/feed

Get paginated feed for current user.

```
GET /api/feed
GET /api/feed?cursor=eyJvIjoyMH0
GET /api/feed?refresh=true
X-User-Id: 550e8400-...
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `cursor` | string | no | Opaque cursor from previous response. Omit for first page |
| `refresh` | bool | no | `true` → invalidate cached feed, fetch fresh recommendations from rec-system. Used for pull-to-refresh |

**`refresh=true` behavior:**
1. `DEL feed:user:{userId}` (invalidate current cached feed)
2. `POST /recommendations` to rec-system → fresh 120 IDs
3. Cache new list, return first page
4. **Ignore cursor** if both `refresh` and `cursor` are set — refresh always starts from page 1

**Difference from prefetch:** Prefetch (at 50% scroll) extends the current batch asynchronously. `refresh=true` replaces the entire feed synchronously.

**Response 200:**
```json
{
  "items": [
    {
      "id": "a1b2c3d4-0000-0000-0000-000000000001",
      "title": "Новый прорыв в квантовых вычислениях",
      "description": "Краткое описание статьи",
      "content": "<p>Полный HTML текст...</p>",
      "content_format": "HTML",
      "source_type": "HABR",
      "source_subtype": "company",
      "url": "https://habr.com/ru/articles/123456/",
      "published_at": "2026-04-03T10:00:00Z",
      "author_name": "techwriter",
      "media": [
        {"type": "image", "url": "https://s3.../img.jpg", "width": 800, "height": 600}
      ],
      "metadata": {},
      "related_ids": ["uuid-5", "uuid-8"]
    }
  ],
  "cursor": "eyJvIjoyMH0",
  "has_next": true
}
```

| Field | Type | Description |
|-------|------|-------------|
| `items` | list[ContentBatchItem] | Content objects for current page, max 20. Format matches content-aggregator response |
| `cursor` | string? | Opaque cursor for next page. `null` if no more items |
| `has_next` | bool | Whether more items exist beyond current page |

**Note:** feed-service passes ContentBatchItem objects from content-aggregator as-is (no transformation). The object structure is defined in `contracts/content-aggregator-design.md`.

**Response 200 (empty feed):**
```json
{
  "items": [],
  "cursor": null,
  "has_next": false,
  "message": "Feed temporarily unavailable"
}
```

---

### 3.2 GET /api/feed/content/{id}

Get single content item by ID. Returns from Redis cache if available, otherwise fetches from content-aggregator.

```
GET /api/feed/content/a1b2c3d4-0000-0000-0000-000000000001
X-User-Id: 550e8400-...
```

**Response 200:**
```json
{
  "id": "a1b2c3d4-0000-0000-0000-000000000001",
  "title": "Новый прорыв в квантовых вычислениях",
  "description": "...",
  "content": "<p>Полный HTML...</p>",
  "content_format": "HTML",
  "source_type": "HABR",
  "source_subtype": "company",
  "url": "https://habr.com/ru/articles/123456/",
  "published_at": "2026-04-03T10:00:00Z",
  "author_name": "techwriter",
  "media": [...],
  "metadata": {},
  "related_ids": ["uuid-5", "uuid-8"]
}
```

**Response 404:** Content not found

**Flow:**
1. Check Redis `content:{id}` → cache hit → return immediately
2. Cache miss → `POST /api/v1/content/batch` to content-aggregator with `{ids: [id], include_related: true}`
3. If returned in `items` → cache (24h) + return
4. If in `not_found` → set negative cache `content:miss:{id}` (5 min) → 404

**Use case:** Frontend already has content from batch (feed page), but this endpoint is needed for:
- Deep links (user shares URL to specific article)
- Related content click (user clicks on related_id)
- Reload after app was in background

---

### 3.3 GET /health

```
GET /health

Response 200:
{"status": "ok", "service": "feed-service", "redis": "connected"}

Response 503:
{"status": "degraded", "service": "feed-service", "redis": "disconnected"}
```

Checks: Redis connectivity, PostgreSQL connectivity.

---

### 3.4 POST /api/feed/bookmarks/{contentId}

Add content to user's bookmarks.

```
POST /api/feed/bookmarks/a1b2c3d4-0000-0000-0000-000000000001
X-User-Id: 550e8400-...
```

**Response 201:**
```json
{"content_id": "a1b2c3d4-...", "action": "bookmarked"}
```

**Response 409:** Already bookmarked

**Side effect:** `INSERT INTO user_bookmarks (user_id, content_id) ON CONFLICT DO NOTHING`

### DELETE /api/feed/bookmarks/{contentId}

Remove from bookmarks.

```
DELETE /api/feed/bookmarks/a1b2c3d4-0000-0000-0000-000000000001
X-User-Id: 550e8400-...
```

**Response 200:**
```json
{"content_id": "a1b2c3d4-...", "action": "unbookmarked"}
```

### GET /api/feed/bookmarks

List bookmarked content (newest first), with full content objects.

```
GET /api/feed/bookmarks
GET /api/feed/bookmarks?cursor=eyJvIjoyMH0
X-User-Id: 550e8400-...
```

**Response 200:** Same format as GET /api/feed:
```json
{
  "items": [ContentBatchItem, ...],
  "cursor": "eyJvIjoyMH0",
  "has_next": true
}
```

**Flow:**
1. `SELECT content_id FROM user_bookmarks WHERE user_id = ? ORDER BY created_at DESC LIMIT 21 OFFSET ?`
2. Fetch content via `getContentBatch(ids)` (same method as feed — Redis cache → content-aggregator)
3. Paginate with opaque cursor (same cursor format as feed)

---

### 3.5 POST /api/feed/likes/{contentId}

```
POST /api/feed/likes/a1b2c3d4-...
X-User-Id: 550e8400-...
```

**Response 201:** `{"content_id": "...", "action": "liked"}`  
**Response 409:** Already liked

Side effect: `INSERT INTO user_likes`. If content was previously disliked → `DELETE FROM user_dislikes` (mutual exclusion).

### DELETE /api/feed/likes/{contentId}

**Response 200:** `{"content_id": "...", "action": "unliked"}`

### GET /api/feed/likes

Same format as GET /api/feed/bookmarks. Paginated list of liked content with full objects.

---

### 3.6 POST /api/feed/dislikes/{contentId}

```
POST /api/feed/dislikes/a1b2c3d4-...
X-User-Id: 550e8400-...
```

**Response 201:** `{"content_id": "...", "action": "disliked"}`  
**Response 409:** Already disliked

Side effect: `INSERT INTO user_dislikes`. If content was previously liked → `DELETE FROM user_likes` (mutual exclusion).

### DELETE /api/feed/dislikes/{contentId}

**Response 200:** `{"content_id": "...", "action": "undisliked"}`

### GET /api/feed/dislikes

Same format as GET /api/feed/bookmarks. Paginated list of disliked content with full objects.

---

### 3.7 GET /api/feed/content/{contentId}/status

Check if current user has liked/disliked/bookmarked a specific content item. Used by frontend to render button states.

```
GET /api/feed/content/a1b2c3d4-.../status
X-User-Id: 550e8400-...
```

**Response 200:**
```json
{
  "liked": false,
  "disliked": false,
  "bookmarked": true
}
```

**Implementation:** 3 EXISTS queries (fast with PK index).

---

### Like/Dislike/Bookmark mutual exclusion rules

| Action | Removes |
|--------|---------|
| Like | Existing dislike for same content |
| Dislike | Existing like for same content |
| Bookmark | Nothing (bookmark is independent of like/dislike) |

A user can have: like + bookmark, dislike + bookmark, but NOT like + dislike.

---

### Interaction signals for rec-system

When user likes/dislikes/bookmarks, the **frontend** also sends these as part of the regular interaction batch to `POST /api/interactions/batch`:

| Collection action | Frontend sends as interaction | rec-system signal |
|-------------------|------------------------------|-------------------|
| like | `action_type: "click"` | LIKE (+0.60) |
| dislike | `action_type: "hide"` | DISLIKE (-0.70) |
| bookmark | `action_type: "save"` | BOOKMARK (+0.80) |

**feed-service does NOT send interactions to Kafka** — that's the frontend's responsibility via user-interactions-service. feed-service only manages the collection storage.

---

## 4. Feed Assembly Flow

```kotlin
fun getFeed(userId: UUID, cursorStr: String?, refresh: Boolean = false, limit: Int = 20): FeedResponse {
    val cacheKey = "feed:user:$userId"

    // 0. Pull-to-refresh: invalidate cache, start fresh
    if (refresh) {
        redis.delete(cacheKey)
        return buildFeedFromRecSystem(userId, limit)
    }

    val offset = cursorStr?.let { decodeCursor(it) } ?: 0

    // 1. Check if cached feed exists
    val totalSize = redis.opsForList().size(cacheKey) ?: 0

    if (totalSize == 0L && offset == 0) {
        // Cache miss on first page — fetch from rec-system
        return buildFeedFromRecSystem(userId, limit)
    }

    if (totalSize == 0L && offset > 0) {
        // Cache expired mid-session — rebuild
        return buildFeedFromRecSystem(userId, limit)
    }

    // 2. Check prefetch trigger (50% of batch)
    if (offset >= totalSize * 0.5 && !prefetchInProgress(userId)) {
        triggerAsyncPrefetch(userId)
    }

    // 3. Read page from Redis LIST
    val ids = redis.opsForList().range(cacheKey, offset.toLong(), (offset + limit - 1).toLong())
        ?: emptyList()

    if (ids.isEmpty()) {
        // Scrolled past end, no prefetch ready
        return FeedResponse(items = emptyList(), cursor = null, hasNext = false)
    }

    // 4. Fetch content objects (with cache)
    val content = getContentBatch(ids.map { it.toString() })

    // 5. Build response
    val hasNext = offset + limit < totalSize
    val nextCursor = if (hasNext) encodeCursor(offset + limit) else null

    return FeedResponse(items = content, cursor = nextCursor, hasNext = hasNext)
}
```

### Build Feed from rec-system

```kotlin
@CircuitBreaker(name = "rec-system", fallbackMethod = "recSystemFallback")
@TimeLimiter(name = "rec-system")
fun buildFeedFromRecSystem(userId: UUID, limit: Int): FeedResponse {
    val response = recSystemClient.getRecommendations(userId, count = 120)

    // Cache in Redis LIST
    val cacheKey = "feed:user:$userId"
    val tempKey = "feed:user:$userId:tmp"
    redis.opsForList().rightPushAll(tempKey, response.items)
    redis.rename(tempKey, cacheKey)
    redis.expire(cacheKey, Duration.ofMinutes(30))

    val pageIds = response.items.take(limit)
    val content = getContentBatch(pageIds)

    return FeedResponse(
        items = content,
        cursor = if (response.items.size > limit) encodeCursor(limit) else null,
        hasNext = response.items.size > limit
    )
}

fun recSystemFallback(userId: UUID, limit: Int, ex: Throwable): FeedResponse {
    // Fallback 1: try cold-start
    val coldStart = try {
        recSystemClient.getColdStart(count = 30)
    } catch (e: Exception) { null }

    if (coldStart != null) {
        return FeedResponse(items = getContentBatch(coldStart.items.take(limit)), 
            cursor = null, hasNext = false)
    }

    // Fallback 2: globally cached cold-start (written by rec-system APScheduler)
    val trendingJson = redis.opsForValue().get("rec:cold-start:trending")
    val cached = trendingJson?.let { objectMapper.readValue<List<String>>(it) }
    if (!cached.isNullOrEmpty()) {
        return FeedResponse(items = getContentBatch(cached.take(limit)),
            cursor = null, hasNext = false)
    }

    // Fallback 3: empty feed
    return FeedResponse(items = emptyList(), cursor = null, hasNext = false,
        message = "Feed temporarily unavailable")
}
```

### Content Fetching with Cache

**Important:** content-aggregator-service returns `items` as `Map<String, ContentBatchItem>` (not a list), and `not_found` (not `missing_ids`). Port is **8086**, base path is `/api/v1/content`.

```kotlin
@CircuitBreaker(name = "content-service")
@TimeLimiter(name = "content-service")
@Retry(name = "content-service")
fun getContentBatch(ids: List<String>): List<ContentBatchItem> {
    // 1. Check Redis cache (24h TTL)
    val keys = ids.map { "content:$it" }
    val cached = redis.opsForValue().multiGet(keys) ?: emptyList()

    val result = mutableMapOf<String, ContentBatchItem>()
    val uncachedIds = mutableListOf<String>()

    ids.forEachIndexed { i, id ->
        val value = cached.getOrNull(i)
        if (value != null) {
            result[id] = objectMapper.readValue(value.toString(), ContentBatchItem::class.java)
        } else {
            uncachedIds.add(id)
        }
    }

    // 2. Check negative cache (5 min TTL) — skip known-missing IDs
    val negativeCacheKeys = uncachedIds.map { "content:miss:$it" }
    val negativeHits = redis.opsForValue().multiGet(negativeCacheKeys) ?: emptyList()
    val toFetch = uncachedIds.filterIndexed { i, _ -> negativeHits.getOrNull(i) == null }

    // 3. Fetch from content-aggregator-service (port 8086)
    if (toFetch.isNotEmpty()) {
        val response = contentAggregatorClient.postBatch(
            ContentBatchRequest(ids = toFetch, includeRelated = true, relatedLimit = 5)
        )
        // response.items is Map<String, ContentBatchItem>
        response.items.forEach { (id, item) ->
            redis.opsForValue().set("content:$id",
                objectMapper.writeValueAsString(item), Duration.ofHours(24))
            if (item.relatedIds != null) {
                redis.opsForValue().set("related:$id",
                    objectMapper.writeValueAsString(item.relatedIds), Duration.ofHours(2))
            }
            result[id] = item
        }
        // Negative cache for not-found IDs
        response.notFound.forEach { id ->
            redis.opsForValue().set("content:miss:$id", "1", Duration.ofMinutes(5))
        }
    }

    // 4. Preserve original order, skip missing
    return ids.mapNotNull { id -> result[id] }
}
```

---

## 5. Database Schema (feed-db)

```sql
-- User bookmarks
CREATE TABLE user_bookmarks (
    user_id     UUID NOT NULL,
    content_id  UUID NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

CREATE INDEX idx_bookmarks_user_ts ON user_bookmarks (user_id, created_at DESC);

-- User likes
CREATE TABLE user_likes (
    user_id     UUID NOT NULL,
    content_id  UUID NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

CREATE INDEX idx_likes_user_ts ON user_likes (user_id, created_at DESC);

-- User dislikes
CREATE TABLE user_dislikes (
    user_id     UUID NOT NULL,
    content_id  UUID NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

CREATE INDEX idx_dislikes_user_ts ON user_dislikes (user_id, created_at DESC);
```

**3 separate tables** (not 1 with action_type) — simpler queries, clearer indexes, no enum filtering.

**Mutual exclusion** (like ↔ dislike) enforced in application code, not DB constraints.

---

## 6. Kafka Integration

### 6.1 Consumed Events

#### `recommendations.updated`

**Producer:** rec-system  
**Consumer group:** `feed-service-invalidation`

```json
{
  "event_type": "recommendations.updated",
  "user_id": "550e8400-...",
  "reason": "onboarding_complete",
  "timestamp": "2026-04-03T12:10:00Z"
}
```

**Behavior:** `DEL feed:user:{userId}` from Redis. No proactive re-computation.

#### `content.updated` *(deferred — not implemented yet)*

Content-aggregation-system currently does NOT produce Kafka events. Content cache invalidation relies on TTL (24h). This topic may be added later if content freshness becomes a requirement.

When implemented:
```json
{
  "event_type": "content.updated",
  "content_id": "a1b2c3d4-...",
  "action": "updated"
}
```

**Behavior:** `DEL content:{contentId}` from Redis. Next content fetch for this ID will get fresh data.

### 6.2 Produced Events

**None.** feed-service does not produce Kafka events.

---

## 7. Redis Usage

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `feed:user:{userId}` | LIST | 30 min | Cached ranked content IDs from rec-system |
| `feed:user:{userId}:tmp` | LIST | Transient | Temp key for atomic RENAME during cache population |
| `feed:user:{userId}:prefetch` | STRING "1" | 60s | Lock to prevent concurrent prefetch requests |
| `rec:cold-start:trending` | STRING (JSON) | 1 hour | **Written by rec-system** (APScheduler). feed-service reads only |
| `content:{contentId}` | STRING (JSON) | **24 hours** | Cached ContentBatchItem from content-aggregator |
| `related:{contentId}` | STRING (JSON) | **2 hours** | Cached related_ids list |
| `content:miss:{contentId}` | STRING "1" | **5 min** | Negative cache — ID confirmed not-found |

### Memory Estimate

| Data | Per User | 10K Users | 100K Users |
|------|----------|-----------|------------|
| Feed LIST (120 UUIDs) | ~5 KB | ~50 MB | ~500 MB |
| Content cache (10K items) | shared | ~20 MB | ~20 MB |
| Total | — | ~70 MB | ~520 MB |

---

## 8. Resilience4j Configuration

### HTTP Client Timeouts (RestClient)

**TimeLimiter is NOT used.** feed-service uses RestClient (blocking). `@TimeLimiter` cancels the `CompletableFuture` but does NOT interrupt the blocked thread — under load this exhausts the thread pool. Instead, timeouts are set at the HTTP client level (socket-level), which actually releases the thread.

```kotlin
@Configuration
class HttpClientConfig {

    @Bean
    fun recSystemRestClient(): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
        return RestClient.builder()
            .baseUrl("http://rec-system:8000")
            .requestFactory(JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(Duration.ofSeconds(2))   // ← releases thread on timeout
            })
            .build()
    }

    @Bean
    fun contentAggregatorRestClient(): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build()
        return RestClient.builder()
            .baseUrl("http://content-aggregation-system:8086")
            .requestFactory(JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(Duration.ofSeconds(1))
            })
            .build()
    }
}
```

| Target | Connect timeout | Read timeout | Why |
|--------|----------------|-------------|-----|
| rec-system | 2s | 2s | ML inference can be slow, but >2s = degraded UX |
| content-aggregator | 1s | 1s | Simple DB reads, should be fast |

### Resilience4j Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      rec-system:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
        record-exceptions:
          - java.io.IOException
          - java.net.http.HttpTimeoutException
      content-service:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
  retry:
    instances:
      content-service:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - java.net.http.HttpTimeoutException
```

**No `timelimiter` section** — timeouts handled by RestClient at socket level.

**Decorator order (outer → inner → call):** `CircuitBreaker → Retry → call`

```
Request
  → CircuitBreaker (sees 1 final result per request, fast-fails if circuit open)
    → Retry (3 attempts — all hidden from CB, each capped by RestClient read timeout)
      → RestClient call (connect 1s + read 1s = max 2s per attempt, thread released on timeout)
```

**Why no TimeLimiter:**
- `@TimeLimiter` wraps call in `CompletableFuture` — cancels the future on timeout, but the **blocking thread continues running** until socket timeout
- Under load: 100 requests to slow rec-system → 100 threads blocked → Tomcat thread pool exhausted → service dead
- RestClient `readTimeout` throws `HttpTimeoutException` at socket level → thread is **actually released**
- Total worst case: 3 retries × 2s read timeout × 2x backoff = ~10s. Acceptable for edge case; typical: 1 attempt < 200ms

**Spring annotation order:**
```kotlin
@CircuitBreaker(name = "content-service", fallbackMethod = "contentFallback")
@Retry(name = "content-service")
fun getContentBatch(...) { ... }
```

---

## 9. Scheduled Jobs (Quartz)

**None required.** Cold-start cache (`rec:cold-start:trending`) is owned and refreshed by rec-system's APScheduler (every 60 min). feed-service only reads this key.

If rec-system is down and the key expired, feed-service falls back to `GET /recommendations/cold-start` which will auto-populate the cache on response.

---

## 10. Service Dependencies

| Dependency | Protocol | Purpose | Failure Mode |
|-----------|----------|---------|--------------|
| PostgreSQL (feed-db) | JDBC | User collections (bookmarks, likes, dislikes) | Collections endpoints return 503. Feed endpoint unaffected |
| Redis | TCP | Feed cache, content cache, cold-start cache | Service degraded — every request hits rec-system + content-service. High latency |
| rec-system | HTTP | POST /recommendations, GET /recommendations/cold-start | Circuit breaker → fallback to Redis cold-start cache → empty feed |
| content-aggregation-system | HTTP (port 8086) | POST /api/v1/content/batch | Circuit breaker + retry → items with missing content skipped |
| Kafka | TCP | Consume `recommendations.updated` | Cache not invalidated — stale feed served until TTL expiry (30 min max) |

---

## 11. Content-Aggregation-System Contract

**Service:** content-aggregator-service (Kotlin/Spring Boot 3.4.5, port **8086**)  
**Role:** Read-only REST API. No Kafka, no Redis. Reads from shared `data_flow.published_content` table.

```
POST http://content-aggregation-system:8086/api/v1/content/batch
Content-Type: application/json

{
  "ids": ["uuid-1", "uuid-2", "uuid-99"],
  "include_related": true,
  "related_limit": 5
}
```

**Response 200:**
```json
{
  "items": {
    "uuid-1": {
      "id": "uuid-1",
      "title": "Заголовок статьи",
      "description": "Краткое описание",
      "content": "<p>Полный HTML с S3 URLs...</p>",
      "content_format": "HTML",
      "source_type": "HABR",
      "source_subtype": "company",
      "url": "https://habr.com/...",
      "published_at": "2026-04-03T12:00:00Z",
      "author_name": "username",
      "media": [
        {"type": "image", "url": "https://s3.../img.jpg", "width": 800, "height": 600}
      ],
      "metadata": {},
      "related_ids": ["uuid-5", "uuid-8"]
    }
  },
  "not_found": ["uuid-99"]
}
```

**Key differences from initial design:**
- `items` is a **Map\<String, ContentBatchItem\>**, not a List (O(1) lookup by ID)
- Field `not_found` (not `missing_ids`)
- `include_related` + `related_limit` — returns related content IDs per item
- Content is **full HTML** (not preview/summary)
- Media is a structured array (not a single `thumbnail_url`)
- `source_type` is an enum (HABR, VCRU, TELEGRAM_CHANNEL), not a display name

See `contracts/content-aggregator-design.md` for full specification.

---

## 12. Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: feed-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:5432/feed_db
    username: ${DB_USER:feed}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 15
      connection-timeout: 5000
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:kafka:9092}
    consumer:
      group-id: feed-service-invalidation
      auto-offset-reset: latest
      enable-auto-commit: false
services:
  rec-system:
    url: http://rec-system:8000
  content-aggregation:
    url: http://content-aggregation-system:8086

feed:
  page-size: 20
  cache:
    feed-ttl-minutes: 30
    content-ttl-hours: 24
    related-ttl-hours: 2
    negative-cache-ttl-minutes: 5
  prefetch:
    trigger-percent: 50
    batch-size: 120

gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}
```

---

## 13. Cursor Implementation

```kotlin
private val objectMapper = ObjectMapper()

fun encodeCursor(offset: Int): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"o":$offset}""".toByteArray())

fun decodeCursor(cursor: String): Int =
    try {
        val json = String(Base64.getUrlDecoder().decode(cursor))
        objectMapper.readTree(json).get("o").asInt()
    } catch (e: Exception) {
        throw BadRequestException("Invalid cursor")
    }
```

---

**Краткое резюме (RU):** feed-service — оркестратор ленты + хранение пользовательских коллекций. Endpoints: GET /feed (cursor + refresh), GET /feed/content/{id}, CRUD для bookmarks/likes/dislikes, GET /feed/content/{id}/status. PostgreSQL (feed-db) для коллекций (3 таблицы). Redis для кэшей (feed LIST 30мин, content STRING 24ч, related 2ч, negative cache 5мин). Cold-start cache (`rec:cold-start:trending`) пишет rec-system. Kafka: потребляет `recommendations.updated` для lazy-инвалидации. HTTP: rec-system:8000 (rankings) + content-aggregator:8086 (контент, Map-формат) с Resilience4j.
