# ADR-005: Feed Caching Strategy

**Status:** Accepted  
**Date:** 2026-04-03 (revised)  
**Decision Makers:** Architecture Team

## Context

`GET /feed` is the primary read path. It requires:
1. Rankings from rec-system (HTTP, ~50-200ms)
2. Content from content-aggregation-system (HTTP, ~20-50ms)

Without caching, every feed request triggers 2+ synchronous HTTP calls.

## Previous Approach (Rejected): Redis ZSET + Score-Based Cursor

Initially proposed storing rec-system scores in a ZSET and paginating by score. **Rejected for 3 reasons:**

1. **Score leaking** — Exposing float scores outside rec-system is leaking an implementation detail. If rec-system switches to listwise ranking (LambdaMART), multi-objective optimization, or any model where absolute scores are meaningless, the caching layer breaks.

2. **Score collision bug** — Two items with identical scores (common in practice) cause `ZREVRANGEBYSCORE (0.85 -inf` to skip one. Workarounds (composite score + position) are unnecessary complexity.

3. **ZSET is overkill** — The cached list is immutable per user session. No concurrent writes, no index shifting. A simple LIST with stable offsets is sufficient.

## Decision: Immutable Ordered LIST + Offset Pagination + Predictive Prefetch

### Core Design

rec-system returns an **ordered list of content IDs** — order IS the ranking. No scores exposed.

```
rec-system response:  [id_1, id_2, ..., id_100]  (order = display order)
                            ↓
Redis:  LIST  feed:user:{userId}  →  [id_1, id_2, ..., id_100]
                            ↓
GET /feed?cursor=<opaque>   →  LRANGE feed:user:123 offset offset+limit-1
```

**The list is immutable for its lifetime.** It doesn't change until a new batch replaces it. This means offset-based pagination is inherently stable — the problem that score-based cursors were solving simply doesn't exist.

### Pagination

**Cursor format:** Opaque base64-encoded offset (not raw integer). This decouples frontend from implementation — we can change to keyset or token-based pagination later without breaking the API.

```kotlin
// Encode: offset 20 → "eyJvIjoyMH0=" (base64 of {"o":20})
fun encodeCursor(offset: Int): String =
    Base64.getEncoder().encodeToString("""{"o":$offset}""".toByteArray())

// Decode: "eyJvIjoyMH0=" → offset 20
fun decodeCursor(cursor: String): Int =
    objectMapper.readTree(Base64.getDecoder().decode(cursor)).get("o").asInt()
```

**Pagination flow:**
```
GET /feed                    → offset=0,  return items[0..19],   cursor_next=encode(20)
GET /feed?cursor=eyJvIjoyMH0 → offset=20, return items[20..39],  cursor_next=encode(40)
GET /feed?cursor=eyJvIjo0MH0 → offset=40, return items[40..59],  cursor_next=encode(60)
```

**has_next detection:** `LLEN feed:user:123` > offset + limit

### Predictive Prefetch

When user reaches ~50% of current batch, asynchronously request next batch from rec-system:

```kotlin
fun getFeed(userId: UUID, offset: Int, limit: Int): FeedResponse {
    val cacheKey = "feed:user:$userId"
    val totalSize = redis.opsForList().size(cacheKey) ?: 0

    // Prefetch trigger: user past 50% of current batch
    if (offset >= totalSize * 0.5 && !prefetchInProgress(userId)) {
        triggerAsyncPrefetch(userId)
    }

    // Fetch current page
    val ids = redis.opsForList().range(cacheKey, offset.toLong(), (offset + limit - 1).toLong())

    if (ids.isNullOrEmpty() && offset == 0) {
        // Cache miss on first page — sync fetch
        return buildFeedFromRecSystem(userId, limit)
    }

    // Fetch content for these IDs
    val content = getContentBatch(ids)

    return FeedResponse(
        items = content,
        cursor = if (offset + limit < totalSize) encodeCursor(offset + limit) else null,
        hasNext = offset + limit < totalSize
    )
}
```

**Prefetch flow:**
```
User scrolls to item #50 of 100
    ↓
feed-service fires async: POST rec-system /recommendations {user_id, count: 120}
    ↓
rec-system computes next batch (async, ~200ms-2s)
    ↓
feed-service receives response → stores as new list
    ↓
User reaches item #100 → seamless transition to next batch
```

### Edge Case: User Reaches End Before Prefetch Completes

For MVP, three options (ordered by simplicity):

1. **Return `has_next: false`** — Frontend shows "end of feed." User pulls-to-refresh later. Simplest, acceptable for MVP.

2. **Sync fetch with timeout** — If prefetch not ready, block for up to 2s (behind circuit breaker). If timeout → option 1.

3. **Buffer overflow** — Request 120 items from rec-system but only count first 100 for prefetch trigger. Extra 20 = runway. Still simple, slightly better UX.

