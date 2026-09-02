# ADR-001: Authentication Strategy — Keycloak vs Spring Authorization Server

**Status:** Accepted  
**Date:** 2026-04-03  
**Decision Makers:** Architecture Team

## Context

The platform needs a full authentication/authorization solution:
- Registration with email + password + verification
- JWT issuance (access + refresh tokens)
- Password reset flow
- Token revocation
- Custom claims in JWT (subscription_tier, roles)
- Integration with Kafka (publish `user.registered` event)
- Constraints: Kotlin + Spring Boot stack, no Spring Cloud components

## Options

### Option A: Keycloak (Self-Hosted)

| Aspect | Details |
|--------|---------|
| **Features** | Full OAuth 2.0/OIDC, admin UI, social login, MFA, email verification built-in |
| **RAM** | 1,250 MB baseline per instance (10k cached sessions). Known regression: v23→v24 caused 3x RAM spike (bug #28671) |
| **CPU** | 1 vCPU per 15 password logins/sec; 1 vCPU per 120 refresh/sec |
| **Startup** | 30-60s with pre-built image, 60-120s without |
| **Custom Claims** | Simple attributes: UI mapper. Complex (external DB): requires Java SPI JAR in `/opt/keycloak/providers/` — **breaks on every major version update** |
| **Email Templates** | FreeMarker `.ftl` — customizable but must maintain both HTML and text versions. Variable gotcha: `${linkExpiration}` NOT `${expiration}` |
| **Kafka Integration** | Not native. Requires custom Event Listener SPI (another JAR that breaks on upgrades) |
| **Upgrade Path** | Cannot skip major versions (v24→v26 requires v25 stop). Infinispan cache incompatibility between versions |
| **CVE History** | Active CVE stream 2023-2025 (SAML, redirect, session hijack, DoS) |
| **TCO (3yr estimate)** | $199K-$211K including 3+ hrs/week specialized maintenance (independent study) |
| **Bootstrap vars** | Old `KEYCLOAK_USER`/`KEYCLOAK_PASSWORD` removed since v17. Use `KC_BOOTSTRAP_ADMIN_USERNAME`/`KC_BOOTSTRAP_ADMIN_PASSWORD` |

### Option B: Spring Authorization Server (Custom)

| Aspect | Details |
|--------|---------|
| **Features** | OAuth 2.1 + OIDC 1.0, built by Spring team. No admin UI, no social login out of box |
| **RAM** | ~256-512 MB (standard Spring Boot app) |
| **CPU** | Standard Spring Boot resource profile |
| **Startup** | 3-8s (standard Spring Boot) |
| **Custom Claims** | `OAuth2TokenCustomizer<JwtEncodingContext>` — 10 lines of Kotlin, no external JARs |
| **Email Verification** | Not built-in. Must implement: token generation → DB storage → email send. Estimate: 1-2 days |
| **Kafka Integration** | Native Spring Kafka, trivial |
| **Upgrade Path** | Standard Spring Boot upgrade — well-documented, incremental |
| **Token Storage** | `JdbcOAuth2AuthorizationService` for PostgreSQL. Refresh tokens persisted in DB |
| **Token Revocation** | Pattern: store `jti` in Redis SET with TTL = remaining token lifetime. Gateway checks on each request |

### Option C: Hybrid — Keycloak + Adapter Service

| Aspect | Details |
|--------|---------|
| **Complexity** | Two systems to maintain: Keycloak + adapter |
| **Custom Claims** | Still requires Keycloak SPI JAR for JWT enrichment |
| **Kafka** | Adapter polls Keycloak events or uses Event Listener SPI → Kafka |
| **Benefit** | Gets Keycloak UI and social login |

## Recommendation: Option B — Spring Authorization Server

**Rationale:**

1. **Stack consistency** — Same technology as all other services. Team doesn't need Keycloak-specific expertise (Java SPI development, FreeMarker templating, Infinispan tuning).

2. **Custom claims are trivial** — `subscription_tier` changes frequently (on payment). In Keycloak, this requires either SPI JAR (fragile) or User Attributes API call before each token issuance. In Spring Auth Server, it's a `@Bean` that reads from local DB.

3. **Kafka integration is native** — Keycloak requires custom Event Listener SPI. Spring Boot just uses `@KafkaTemplate`.

4. **Operational cost** — Keycloak is a full-time job. RAM regression bugs, version lock-in, CVE patching. Spring Boot is what the team already operates.

5. **Missing features are cheap to build** — Email verification (1-2 days), password reset (1 day). Social login can be added later with Spring Security OAuth2 Client if needed.

6. **Resource footprint** — ~256 MB vs ~1,250 MB.

**Counter-argument addressed:** "Keycloak is battle-tested." — True, but Spring Security + Nimbus JOSE is equally battle-tested for JWT issuance/validation. Keycloak shines for multi-realm, multi-tenant, social login federation, SAML — none of which are requirements here.

## Consequences

- Must implement email verification flow manually (token → DB → email)
- Must implement password reset flow manually
- No admin UI for user management (API endpoints suffice)
- Social login requires additional work if needed later
- Team owns all auth code — full control, full responsibility

## Key Implementation Details

### Token Strategy
```
access_token:  JWT, 15 min TTL, contains {sub, roles, subscription_tier}
refresh_token: opaque, 30 days TTL, stored in PostgreSQL
revocation:    Redis SET "revoked_tokens", SADD jti with TTL = remaining access_token lifetime
```

### Database Schema (auth-db)
```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email_verified  BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE email_verification_tokens (
    token           VARCHAR(255) PRIMARY KEY,
    user_id         UUID REFERENCES users(id),
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN DEFAULT FALSE
);

CREATE TABLE outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    published_at    TIMESTAMPTZ
);
```

---

**Краткое резюме (RU):** Рекомендуется Spring Authorization Server вместо Keycloak. Причины: единый стек (Kotlin/Spring), тривиальная кастомизация JWT claims, нативная интеграция с Kafka, в 5 раз меньше RAM, отсутствие операционного бремени Keycloak (SPI JAR-ы, миграции между версиями, CVE-патчинг). Недостающие фичи (email verification, password reset) реализуются за 2-3 дня.
