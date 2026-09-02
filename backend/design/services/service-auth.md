# auth-service — Service Design Document

**Status:** Accepted  
**Date:** 2026-04-03  
**Technology:** Kotlin, Spring Boot 3.x, Spring Authorization Server, Spring Security

---

## 1. Overview

Handles all authentication flows: registration, login, token management, password operations. Owns user credentials. Publishes domain events via Transactional Outbox.

**Responsibilities:**
- User registration (email + password + email verification)
- Login → JWT issuance (access + refresh tokens)
- Token refresh, revocation, logout
- Password reset (email flow)
- Password change (authenticated)
- Maintain user's `subscription_tier` attribute (consumed from Kafka) for JWT claims
- JWKS endpoint for gateway and other services

**Not responsible for:**
- User profiles (→ user-service)
- Subscription lifecycle (→ subscription-service)
- Business logic beyond authentication

**Important: Custom endpoints, NOT standard OAuth2 flows.**  
This service uses Spring Authorization Server as a **library** (JWT signing, JWKS, token customizer), not as a full OAuth2 authorization server. Endpoints (`/auth/login`, `/auth/refresh`) are custom REST controllers — NOT the standard `/oauth2/token` endpoint. This means:
- No OAuth2 client registration needed (no `RegisteredClient`)
- No Authorization Code Flow / PKCE
- Refresh tokens work for SPA (public client) — because we control issuance, not OAuth 2.1 spec
- `OAuth2TokenCustomizer<JwtEncodingContext>` is used only for custom JWT claims

---

## 2. Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Runtime | Kotlin + Spring Boot 3.x | Application framework |
| Auth Framework | Spring Authorization Server (OAuth 2.1 + OIDC) | Token issuance, JWKS, OAuth flows |
| Security | Spring Security | Password encoding (BCrypt), authentication filters |
| Database | PostgreSQL 16 + Spring Data JDBC | User credentials, tokens, outbox |
| Kafka | Spring Kafka | Consume `subscription.changed`, produce via outbox |
| Redis | Spring Data Redis (Lettuce) | Token revocation set |
| Email | Spring Mail (JavaMailSender) | Verification and password reset emails |
| Scheduler | Quartz (spring-boot-starter-quartz) | Cleanup jobs |
| Migrations | Flyway | Database schema versioning |
| Build | Gradle (Kotlin DSL) | Build system |
| Container | Docker (eclipse-temurin:21-jre-alpine) | Deployment |

---

## 3. API Endpoints

Base path: `/auth`  
All endpoints are **public** (no JWT required) unless marked otherwise.

### 3.1 POST /auth/register

Register a new user. Sends email verification.

```
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecureP@ss123"
}
```

**Validation:**
- `email`: valid format, max 255 chars
- `password`: min 8 chars, at least 1 uppercase, 1 lowercase, 1 digit

**Response 201:**
```json
{
  "message": "Verification email sent. Check your inbox.",
  "email": "user@example.com"
}
```

**Response 409:** Email already registered  
**Response 422:** Validation failed

**Side effects:**
1. INSERT into `users` (email_verified = false) + INSERT into `outbox` (email_verification) — single transaction
2. Send verification email with token link: `{FRONTEND_URL}/verify?token={token}`

---

### 3.2 GET /auth/verify

Verify email address via token from email link.

```
GET /auth/verify?token=abc123def456
```

**Response 200:**
```json
{
  "message": "Email verified successfully",
  "email": "user@example.com"
}
```

**Response 400:** Token expired or invalid  
**Response 404:** Token not found

**Side effects:**
1. UPDATE `users` SET email_verified = true + INSERT into `outbox` (user.registered) — single transaction
2. Outbox poller publishes `user.registered` to Kafka

---

### 3.3 POST /auth/login

Authenticate user, return tokens.

```
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecureP@ss123"
}
```

**Response 200:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Response 401:** Invalid credentials  
**Response 403:** Email not verified

**access_token JWT claims:**
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iss": "auth-service",
  "aud": "content-platform",
  "exp": 1712150400,
  "iat": 1712149500,
  "jti": "unique-token-id",
  "roles": ["USER"],
  "subscription_tier": "free"
}
```

---

### 3.4 POST /auth/refresh

Refresh access token using refresh token.

```
POST /auth/refresh
Content-Type: application/json

