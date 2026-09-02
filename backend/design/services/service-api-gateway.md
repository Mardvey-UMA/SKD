# API Gateway — Service Design Document

**Status:** Accepted  
**Date:** 2026-04-03  
**Technology:** Kotlin, Spring Boot 3.x (WebFlux), Spring Security OAuth2 Resource Server

---

## 1. Overview

Single entry point for all frontend requests. Validates JWT tokens, enriches requests with user context headers, enforces rate limits, and proxies to internal services. This is NOT Spring Cloud Gateway — it is a standard Spring Boot WebFlux application.

**Responsibilities:**
- JWT validation (JWKS-based, keys cached from auth-service)
- Token revocation check (Redis)
- Rate limiting per user (Bucket4j + Redis)
- Header enrichment (X-User-Id, X-User-Roles, X-Subscription-Tier, X-Request-Id, X-Gateway-Signature)
- HMAC signing of forwarded headers
- Routing to internal services
- Public endpoint allowlisting (auth endpoints)

**Not responsible for:**
- Business logic
- Data persistence
- Kafka integration

---

## 2. Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Runtime | Kotlin + Spring Boot 3.x (WebFlux) | Reactive non-blocking gateway |
| Security | Spring Security OAuth2 Resource Server + Nimbus JOSE | JWT validation, JWKS fetching |
| Rate Limiting | Bucket4j + bucket4j-redis (Lettuce) | Distributed per-user rate limiting |
| HTTP Client | Spring WebClient | Streaming reverse proxy to internal services |
| Redis Client | Spring Data Redis (Lettuce) | Token revocation check, rate limit state |
| Logging | SLF4J + Logback | Structured JSON logging |
| Metrics | Micrometer + Prometheus | Request latency, error rates, circuit breaker state |
| Build | Gradle (Kotlin DSL) | Build and dependency management |
| Container | Docker (eclipse-temurin:21-jre-alpine) | Deployment |

---

## 3. Filter Pipeline

Requests pass through filters in this exact order:

```
1. RequestIdFilter          — Generate X-Request-Id (UUID) for tracing
2. JwtAuthenticationFilter  — Spring Security: validate JWT signature via cached JWKS
3. TokenRevocationFilter    — Check jti against Redis SET "revoked_tokens"
4. RateLimitFilter          — Bucket4j: sliding window per user_id
5. HeaderEnrichmentFilter   — Extract claims, build headers, compute HMAC signature
6. ProxyFilter              — WebClient: stream request to target service, stream response back
```

Public endpoints (e.g., `/api/auth/register`, `/api/auth/login`, `/webhook/yookassa`) skip filters 2-5.

**CorsWebFilter** runs before the pipeline (handles OPTIONS preflight and adds headers to all responses).

---

## 4. Route Configuration

```yaml
gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}  # shared with all internal services
  default-timeout-ms: 5000
  cors:
    allowed-origins:
      - ${FRONTEND_URL:http://localhost:3000}
      - http://localhost:5173           # Vite dev server
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: Authorization,Content-Type,X-Request-Id
    exposed-headers: X-Request-Id,Retry-After
    allow-credentials: true
    max-age: 3600                       # preflight cache 1 hour
  routes:
    - path-prefix: /api/auth
      target: http://auth-service:8080
      public: true                      # skip JWT validation
      strip-prefix: false
    - path-prefix: /api/users
      target: http://user-service:8080
      strip-prefix: false
    - path-prefix: /api/feed
      target: http://feed-service:8080
      strip-prefix: false
    - path-prefix: /api/interactions
      target: http://user-interactions-service:8080
      strip-prefix: false
    - path-prefix: /api/subscription
      target: http://subscription-service:8080
      strip-prefix: false
    - path-prefix: /webhook/yookassa
      target: http://subscription-service:8080
      public: true                      # no JWT — verified by IP whitelist in subscription-service
      strip-prefix: false
    - path-prefix: /api/config
      target: http://content-aggregation-system:8086
      strip-prefix: false
      required-roles: [SUBSCRIBER]      # subscription gate
  rate-limit:
    capacity: 100                       # requests per window
    window-seconds: 60                  # 1-minute window
    overdraft: 20                       # burst allowance
```

**CORS notes:**
- `allow-credentials: true` — needed for cookies/Authorization header
- Multiple `allowed-origins` for dev (localhost:3000, localhost:5173) and production
- `*` origin NOT used — credentials require explicit origins
- For manual API testing (curl, Postman): CORS doesn't apply — it's browser-only

---

## 5. Header Enrichment

Gateway extracts claims from validated JWT and forwards as headers:

