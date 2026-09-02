# ADR-003: user-interactions-service — Dedicated Service vs Alternatives

**Status:** Accepted  
**Date:** 2026-04-03  
**Decision Makers:** Architecture Team

## Context

Frontend generates user interaction events (view, click, scroll_past, share, save, hide) that must reach rec-system. Three approaches possible.

## Options

| Criteria | Dedicated Service | Frontend → Kafka REST Proxy | Frontend → rec-system |
|----------|-------------------|---------------------------|----------------------|
| **Analytics** | Raw events in PostgreSQL — queryable | No persistent storage | rec-system must store/forward |
| **Batching** | Server-side (30s / 50 events) | None server-side | None |
| **Validation** | Schema validation, dedup | None | rec-system validates |
| **Security** | Kafka internals hidden | **Kafka topology exposed** to frontend | rec-system publicly routable |
| **SRP** | Clean separation | N/A | **rec-system = ingestion + ML** |
| **Infrastructure** | +1 service, +1 DB | +Kafka REST Proxy | None |
| **Reliability** | Events persisted before Kafka | Events lost if Kafka down | Events lost if rec-system down |

## Recommendation: Dedicated Service

**Rationale:**
1. **Analytics** — Raw interaction data is critical for product decisions. Lost forever without persistent storage.
2. **Decoupling** — rec-system focuses on ML, not HTTP event ingestion.
3. **Batching** — Reduces rec-system load (aggregated batches vs per-event calls).
4. **Validation** — Deduplication, schema checks at system boundary.
5. **Simple** — validate→store→batch→publish. Estimate: 3-5 days.

## Implementation

### Schema (interactions-db)
```sql
CREATE TABLE user_interactions (
    id          BIGSERIAL,
    user_id     UUID NOT NULL,
    content_id  UUID NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    duration_sec INTEGER,
    client_ts   TIMESTAMPTZ NOT NULL,
    server_ts   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (server_ts, id)
) PARTITION BY RANGE (server_ts);

CREATE INDEX idx_interactions_user ON user_interactions (user_id, server_ts);
CREATE INDEX idx_interactions_content ON user_interactions (content_id, action_type);
```

### Kafka Topic
```
Topic: user.interactions.batch | Key: user_id | Partitions: 12 | Retention: 7d
```

### Batching: flush every 30s OR 50 events per user (whichever first)

### API
```
POST /interactions/batch  →  202 Accepted
Body: { "events": [{content_id, action_type, duration_sec, timestamp}, ...] }
```

---

**Краткое резюме (RU):** Рекомендуется выделенный сервис. Хранение raw events для аналитики, батчинг для rec-system, валидация, decoupling от ML. Реализация: 3-5 дней.