{
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

**Response 200:** Same format as login response (new access + refresh tokens)  
**Response 401:** Invalid or expired refresh token

**Behavior:**
- Reads current `subscription_tier` from DB → includes in new access_token claims
- Old refresh token is invalidated (one-time use)
- New refresh token issued (rotation)

---

### 3.5 POST /auth/logout *(Authenticated)*

Revoke current access token.

```
POST /auth/logout
Authorization: Bearer eyJhbG...
```

**Response 200:**
```json
{"message": "Logged out successfully"}
```

**Side effects:**
1. `SETEX revoked:{jti} {remaining_seconds} "1"` in Redis (TTL = remaining access_token lifetime, max 900s)
2. Delete refresh token from DB

---

### 3.6 POST /auth/password/reset-request

Initiate password reset flow.

```
POST /auth/password/reset-request
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**Response 200:** Always returns 200 (prevent email enumeration)
```json
{"message": "If this email exists, a reset link has been sent"}
```

**Side effects:** If email exists → generate reset token, send email with link: `{FRONTEND_URL}/reset-password?token={token}`

---

### 3.7 POST /auth/password/reset

Complete password reset with token.

```
POST /auth/password/reset
Content-Type: application/json

{
  "token": "reset-token-from-email",
  "new_password": "NewSecureP@ss456"
}
```

**Response 200:**
```json
{"message": "Password reset successfully"}
```

**Response 400:** Token expired or invalid

**Side effects:** Revoke all existing refresh tokens for this user.

---

### 3.8 POST /auth/password/change *(Authenticated)*

Change password for authenticated user.

```
POST /auth/password/change
Authorization: Bearer eyJhbG...
Content-Type: application/json

{
  "current_password": "OldP@ss123",
  "new_password": "NewP@ss456"
}
```

**Response 200:**
```json
{"message": "Password changed successfully"}
```

**Response 401:** Current password incorrect

---

### 3.9 GET /.well-known/jwks.json

JWKS endpoint. Returns public keys for JWT verification.

```
GET /.well-known/jwks.json

Response 200:
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-id-1",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

Consumed by: API Gateway (Spring Security auto-fetches), any service that needs to verify tokens independently.

---

### 3.10 GET /health

```
GET /health
Response 200: {"status": "ok", "service": "auth-service", "checks": {"database": "connected", "redis": "connected", "kafka": "connected"}}
Response 503: {"status": "degraded", ...}
```

See `integration/api-conventions.md` for health check standard.

---

## 4. Database Schema (auth-db)

```sql
-- Users table
CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) UNIQUE NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    email_verified    BOOLEAN DEFAULT FALSE,
    subscription_tier VARCHAR(20) DEFAULT 'free',    -- updated via Kafka
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);

-- Email verification tokens
CREATE TABLE email_verification_tokens (
    token       VARCHAR(255) PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_verification_user ON email_verification_tokens (user_id);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    token       VARCHAR(255) PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,   -- SHA-256 hash of token value
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

-- Transactional Outbox
CREATE TABLE outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

-- Spring Authorization Server tables (auto-managed)
-- oauth2_registered_client
-- oauth2_authorization
-- oauth2_authorization_consent
```

---

## 5. Kafka Integration

### 5.1 Produced Events (via Outbox)

#### `user.registered`

Published after successful email verification.

```json
{
  "event_type": "user.registered",
  "aggregate_type": "User",
  "aggregate_id": "550e8400-...",
  "payload": {
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "timestamp": "2026-04-03T12:00:00Z"
  }
}
```

**Topic:** `user.registered`  
**Key:** `user_id`  
**Consumer:** user-service

### 5.2 Consumed Events

#### `subscription.changed`

Updates `subscription_tier` in `users` table so next JWT refresh includes correct claims.

```json
{
  "event_type": "subscription.changed",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "tier": "premium",
  "expires_at": "2026-05-03T12:00:00Z",
  "timestamp": "2026-04-03T12:00:00Z"
}
```

**Topic:** `subscription.changed`  
**Consumer group:** `auth-service-subscriptions`  
**Behavior:** `UPDATE users SET subscription_tier = :tier, updated_at = now() WHERE id = :userId`  
**Idempotent:** Yes — UPDATE is naturally idempotent.

---

## 6. Redis Usage

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `revoked:{jti}` | STRING "1" | Remaining access_token lifetime (max 900s) | Revoked access token tracking |

```kotlin
// On logout: revoke current access token
fun revokeToken(jti: String, expiresAt: Instant) {
    val remainingSeconds = Duration.between(Instant.now(), expiresAt).seconds
    if (remainingSeconds > 0) {
        redis.opsForValue().set("revoked:$jti", "1", Duration.ofSeconds(remainingSeconds))
    }
}
```

Gateway checks: `redis.get("revoked:$jti") != null` — see gateway design for details.

---

## 7. Scheduled Jobs (Quartz)

| Job | Schedule | Description |
|-----|----------|-------------|
| `CleanupExpiredVerificationTokensJob` | Daily 03:00 UTC | DELETE FROM email_verification_tokens WHERE expires_at < now() - INTERVAL '7 days' |
| `CleanupExpiredResetTokensJob` | Daily 03:00 UTC | DELETE FROM password_reset_tokens WHERE expires_at < now() - INTERVAL '1 day' |
| `CleanupExpiredRefreshTokensJob` | Daily 04:00 UTC | DELETE FROM refresh_tokens WHERE expires_at < now() |
| `CleanupPublishedOutboxJob` | Daily 05:00 UTC | DELETE FROM outbox WHERE published_at IS NOT NULL AND published_at < now() - INTERVAL '3 days' |

**Outbox poller** is NOT a Quartz job — it runs via `@Scheduled(fixedDelay = 5000)` for higher frequency:

```kotlin
@Scheduled(fixedDelay = 5000)
fun publishOutboxEvents() {
    val events = outboxRepository.findUnpublished(limit = 100)
    events.forEach { event ->
        try {
            kafkaTemplate.send(event.eventType, event.aggregateId, event.payload).get()
            outboxRepository.markPublished(event.id)
        } catch (e: Exception) {
            log.error("Outbox publish failed for event ${event.id}", e)
        }
    }
}
```

---

## 8. Token Strategy

| Token | Format | Lifetime | Storage |
|-------|--------|----------|---------|
| Access token | JWT (RS256) | 15 min | Not stored (stateless). jti in Redis if revoked |
| Refresh token | Opaque (SecureRandom, 64 chars) | 30 days | SHA-256 hash in `refresh_tokens` table |
| Email verification | Opaque (SecureRandom, 64 chars) | 24 hours | `email_verification_tokens` table |
| Password reset | Opaque (SecureRandom, 64 chars) | 1 hour | `password_reset_tokens` table |

### JWT Custom Claims

```kotlin
@Bean
fun jwtCustomizer(userRepository: UserRepository): OAuth2TokenCustomizer<JwtEncodingContext> {
    return OAuth2TokenCustomizer { context ->
        if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) {
            val userId = context.getPrincipal<Authentication>().name
            val user = userRepository.findById(UUID.fromString(userId))
            context.claims.claim("roles", user?.roles ?: listOf("USER"))
            context.claims.claim("subscription_tier", user?.subscriptionTier ?: "free")
        }
    }
}
```

---

## 9. Service Dependencies

| Dependency | Protocol | Purpose | Failure Mode |
|-----------|----------|---------|--------------|
| PostgreSQL (auth-db) | JDBC | User credentials, tokens, outbox | Service unavailable — all auth operations fail |
| Redis | TCP | Token revocation | auth-service can still issue/refresh tokens. Revocation checks in gateway fail-open |
| Kafka | TCP | Publish user.registered, consume subscription.changed | Outbox buffers events. subscription.changed consumption paused, retried |
| SMTP server | SMTP | Verification and reset emails | Registration succeeds but email delayed. Retry via outbox |

---

## 10. Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:5432/auth_db
    username: ${DB_USER:auth}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
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
      group-id: auth-service-subscriptions
      auto-offset-reset: earliest
      enable-auto-commit: false
    producer:
      acks: all
  mail:
    host: ${SMTP_HOST}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USER}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.starttls.enable: true
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always