| Header | Source | Example |
|--------|--------|---------|
| `X-User-Id` | JWT `sub` claim | `550e8400-e29b-41d4-a716-446655440000` |
| `X-User-Roles` | JWT `roles` claim | `USER,SUBSCRIBER` |
| `X-Subscription-Tier` | JWT `subscription_tier` claim | `premium` |
| `X-Request-Id` | Generated UUID per request | `f47ac10b-58cc-4372-a567-0e02b2c3d479` |
| `X-Gateway-Signature` | HMAC-SHA256 of above headers | `base64-encoded-signature` |

### HMAC Signature Computation

```kotlin
fun computeSignature(userId: String, roles: String, tier: String, requestId: String): String {
    val payload = "$userId|$roles|$tier|$requestId"
    return Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256")) }
        .doFinal(payload.toByteArray())
        .let { Base64.getEncoder().encodeToString(it) }
}
```

Internal services MUST verify this signature before trusting the headers.

---

## 6. Redis Usage

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `revoked:{jti}` | STRING "1" | Remaining access_token lifetime (max 900s) | Token revocation check |
| `rate:{user_id}` | STRING (Bucket4j state) | Auto-managed by Bucket4j | Per-user rate limit counters |

### Token Revocation Check

```kotlin
// Called on every authenticated request
fun isTokenRevoked(jti: String): Boolean {
    return redis.opsForValue().get("revoked:$jti") != null
}
```

**Why individual keys (not SET):** Each revoked token needs its own TTL = remaining access_token lifetime. Redis SET does not support per-member TTL. Individual STRING keys with EXPIRE solve this — they self-cleanup when the access token would have expired anyway.

---

## 7. Subscription Gate (for config-service)

For routes with `required-roles: [SUBSCRIBER]`:

```kotlin
// In HeaderEnrichmentFilter, after extracting claims
if (route.requiredRoles.isNotEmpty()) {
    val userTier = jwt.claims["subscription_tier"] as? String ?: "free"
    if (userTier == "free") {
        return ServerResponse.status(403)
            .bodyValue(ErrorResponse("subscription_required", "Active subscription required"))
    }
}
```

---

## 8. JWKS Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-service:8080/.well-known/jwks.json
          # Spring Security caches JWKS keys automatically
          # Keys refreshed on signature verification failure (key rotation support)
```

**Resilience:** If auth-service is down at gateway startup, JWKS fetch fails and gateway cannot validate tokens. Mitigation: retry with backoff on startup, health check reports unhealthy until JWKS loaded.

---

## 9. Database

**None.** Gateway is stateless. All state lives in Redis (rate limits, revocation set).

---

## 10. Scheduled Jobs (Quartz)

**None.** Gateway has no scheduled jobs. Rate limit cleanup is handled by Redis TTL. Revocation entries are self-expiring.

---

## 11. Service Dependencies

| Dependency | Protocol | Purpose | Failure Mode |
|-----------|----------|---------|--------------|
| auth-service | HTTP (JWKS) | Fetch public keys for JWT validation | Gateway unhealthy until JWKS loaded. Cached keys continue to work |
| Redis | TCP | Revocation check, rate limiting | Revocation: fail-open (accept token). Rate limit: fail-open (allow request) |
| Internal services | HTTP | Proxy destination | Return 502 Bad Gateway to client |

---

## 12. Configuration (application.yml)

```yaml
server:
  port: 8080
  netty:
    connection-timeout: 5000ms

spring:
  application:
    name: api-gateway
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://auth-service:8080/.well-known/jwks.json
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}

gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}
  rate-limit:
    capacity: 100
    window-seconds: 60
  routes:
    # ... as defined in section 4

logging:
  pattern:
    console: '{"ts":"%d","level":"%p","svc":"api-gateway","reqId":"%X{requestId}","msg":"%m"}%n'

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  endpoint:
    health:
      show-details: always
```

---

## 13. Error Responses

All gateway errors follow a consistent format:

```json
{
  "error": "error_code",
  "message": "Human-readable message",
  "request_id": "f47ac10b-..."
}
```

| HTTP Status | Error Code | When |
|------------|------------|------|
| 401 | `invalid_token` | JWT signature invalid, expired, or malformed |
| 401 | `token_revoked` | jti found in revocation set |
| 403 | `subscription_required` | Route requires SUBSCRIBER role, user is free |
| 429 | `rate_limit_exceeded` | Bucket4j limit exceeded. Includes `Retry-After` header |
| 502 | `service_unavailable` | Target service did not respond |
| 504 | `gateway_timeout` | Target service response timed out |

---

## 14. Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/api-gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xmx256m", "-jar", "app.jar"]
```

**Resource limits:** 256 MB heap, ~350 MB total container memory.

---

**Краткое резюме (RU):** API Gateway на Spring Boot WebFlux. Stateless — вся стейт-информация в Redis. Конвейер из 6 фильтров: JWT → revocation → rate limit → header enrichment + HMAC → proxy. Subscription gate для config-service через JWT claim. Нет БД, нет Kafka, нет Quartz.