**Recommendation for MVP: Option 1 + 3 combined.** Request 120 items, trigger prefetch at item ~50, buffer of 20 items at the end. If user still outruns prefetch — return `has_next: false`.

### Cache Population

```kotlin
fun buildFeedFromRecSystem(userId: UUID, limit: Int): FeedResponse {
    val rankings = recSystemClient.getRankings(userId, count = 120) // 100 + 20 buffer

    if (rankings.isEmpty()) {
        // Cold start fallback
        return getColdStartFeed(limit)
    }

    val cacheKey = "feed:user:$userId"
    val tempKey = "feed:user:$userId:temp"

    // Atomic replacement via RENAME
    redis.opsForList().rightPushAll(tempKey, rankings.map { it.contentId })
    redis.rename(tempKey, cacheKey)
    redis.expire(cacheKey, Duration.ofMinutes(30))

    val pageIds = rankings.take(limit).map { it.contentId }
    val content = getContentBatch(pageIds)

    return FeedResponse(
        items = content,
        cursor = if (rankings.size > limit) encodeCursor(limit) else null,
        hasNext = rankings.size > limit
    )
}
```

### Content Cache (Layer 2)

Content objects cached separately — shared across users:

```
Key:     content:{content_id}
Type:    STRING (JSON)
TTL:     24 hours
```

24h TTL is acceptable: platform aggregates external content (Habr, VC.ru, Telegram) — retractions are extremely rare. `content.updated` Kafka topic can be added later for faster invalidation if needed.

```kotlin
fun getContentBatch(ids: List<String>): List<Content> {
    // Multi-get from Redis
    val keys = ids.map { "content:$it" }
    val cached = redis.opsForValue().multiGet(keys)

    val hits = mutableListOf<Content>()
    val missIds = mutableListOf<String>()

    ids.forEachIndexed { i, id ->
        val value = cached?.get(i)
        if (value != null) hits.add(deserialize(value))
        else missIds.add(id)
    }

    // Fetch misses from content-aggregation-system
    if (missIds.isNotEmpty()) {
        val fetched = contentClient.getBatch(missIds) // POST /content/batch
        fetched.forEach { content ->
            redis.opsForValue().set("content:${content.id}", serialize(content), Duration.ofMinutes(5))
            hits.add(content)
        }
    }

    // Preserve original order
    return ids.mapNotNull { id -> hits.find { it.id == id } }
}
```

### Cache Invalidation

| Trigger | Kafka Topic | Action |
|---------|-------------|--------|
| New recommendations ready (prefetch complete) | `recommendations.updated` | Atomic LIST replacement via RENAME |
| Content updated | `content.updated` | `DEL content:{contentId}` |
| User re-onboards (changes interests) | Direct call | `DEL feed:user:{userId}` |

### Cold Start

```
Key:     feed:cold-start:default
Type:    LIST (global trending content, ~100 items)
TTL:     1 hour
Updated: periodically from content-aggregation (popular content)
```

New user → copy trending: `RPUSH feed:user:{new} + EXPIRE 1800`. Overwritten on first rec-system response after onboarding.

## Request Cost Analysis

| Scenario | HTTP Calls | Redis Ops | Estimated Latency |
|----------|-----------|-----------|-------------------|
| Cache HIT (feed + all content) | 0 | LRANGE + MGET = 2 | ~2-5ms |
| Cache HIT feed, partial content miss | 1 (batch content) | LRANGE + MGET + MSET = 3 | ~30-60ms |
| Cache MISS (first page) | 1 (rec-system) + 1 (content) | RPUSH + LRANGE + MGET + MSET = 4 | ~100-300ms |

## Redis Memory Estimate

| Data | Per User | 10K Users | 100K Users |
|------|----------|-----------|------------|
| Feed LIST (120 UUIDs, ~36 bytes each) | ~5 KB | ~50 MB | ~500 MB |
| Content cache (10K items, ~2KB each) | shared | ~20 MB | ~20 MB |
| Total | — | ~70 MB | ~520 MB |

Note: LIST is more memory-efficient than ZSET (no score storage overhead).

## Consequences

- rec-system API contract: returns `List<ContentId>` in display order — no scores exposed
- Immutable cache = stable offsets = no cursor bugs
- Simpler Redis operations (LIST + STRING) vs ZSET
- Prefetch adds mild complexity but prevents UX cliffs
- Need `POST /content/batch` endpoint on content-aggregation-system

---

**Краткое резюме (RU):** Redis LIST для иммутабельного упорядоченного списка от rec-system. Пагинация по offset (стабильный, т.к. список не меняется). Opaque cursor (base64 offset). Predictive prefetch на 50% батча. rec-system возвращает только порядок, без скоров — порядок и есть контракт. ZSET отвергнут: score leaking, collision bug, лишняя сложность.