auth:
  jwt:
    access-token-ttl: 900          # 15 minutes
    refresh-token-ttl: 2592000     # 30 days
    issuer: auth-service
    audience: content-platform
  verification-token-ttl: 86400    # 24 hours
  reset-token-ttl: 3600            # 1 hour
  frontend-url: ${FRONTEND_URL:http://localhost:3000}

gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}  # for HMAC verification on internal endpoints
```

---

## 11. HMAC Verification (Internal Filter)

auth-service verifies X-Gateway-Signature on endpoints that receive gateway-proxied requests (e.g., `/auth/logout`, `/auth/password/change`):

```kotlin
@Component
class GatewaySignatureFilter(
    @Value("\${gateway.hmac-secret}") private val hmacSecret: String
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val signature = request.getHeader("X-Gateway-Signature")
        val userId = request.getHeader("X-User-Id")

        if (signature == null || userId == null) {
            response.sendError(401, "Missing gateway headers")
            return
        }

        val roles = request.getHeader("X-User-Roles") ?: ""
        val tier = request.getHeader("X-Subscription-Tier") ?: ""
        val requestId = request.getHeader("X-Request-Id") ?: ""

        val expected = computeHmac("$userId|$roles|$tier|$requestId", hmacSecret)
        if (!MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())) {
            response.sendError(401, "Invalid gateway signature")
            return
        }
        chain.doFilter(request, response)
    }
}
```

---

**Краткое резюме (RU):** auth-service на Spring Authorization Server. 9 HTTP-эндпоинтов (register, verify, login, refresh, logout, password reset/change, JWKS). PostgreSQL для credentials и токенов. Redis для token revocation. Kafka: публикует `user.registered` через outbox, потребляет `subscription.changed` для обновления JWT claims. 4 Quartz-задачи для cleanup.
