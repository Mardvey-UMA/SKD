# ADR-004: Data Consistency — Transactional Outbox Pattern

**Status:** Accepted  
**Date:** 2026-04-03  
**Decision Makers:** Architecture Team

## Context

Multiple services need to atomically update their database AND publish a Kafka event. Example: auth-service creates a user in PostgreSQL and must publish `user.registered` to Kafka. If the service crashes between DB commit and Kafka send, the event is lost.

**Critical finding:** Spring Cloud Stream `@Transactional` + StreamBridge is **NOT a real Outbox**. Official Spring blog (Oct 2023): "if the application crashes after the database operation, no data will be sent to Kafka."

## Options

### Option A: Outbox + @Scheduled Polling

| Aspect           | Details                                                                                                           |
| ---------------- | ----------------------------------------------------------------------------------------------------------------- |
| **Mechanism**    | Write event to `outbox` table in same DB transaction. Separate poller reads unpublished events and sends to Kafka |
| **Latency**      | Up to polling interval (500ms-5s)                                                                                 |
| **Dependencies** | None beyond existing PostgreSQL + Spring                                                                          |
| **Complexity**   | Low — ~50 lines of Kotlin                                                                                         |
| **DB Load**      | Polling query every interval. Index on `published_at IS NULL` keeps it fast                                       |

### Option B: Outbox + Debezium CDC

| Aspect           | Details                                                                                                                                   |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **Mechanism**    | Write to outbox table. Debezium reads PostgreSQL WAL → publishes to Kafka automatically                                                   |
| **Latency**      | Sub-second (<100ms typical)                                                                                                               |
| **Dependencies** | +Debezium (Kafka Connect), +PostgreSQL logical replication slot                                                                           |
| **Throughput**   | 5k-80k events/sec (Kafka Connect mode)                                                                                                    |
| **Risk**         | **WAL accumulation**: if connector stops and replication slot not removed, WAL fills PostgreSQL disk. Must monitor `pg_replication_slots` |

### Option C: Saga Pattern

| Aspect                 | Details                                                                                                      |
| ---------------------- | ------------------------------------------------------------------------------------------------------------ |
| **Mechanism**          | Orchestrated or choreographed compensating transactions                                                      |
| **When needed**        | Multi-step workflows requiring rollback of completed steps                                                   |
| **For auth→user flow** | **Overkill.** Creating a profile is `INSERT ... ON CONFLICT DO NOTHING` — idempotent, no compensation needed |

## Recommendation: Option A (Phase 1) → Option B (Phase 2)

**Phase 1 — Outbox + Polling:**
Start simple. Polling with 500ms-5s interval is sufficient for auth→user flow (not latency-sensitive).

**Phase 2 — Debezium CDC:**
Migrate when event throughput exceeds ~1k events/sec or when sub-second latency becomes a requirement.

**For auth→user-service specifically:**

- Saga NOT needed
- Idempotent consumer: `INSERT INTO profiles ... ON CONFLICT DO NOTHING`
- Retry: Kafka consumer with max 3 retries + exponential backoff
- Dead Letter Queue: `user.registered.DLQ` → alert → manual resolution

## Implementation Details

### Outbox Table (shared schema across services)

```sql
CREATE TABLE outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,   -- 'User', 'Subscription'
    aggregate_id    VARCHAR(255) NOT NULL,  -- entity UUID
    event_type      VARCHAR(100) NOT NULL,  -- 'user.registered'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    published_at    TIMESTAMPTZ             -- NULL = unpublished
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

### Outbox Poller

```kotlin
@Scheduled(fixedDelay = 5000) // 5 seconds
fun publishOutboxEvents() {
    val events = outboxRepository.findUnpublished(limit = 100)
    events.forEach { event ->
        try {
            kafkaTemplate.send(event.eventType, event.aggregateId, event.payload).get()
            outboxRepository.markPublished(event.id)
        } catch (e: Exception) {
            log.error("Failed to publish outbox event ${event.id}", e)
            // Will retry on next poll cycle
        }
    }
}
```

### Idempotent Consumer Example (user-service)

```kotlin
@KafkaListener(topics = ["user.registered"])
fun handleUserRegistered(event: UserRegisteredEvent) {
    // ON CONFLICT DO NOTHING — safe to replay
    userRepository.createIfNotExists(
        Profile(
            id = UUID.randomUUID(),
            authProviderId = event.authProviderId,
            email = event.email,
            createdAt = Instant.now()
        )
    )
    // Publish downstream event
    kafkaTemplate.send("user.created", UserCreatedEvent(userId, event.email))
}
```

### DLQ Strategy

```yaml
spring:
  kafka:
    consumer:
      properties:
        max.poll.retries: 3
        retry.backoff.ms: 1000
    # After 3 retries → Dead Letter Topic
    listener:
      ack-mode: RECORD
```

Monitor DLQ topic size. Alert if > 0 messages. Manual investigation and replay.

---

**Краткое резюме (RU):** Transactional Outbox + @Scheduled polling для старта. Spring Cloud Stream @Transactional — НЕ настоящий outbox (official Spring blog). Для auth→user: Saga не нужна, idempotent consumer + DLQ достаточно. Миграция на Debezium CDC при нагрузке >1k events/sec.
