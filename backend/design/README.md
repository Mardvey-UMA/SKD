# Content Platform — Design Specification

**Location:** `design/`  
**Last updated:** 2026-04-04

Полная спецификация 6 новых микросервисов контент-платформы. Каждый документ содержит всё необходимое для реализации: API endpoints, БД-схемы, Kafka-интеграция, Redis, Quartz, зависимости, конфигурация, примеры кода.

---

## Структура

```
design/
├── README.md                           ← вы здесь
│
├── services/                           ← Design Documents (по ним строится реализация)
│   ├── service-api-gateway.md          — API Gateway (Spring Boot WebFlux)
│   ├── service-auth.md                 — auth-service (Spring Authorization Server)
│   ├── service-user.md                 — user-service (profiles, onboarding)
│   ├── service-interactions.md         — user-interactions-service (event collection)
│   ├── service-feed.md                 — feed-service (feed assembly, collections, Redis caching)
│   └── service-subscription.md         — subscription-service (YooKassa payments)
│
├── integration/                        ← Cross-service contracts & conventions
│   ├── api-conventions.md              — Unified error format, health checks, OpenAPI/Swagger
│   ├── cross-service-contracts.md      — Kafka + HTTP contracts verification matrix
│   └── rec-system-integration-contract.md  — rec-system API/Kafka contract
│
├── adr/                                ← Architecture Decision Records
│   ├── adr-001-auth-strategy.md        — Spring Auth Server vs Keycloak
│   ├── adr-002-api-gateway.md          — Spring WebFlux vs Kong/nginx
│   ├── adr-003-user-interactions-service.md — Dedicated service
│   ├── adr-004-data-consistency.md     — Outbox + Polling
│   ├── adr-005-feed-caching.md         — Redis LIST + offset + prefetch
│   └── adr-006-subscription-jwt.md     — Eventual consistency (15 min TTL)
│
├── diagrams.md                         — C4, sequence diagrams, risk map (Mermaid)
└── risk-assessment.md                  — 10 risks with mitigations
```

## Existing Systems (reference, в отдельной директории)

```
contracts/
├── rec-system-design.md                — rec-system: API, Kafka, scoring, NLP pipeline
├── rec-system-as-is.md                 — rec-system: текущая реализация с путями к коду
└── content-aggregator-design.md        — content-aggregator: read-only REST API, port 8086
```

---

## Quick Navigation

### Хочу понять архитектуру целиком
→ `diagrams.md` (C4 Container diagram) → `integration/cross-service-contracts.md` (все связи)

### Хочу реализовать конкретный сервис
→ `services/service-{name}.md` — полная спецификация

### Хочу понять почему выбрали X, а не Y
→ `adr/adr-NNN-{topic}.md`

### Хочу понять как feed-service собирает ленту
→ `services/service-feed.md` (section 4: Feed Assembly Flow)

### Хочу понять bookmarks / likes / dislikes
→ `services/service-feed.md` (sections 3.4-3.7: Collections API + section 5: DB Schema)

### Хочу понять формат ошибок, health checks, OpenAPI
→ `integration/api-conventions.md`

### Хочу понять интеграцию с YooKassa
→ `services/service-subscription.md` (sections 8-13)

### Хочу понять контракт rec-system
→ `integration/rec-system-integration-contract.md` + `contracts/rec-system-design.md`

---

## Services Summary

| Сервис | Порт | БД | Redis | Kafka IN | Kafka OUT |
|--------|------|----|-------|----------|-----------|
| API Gateway | 8080 | — | revocation, rate limits | — | — |
| auth-service | 8080 | auth-db | revocation write | subscription.changed | user.registered |
| user-service | 8080 | user-db | — | user.registered, subscription.changed | user.created |
| user-interactions | 8080 | interactions-db | — | — | user.interactions.batch |
| feed-service | 8080 | feed-db | feed LIST, content, related, negative, cold-start (read) | recommendations.updated | — |
| subscription-service | 8080 | subscription-db | — | — | subscription.changed |

### Redis Configuration (MVP)

Single node Redis. Обязательные настройки в `redis.conf`:

```
maxmemory 1gb
maxmemory-policy allkeys-lru
save 900 1                     # RDB snapshot: every 15 min if ≥1 key changed
save 300 10                    # every 5 min if ≥10 keys changed
```

- `allkeys-lru` — при достижении лимита Redis вытесняет наименее используемые ключи. Это безопасно: все наши ключи — кэши с TTL, потеря = cache miss → пересчёт
- RDB snapshot — при рестарте Redis восстанавливает данные из дампа. Без этого рестарт = cache stampede на rec-system
- 1GB достаточно для 100K пользователей (~520MB feed cache + content cache + revocation + rate limits)

| Existing System | Порт | БД | Kafka |
|----------------|------|----|-------|
| rec-system | 8000 | content_agg_db (shared) | IN: user.created, user.interactions.batch / OUT: recommendations.updated |
| content-aggregator | 8086 | content_agg_db (shared, read-only) | — |
| dedup-system | — | content_agg_db (shared) | IN: content.published |
| parser-service | — | content_agg_db (shared) | OUT: content.published |

### API Versioning (MVP)

Новые сервисы: `/api/feed`, `/api/auth/*`, `/api/users/*` — без версии в URL. content-aggregator (существующий): `/api/v1/content/*`.

Для MVP явная версия не нужна — breaking changes маловероятны, один фронтенд-клиент. При необходимости: добавить `/v2/` endpoint рядом с существующим, deprecation через `Sunset` header.

### Gateway Fail Policy

| Запрос | Redis down |
|--------|-----------|
| GET (чтение) | **Fail-open** — принять токен, пропустить rate limit |
| POST /checkout, POST /auth/password/change | **Fail-open** — допустимо: checkout защищён проверкой pending payment, password change требует current_password |

Для MVP fail-open на всех endpoints — окно атаки 15 мин (access token TTL), защитные меры в сервисах компенсируют.
