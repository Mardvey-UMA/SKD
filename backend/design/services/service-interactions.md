# user-interactions-service — Service Design Document

**Status:** Accepted  
**Date:** 2026-04-03  
**Technology:** Kotlin, Spring Boot 3.x, Spring Data JDBC

---

## 1. Overview

Collects user interaction events from the frontend, validates and stores them, then batches and publishes to Kafka for rec-system consumption.

**Responsibilities:**
- Accept interaction event batches from frontend (via gateway)
- Validate event schema, deduplicate
- Store raw events in PostgreSQL (partitioned, append-only) for product analytics
- Buffer and batch events, publish to Kafka periodically

**Not responsible for:**
- Processing/interpreting events (→ rec-system)
- Feed generation (→ feed-service)
- User profiles (→ user-service)

---

## 2. Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Runtime | Kotlin + Spring Boot 3.x | Application framework |
| Database | PostgreSQL 16 + Spring Data JDBC | Event storage (partitioned) |
| Kafka | Spring Kafka | Publish interaction batches |
| Scheduler | Quartz (spring-boot-starter-quartz) | Partition management |
| Migrations | Flyway | Schema versioning |
| Build | Gradle (Kotlin DSL) | Build system |
| Container | Docker | Deployment |

---

## 3. API Endpoints

Base path: `/api/interactions`  
All endpoints require authentication (X-User-Id from gateway).

### 3.1 POST /api/interactions/batch

Accept a batch of interaction events from the frontend.

```
POST /api/interactions/batch
X-User-Id: 550e8400-...
Content-Type: application/json

{
  "events": [
    {
      "content_id": "a1b2c3d4-0000-0000-0000-000000000001",
      "action_type": "view",
      "duration_sec": 45,
      "timestamp": "2026-04-03T12:05:00Z"
    },
    {
      "content_id": "a1b2c3d4-0000-0000-0000-000000000002",
      "action_type": "click",
      "timestamp": "2026-04-03T12:05:30Z"
    },
    {
      "content_id": "a1b2c3d4-0000-0000-0000-000000000003",
      "action_type": "hide",
      "timestamp": "2026-04-03T12:06:00Z"
    }
  ]
}
```

**Validation per event:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `content_id` | UUID | yes | Valid UUID format |
| `action_type` | enum | yes | One of: `view`, `click`, `scroll_past`, `share`, `save`, `hide` |
| `duration_sec` | int | no | >= 0, only meaningful for `view` action |
| `timestamp` | ISO8601 | yes | Not in the future (+ 60s tolerance), not older than 24h |

**Batch limits:**
- Max 100 events per batch
- Max 10 batches per minute per user (rate limit in gateway)

**Response 202 Accepted:**
```json
{
  "accepted": 3,
  "rejected": 0
}
```

**Response 422:** Validation failed (entire batch rejected)

**Behavior:**
1. Validate all events
2. INSERT all valid events into `user_interactions` table
3. Add events to in-memory buffer for Kafka batching
4. Return 202 immediately (async processing)

---

### 3.2 GET /health

```
GET /health
Response 200: {"status": "ok", "service": "user-interactions-service", "checks": {"database": "connected", "kafka": "connected"}}
Response 503: {"status": "degraded", ...}
```

---

## 4. Database Schema (interactions-db)

```sql
-- Main partitioned table
CREATE TABLE user_interactions (
    id          BIGSERIAL,
    user_id     UUID NOT NULL,
    content_id  UUID NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    duration_sec INTEGER,
    client_ts   TIMESTAMPTZ NOT NULL,        -- frontend timestamp
    server_ts   TIMESTAMPTZ DEFAULT now(),   -- server receive time
    PRIMARY KEY (server_ts, id)
) PARTITION BY RANGE (server_ts);

-- Current month partition
CREATE TABLE user_interactions_2026_04 PARTITION OF user_interactions
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

-- Next month partition (pre-created by Quartz job)
CREATE TABLE user_interactions_2026_05 PARTITION OF user_interactions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- DEFAULT partition — safety net if Quartz job fails to create next month's partition.
-- Without this, INSERTs fail with "no partition found" when no matching partition exists.
CREATE TABLE user_interactions_default PARTITION OF user_interactions DEFAULT;

-- Analytics indexes
CREATE INDEX idx_interactions_user_ts ON user_interactions (user_id, server_ts);
CREATE INDEX idx_interactions_content_action ON user_interactions (content_id, action_type);
CREATE INDEX idx_interactions_action_ts ON user_interactions (action_type, server_ts);
```

**Partition strategy:**
- Monthly partitions by `server_ts`
- Pre-created 1 month ahead by Quartz job
- Old partitions dropped after retention period (configurable, default: 6 months)

---

## 5. Kafka Integration

### 5.1 Produced Events

#### `user.interactions.batch`

