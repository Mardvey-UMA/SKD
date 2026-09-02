# ADR-006: Subscription Status Propagation in JWT Claims

**Status:** Accepted  
**Date:** 2026-04-03  
**Decision Makers:** Architecture Team

## Context

When a user's subscription changes (via YooMoney payment), the `subscription_tier` claim in their JWT must be updated. The gateway uses this claim to gate access to config-service (subscribers only).

## Options

### Option A: Force Token Re-Issue

| Aspect | Details |
|--------|---------|
| **Mechanism** | On `subscription.changed` → invalidate all user's tokens → push notification to frontend → frontend calls refresh |
| **Latency** | Near-instant (seconds) |
| **Complexity** | Requires WebSocket/SSE push channel to frontend |
| **Risk** | If push fails, user stuck with old token until natural expiry |

### Option B: Short-Lived Tokens + Standard Refresh (Recommended)

| Aspect | Details |
|--------|---------|
| **Mechanism** | access_token TTL = 15 min. On `subscription.changed` → auth-service updates user attributes. Next refresh → new claims |
| **Latency** | Up to 15 minutes (worst case) |
| **Complexity** | No additional infrastructure needed |
| **UX optimization** | Frontend calls refresh immediately after successful payment → typically 1-3 sec delay |

### Option C: Gateway Checks Redis Per Request

| Aspect | Details |
|--------|---------|
| **Mechanism** | Gateway reads `subscription:active:{userId}` from Redis on every request to config-service |
| **Latency** | Instant |
| **Complexity** | +1 Redis lookup per config-service request. Gateway now knows about subscription business logic |
| **Coupling** | Gateway becomes aware of domain concept "subscription" beyond what's in JWT |

## Recommendation: Option B — Eventual Consistency

**Rationale:**

1. **Simplicity** — No push infrastructure needed. Standard OAuth2 refresh flow handles propagation.

2. **Acceptable delay** — User pays → frontend immediately calls `POST /auth/refresh` → auth-service has already consumed `subscription.changed` (Kafka lag ~1-3 sec) → new JWT with updated claims. Real-world delay: 1-5 seconds, not 15 minutes.

3. **No gateway business logic** — Gateway only reads JWT claims. It doesn't know what "subscription" means beyond "this claim value allows/denies access to this route."

**UX optimization flow:**
```
1. User completes payment on YooMoney
2. YooMoney redirects back to SPA with success status
3. SPA calls POST /api/auth/refresh
4. auth-service issues new JWT with subscription_tier: premium
   (auth-service already consumed subscription.changed from Kafka)
5. SPA retries access to config-service with new token
```

**Worst case (Kafka lag):**
If auth-service hasn't consumed the event yet when frontend refreshes:
- Old JWT issued (subscription_tier: free)
- Frontend shows "activating subscription..." message
- Frontend retries refresh after 5 seconds
- By then, auth-service has consumed the event → correct JWT

## Consequences

- Maximum 15 min staleness window (theoretical; practical is 1-5 sec with immediate refresh)
- No WebSocket/SSE infrastructure needed
- Gateway route config is purely declarative (roles from JWT claims)
- auth-service must consume `subscription.changed` Kafka topic

## Implementation Details

### auth-service Kafka Consumer
```kotlin
@KafkaListener(topics = ["subscription.changed"])
fun handleSubscriptionChanged(event: SubscriptionChangedEvent) {
    userRepository.updateSubscriptionTier(
        userId = event.userId,
        tier = event.tier,
        expiresAt = event.expiresAt
    )
    log.info("Updated subscription for user ${event.userId} to ${event.tier}")
}
```

### Token Customizer
```kotlin
@Bean
fun jwtCustomizer(userRepository: UserRepository): OAuth2TokenCustomizer<JwtEncodingContext> {
    return OAuth2TokenCustomizer { context ->
        if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) {
            val userId = context.getPrincipal<Authentication>().name
            val user = userRepository.findById(UUID.fromString(userId))
            context.claims.claim("subscription_tier", user?.subscriptionTier ?: "free")
            context.claims.claim("roles", user?.roles ?: listOf("USER"))
        }
    }
}
```

### Gateway Route Config
```yaml
gateway:
  routes:
    - path: /api/config/**
      target: http://content-aggregation-system:8080
      roles: [SUBSCRIBER]  # checked against subscription_tier claim
```

---

**Краткое резюме (RU):** Eventual consistency через short-lived JWT (15 мин) + стандартный refresh flow. Фронтенд вызывает refresh сразу после оплаты — реальная задержка 1-5 сек. Gateway проверяет subscription_tier из JWT claim для config-service. Не нужен WebSocket/SSE, не нужен Redis lookup на каждый запрос.
