# ADR-002: API Gateway Technology Choice

**Status:** Accepted  
**Date:** 2026-04-03  
**Decision Makers:** Architecture Team

## Context

Need a single entry point for the frontend that:
- Validates JWT access tokens (via JWKS)
- Extracts claims and forwards as headers (X-User-Id, X-User-Roles, X-Subscription-Tier)
- Routes to internal services
- Rate limits per user
- **Constraint:** No Spring Cloud components

## Options Comparison

| Criteria | Kong OSS | Nginx + OpenResty | Ktor | Spring Boot WebFlux |
|----------|----------|-------------------|------|---------------------|
| **JWT/JWKS** | JWT plugin (no JWKS in OSS). OIDC = Enterprise-only | `lua-resty-jwt` + `lua-resty-openidc` for JWKS | Native `JwkProviderBuilder` with caching | **Spring Security OAuth2 RS — best JWKS/key-rotation** |
| **Header Enrichment** | **NOT built-in.** Needs 3rd party `kong-jwt2header` | Trivial in Lua | Trivial in Kotlin | Trivial in `WebFilter` |
| **Rate Limiting** | **Fixed-window only in OSS.** Sliding = Enterprise | `lua-resty-limit-traffic` — "highly experimental" | In-memory only; Bucket4j+Redis for distributed | **Bucket4j + Redis — proven** |
| **Proxy** | Native | Native | **No built-in.** ~200 lines for streaming proxy | WebClient-based, documented |
| **Resources** | ~256-512 MB + own DB | ~50-100 MB | ~128 MB runtime | ~256-512 MB |
| **OSS Status** | **Frozen at v3.9.1** since 2025 | Active | Active | Active |
| **Team Skills** | Lua | Lua (debugging painful) | Kotlin | Kotlin/Java |

### Critical Findings

**Kong OSS — 3 blockers:**
1. OSS frozen at v3.9.1 — no new free Docker images from v3.10+
2. JWT plugin doesn't forward custom claims as headers
3. No JWKS auto-rotation in OSS

**Nginx + OpenResty:** `lua-resty-limit-traffic` self-described as "highly experimental". Debugging Lua in nginx context — no interactive debugger.

**Ktor:** Lightest runtime but no built-in reverse proxy — correct streaming impl is ~200 lines and tricky.

## Recommendation: Spring Boot WebFlux

**This is NOT Spring Cloud Gateway. It's a standard Spring Boot application with WebFlux.**

**Rationale:**

1. **Best JWKS story** — automatic key fetch, caching, rotation via Spring Security OAuth2 RS
2. **Team knowledge** — Kotlin + Spring Boot daily. No Lua to learn. Testable with standard tools
3. **Distributed rate limiting** — Bucket4j + Redis, proven combination
4. **No external dependencies** — Single JAR, no database needed for gateway config
5. **Custom logic is just Kotlin** — Header enrichment, HMAC signing — all testable

**Counter-argument:** "Kong/Nginx are faster proxies." — At <10K RPS, the ~2-5ms overhead is negligible vs development velocity gain.

## Consequences

- Must implement reverse proxy in WebClient (~50-100 lines)
- Must implement HMAC header signing
- Higher memory than nginx (~256 MB vs ~50 MB)
- Full code ownership

## Key Implementation Details

### Gateway WebFilter Pipeline
```
Request → JwtAuthenticationFilter (Spring Security)
        → TokenRevocationFilter (check Redis)
        → RateLimitFilter (Bucket4j + Redis)
        → HeaderEnrichmentFilter (extract claims, sign with HMAC)
        → ProxyFilter (WebClient → internal service)
        → Response
```

### Route Configuration
```yaml
gateway:
  routes:
    - path: /api/auth/**
      target: http://auth-service:8080
      public: true
    - path: /api/users/**
      target: http://user-service:8080
    - path: /api/feed/**
      target: http://feed-service:8080
    - path: /api/interactions/**
      target: http://user-interactions-service:8080
    - path: /api/subscription/**
      target: http://subscription-service:8080
    - path: /api/config/**
      target: http://content-aggregation-system:8080
      roles: [SUBSCRIBER]
```

### HMAC Header Signing
```kotlin
val payload = "$userId|$roles|$subscriptionTier|$requestId"
val signature = Mac.getInstance("HmacSHA256")
    .apply { init(SecretKeySpec(sharedSecret, "HmacSHA256")) }
    .doFinal(payload.toByteArray())
    .let { Base64.getEncoder().encodeToString(it) }
// Header: X-Gateway-Signature: <signature>
// Internal services verify before trusting headers
```

---

**Краткое резюме (RU):** Рекомендуется Spring Boot WebFlux (НЕ Spring Cloud Gateway). Kong OSS заморожен на v3.9.1, не пробрасывает custom claims. Nginx требует Lua. Ktor нет reverse proxy. Spring WebFlux — единый стек, нативная JWKS-ротация, Bucket4j+Redis.