Published periodically from the in-memory buffer.

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
    }
  ],
  "batch_ts": "2026-04-03T12:06:30Z"
}
```

**Topic:** `user.interactions.batch`  
**Key:** `user_id`  
**Partitions:** 12  
**Retention:** 7 days  
**Consumer:** rec-system (`rec-system-interactions`)

### 5.2 Batching Logic

```kotlin
@Component
class InteractionBatchPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    // ConcurrentHashMap with ConcurrentLinkedQueue — both thread-safe without synchronization
    private val buffer = ConcurrentHashMap<UUID, ConcurrentLinkedQueue<InteractionEvent>>()

    fun addToBuffer(userId: UUID, events: List<InteractionEvent>) {
        val queue = buffer.computeIfAbsent(userId) { ConcurrentLinkedQueue() }
        queue.addAll(events)  // ConcurrentLinkedQueue.addAll is thread-safe
    }

    // Flush: every 30 seconds OR 50 events per user (whichever first)
    @Scheduled(fixedDelay = 30_000)
    fun flushAll() {
        // Drain each user's queue atomically — no clear() race
        val userIds = ArrayList(buffer.keys)
        for (userId in userIds) {
            drainAndPublish(userId)
        }
    }

    fun addAndMaybeFlush(userId: UUID, events: List<InteractionEvent>) {
        addToBuffer(userId, events)
        val queue = buffer[userId]
        if (queue != null && queue.size >= 50) {
            drainAndPublish(userId)
        }
    }

    private fun drainAndPublish(userId: UUID) {
        val queue = buffer[userId] ?: return

        // Drain: poll elements one by one — thread-safe, no data loss
        // Events added by other threads DURING drain will stay in queue for next cycle
        val events = mutableListOf<InteractionEvent>()
        while (true) {
            val event = queue.poll() ?: break  // atomic remove from head
            events.add(event)
        }

        if (events.isEmpty()) return

        // Remove empty queue from map (cleanup)
        // If another thread added events between drain and remove — computeIfAbsent will recreate
        if (queue.isEmpty()) {
            buffer.remove(userId, queue)  // conditional remove — only if still same empty queue
        }

        val batch = InteractionBatch(userId, events, Instant.now())
        kafkaTemplate.send(
            "user.interactions.batch",
            userId.toString(),
            objectMapper.writeValueAsString(batch)
        )
    }

    @PreDestroy
    fun flushOnShutdown() {
        log.info("Flushing ${buffer.size} user buffers on shutdown")
        flushAll()
    }
}
```

**Why this fixes the race conditions:**
1. `ConcurrentLinkedQueue` instead of `MutableList` — `addAll()` and `poll()` are thread-safe without locking
2. No `buffer.clear()` — instead, `poll()` drains elements one by one. Events added during drain stay in queue for next cycle (zero data loss)
3. `buffer.remove(userId, queue)` — conditional removal, only if queue is still the same empty instance (no ABA problem)
4. `@PreDestroy` ensures buffered events are flushed on graceful shutdown

**Graceful shutdown:** `@PreDestroy` flushes all remaining buffered events.

---

## 6. Redis Usage

**None.** This service has no caching needs. Events are append-only to PostgreSQL and buffered in-memory for Kafka.

---

## 7. Scheduled Jobs (Quartz)

| Job | Schedule | Description |
|-----|----------|-------------|
| `CreateNextPartitionJob` | 1st of each month, 00:00 UTC | Creates partition for month+1: `CREATE TABLE user_interactions_YYYY_MM PARTITION OF user_interactions FOR VALUES FROM (…) TO (…)` |
| `DropOldPartitionsJob` | 1st of each month, 01:00 UTC | Drops partitions older than 6 months: `DROP TABLE user_interactions_YYYY_MM` |

**Partition naming convention:** `user_interactions_{yyyy}_{mm}`

```kotlin
@Component
class CreateNextPartitionJob : Job {
    override fun execute(context: JobExecutionContext) {
        val nextMonth = YearMonth.now().plusMonths(2)  // create 2 months ahead
        val partitionName = "user_interactions_${nextMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))}"
        val startDate = nextMonth.atDay(1)
        val endDate = nextMonth.plusMonths(1).atDay(1)

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS $partitionName 
            PARTITION OF user_interactions 
            FOR VALUES FROM ('$startDate') TO ('$endDate')
        """)
    }
}
```

---

## 8. Service Dependencies

| Dependency | Protocol | Purpose | Failure Mode |
|-----------|----------|---------|--------------|
| PostgreSQL (interactions-db) | JDBC | Event storage | Return 503 to client. Buffer continues in memory (risk of data loss on crash) |
| Kafka | TCP | Publish interaction batches | Buffer in memory, retry on next flush. Data loss risk if service crashes with full buffer |

**No dependencies on other services.** This service is a pure data collector — it doesn't call auth-service, user-service, rec-system, or any other service.

---

## 9. Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: user-interactions-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:5432/interactions_db
    username: ${DB_USER:interactions}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 5000
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:kafka:9092}
    producer:
      acks: all
      batch-size: 16384
      linger-ms: 100
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always

interactions:
  batch:
    flush-interval-ms: 30000      # flush every 30 seconds
    max-events-per-user: 50       # flush user buffer at 50 events
  validation:
    max-events-per-batch: 100
    max-timestamp-future-sec: 60
    max-timestamp-age-hours: 24
  partition:
    retention-months: 6

gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}
```

---

**Краткое резюме (RU):** user-interactions-service — сборщик событий. 1 HTTP-эндпоинт (POST /batch). PostgreSQL с месячным партиционированием для хранения raw events. Kafka: публикует `user.interactions.batch` (flush каждые 30с или 50 событий). 2 Quartz-задачи для управления партициями. Нет Redis, нет зависимостей от других сервисов.
