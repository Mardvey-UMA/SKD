# subscription-service — Service Design Document

**Status:** Accepted  
**Date:** 2026-04-04 (revised — YooKassa integration corrected)  
**Technology:** Kotlin, Spring Boot 3.x, Spring Data JDBC

---

## 1. Overview

Manages subscription lifecycle and integrates with YooKassa (formerly YooMoney) for payment processing.

**Responsibilities:**
- Create payment sessions via YooKassa API
- Process YooKassa webhooks (payment success/failure/refund)
- Track subscription status (active, expired, cancelled)
- Track payment history
- Manage saved payment methods for recurring (autopayments)
- Publish `subscription.changed` events via Transactional Outbox
- Detect and expire overdue subscriptions (Quartz)

**Not responsible for:**
- User profiles (→ user-service)
- JWT claims update (→ auth-service consumes `subscription.changed`)
- Access control (→ gateway checks JWT claims)

---

## 2. Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Runtime | Kotlin + Spring Boot 3.x | Application framework |
| Database | PostgreSQL 16 + Spring Data JDBC | Subscriptions, payments, outbox |
| Kafka | Spring Kafka | Publish subscription.changed via outbox |
| HTTP Client | RestClient (Spring 6.1+) | YooKassa API calls |
| Scheduler | Quartz (spring-boot-starter-quartz) | Expiration checks, cleanup |
| Migrations | Flyway | Database schema versioning |
| Build | Gradle (Kotlin DSL) | Build system |
| Container | Docker (eclipse-temurin:21-jre-alpine) | Deployment |

---

## 3. API Endpoints

Base path: `/api/subscription`  
All endpoints require authentication unless marked.

### 3.1 GET /api/subscription/status

Get current user's subscription status.

```
GET /api/subscription/status
X-User-Id: 550e8400-...
```

**Response 200 (active subscription):**
```json
{
  "user_id": "550e8400-...",
  "tier": "premium",
  "status": "active",
  "plan_id": "premium_monthly",
  "started_at": "2026-03-03T12:00:00Z",
  "expires_at": "2026-04-03T12:00:00Z",
  "auto_renew": true
}
```

**Response 200 (no subscription):**
```json
{
  "user_id": "550e8400-...",
  "tier": "free",
  "status": "none"
}
```

---

### 3.2 POST /api/subscription/checkout

Create a payment session for subscription purchase.

```
POST /api/subscription/checkout
X-User-Id: 550e8400-...
Content-Type: application/json

{
  "plan": "premium_monthly"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `plan` | enum | yes | `premium_monthly` (299 RUB) or `premium_yearly` (2990 RUB) |

**Response 200:**
```json
{
  "payment_id": "internal-payment-uuid",
  "confirmation_url": "https://yookassa.ru/checkout/...",
  "expires_at": "2026-04-03T12:30:00Z"
}
```

**Response 409:** Active subscription already exists  
**Response 422:** Invalid plan

**Internal flow:**
```
1. Check: active subscription exists? → 409
2. Check: pending payment for same user+plan in last 30 min?
   → YES: return existing confirmation_url (idempotent retry — no second charge)
   → NO: continue to step 3
3. Generate idempotency key (UUID)
4. INSERT payment (status=pending, idempotency_key) → single transaction
5. Call YooKassa API: POST /v3/payments (with Idempotence-Key = idempotency_key)
6. UPDATE payment SET external_id = yookassa_payment_id, confirmation_url = ...
7. Return confirmation_url to frontend
```

**Anti-double-charge protection (step 2):**
```kotlin
fun checkout(userId: UUID, planId: String): CheckoutResponse {
    // 1. Active subscription check
    subscriptionRepository.findActiveByUserId(userId)?.let {
        throw ConflictException("Active subscription already exists")
    }

    // 2. Reuse pending payment (prevents double charge on retry)
    val existingPending = paymentRepository.findRecentPending(
        userId = userId,
        planId = planId,
        since = Instant.now().minus(30, ChronoUnit.MINUTES)
    )
    if (existingPending != null && existingPending.confirmationUrl != null) {
        return CheckoutResponse(
            paymentId = existingPending.id,
            confirmationUrl = existingPending.confirmationUrl,
            expiresAt = existingPending.createdAt.plus(30, ChronoUnit.MINUTES)
        )
    }

    // 3. Create new payment
    val idempotencyKey = UUID.randomUUID()
    val payment = paymentRepository.save(Payment(
        userId = userId,
        planId = planId,
        amountKopecks = plan.priceKopecks,
        status = "pending",
        idempotencyKey = idempotencyKey
    ))

    // 4. Call YooKassa (idempotencyKey ensures YooKassa-side dedup too)
    val yookassaPayment = yookassaClient.createPayment(userId, plan, idempotencyKey)

    // 5. Save external ID and confirmation URL
    payment.externalId = yookassaPayment.id
    payment.confirmationUrl = yookassaPayment.confirmation.confirmationUrl
    paymentRepository.save(payment)

    return CheckoutResponse(
        paymentId = payment.id,
        confirmationUrl = yookassaPayment.confirmation.confirmationUrl,
        expiresAt = payment.createdAt.plus(30, ChronoUnit.MINUTES)
    )
}
```

**Why this prevents double charge:**
- Frontend retries `POST /checkout` → server finds existing pending payment → returns same `confirmation_url` → user redirected to same YooKassa payment page → only 1 charge
- Even if pending check somehow fails: YooKassa `Idempotence-Key` deduplicates on their side (same key = same payment)

---

### 3.3 POST /api/subscription/cancel

Cancel auto-renewal. Subscription remains active until `expires_at`.

```
POST /api/subscription/cancel
X-User-Id: 550e8400-...
```

**Response 200:**
```json
{
  "message": "Auto-renewal cancelled. Subscription active until 2026-04-03T12:00:00Z",
  "expires_at": "2026-04-03T12:00:00Z"
}
```

**Response 404:** No active subscription

**Side effects:**
1. UPDATE subscriptions SET auto_renew = false
2. No `subscription.changed` event (tier doesn't change until expiry)

---

### 3.4 POST /webhook/yookassa *(Public, no JWT, no gateway)*

YooKassa payment webhook. Called directly by YooKassa servers, NOT through API Gateway.

**Security:** Verified by source IP whitelist + object status confirmation (see section 8.3).

```
POST /webhook/yookassa
Content-Type: application/json

{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "22d6d597-000f-5000-9000-145f6df21d6f",
    "status": "succeeded",
    "paid": true,
    "amount": {
      "value": "299.00",
      "currency": "RUB"
    },
    "payment_method": {
      "type": "bank_card",
      "id": "22e18a2f-000f-5000-a000-1db6312b7767",
      "saved": true,
      "card": {
        "first6": "555555",
        "last4": "4444",
        "expiry_month": "07",
        "expiry_year": "2028"
      }
    },
    "metadata": {
      "user_id": "550e8400-e29b-41d4-a716-446655440000",
      "plan": "premium_monthly",
      "internal_payment_id": "internal-uuid"
    },
    "created_at": "2026-04-03T12:00:00Z"
  }
}
```

**Response:** Always 200. YooKassa ignores response body/headers.

**YooKassa events handled (matching webhook settings in dashboard):**

| Event | Description | Action |
|-------|-------------|--------|
| `payment.succeeded` | Успешный платёж | Activate subscription, save payment_method.id for recurring |
| `payment.waiting_for_capture` | Поступление платежа, который нужно подтвердить | Auto-capture immediately (POST /payments/{id}/capture). Used if two-step payment is configured on YooKassa side |
| `payment.canceled` | Отмена платежа или ошибка оплаты | Mark payment failed, no subscription change |
| `refund.succeeded` | Успешный возврат денег покупателю | Deactivate subscription, tier → free |

**Processing flow (payment.succeeded):**
```kotlin
@Transactional
fun handlePaymentSucceeded(notification: YookassaNotification) {
    val payment = notification.`object`
    val metadata = payment.metadata
    val userId = UUID.fromString(metadata["user_id"])
    val planId = metadata["plan"]
    val internalPaymentId = UUID.fromString(metadata["internal_payment_id"])

    // 1. Idempotency check
    val existingLog = webhookLogRepository.findByExternalEventId(payment.id)
    if (existingLog?.processed == true) return

    // 2. Verify payment status via YooKassa API (anti-spoofing)
    val verified = yookassaClient.getPayment(payment.id)
    if (verified.status != "succeeded") {
        log.warn("Payment ${payment.id} status mismatch: webhook=succeeded, api=${verified.status}")
        return
    }

    // 3. Update internal payment record
    val internalPayment = paymentRepository.findById(internalPaymentId)
    internalPayment.status = "succeeded"
    internalPayment.externalId = payment.id
    internalPayment.externalStatus = "succeeded"
    paymentRepository.save(internalPayment)

    // 4. Save payment method for recurring (if saved=true)
    if (payment.paymentMethod?.saved == true) {
        paymentMethodRepository.upsert(
            userId = userId,
            methodId = payment.paymentMethod.id,
            type = payment.paymentMethod.type,
            cardLast4 = payment.paymentMethod.card?.last4
        )
    }

    // 5. Activate subscription
    val plan = planRepository.findById(planId)
    subscriptionRepository.upsert(
        userId = userId,
        planId = planId,
        tier = "premium",
        status = "active",
        expiresAt = Instant.now().plus(plan.durationDays.toLong(), ChronoUnit.DAYS),
        autoRenew = true
    )

    // 6. Publish event via outbox
    outboxRepository.save(OutboxEvent(
        aggregateType = "Subscription",
        aggregateId = userId.toString(),
        eventType = "subscription.changed",
        payload = mapOf(
            "user_id" to userId,
            "tier" to "premium",
            "status" to "active",
            "plan_id" to planId,
            "expires_at" to Instant.now().plus(plan.durationDays.toLong(), ChronoUnit.DAYS),
            "timestamp" to Instant.now()
        )
    ))

    // 7. Log webhook as processed
    webhookLogRepository.upsert(payment.id, processed = true, payload = notification)
}
```

**Processing flow (payment.waiting_for_capture):**
```kotlin
fun handleWaitingForCapture(notification: YookassaNotification) {
    val payment = notification.`object`

    // Idempotency check
    val existingLog = webhookLogRepository.findByExternalEventId(payment.id)
    if (existingLog?.processed == true) return

    // Auto-capture immediately — we use one-step payments (capture=true),
    // but if YooKassa sends waiting_for_capture, we confirm it.
    yookassaClient.capturePayment(
        paymentId = payment.id,
        amount = payment.amount,    // capture full amount
        idempotencyKey = UUID.randomUUID()
    )
    // After capture, YooKassa will send payment.succeeded → handled above

    webhookLogRepository.upsert(payment.id, processed = true, payload = notification)
}
```

**Processing flow (payment.canceled):**
```kotlin
fun handlePaymentCanceled(notification: YookassaNotification) {
    val payment = notification.`object`
    val metadata = payment.metadata
    val internalPaymentId = UUID.fromString(metadata["internal_payment_id"])

    val existingLog = webhookLogRepository.findByExternalEventId(payment.id)
    if (existingLog?.processed == true) return

    // Mark internal payment as failed
    val internalPayment = paymentRepository.findById(internalPaymentId)
    internalPayment.status = "cancelled"
    internalPayment.externalStatus = payment.status
    paymentRepository.save(internalPayment)

    // No subscription change — payment didn't succeed
    webhookLogRepository.upsert(payment.id, processed = true, payload = notification)
}
```

**Processing flow (refund.succeeded):**
```kotlin
@Transactional
fun handleRefundSucceeded(notification: YookassaNotification) {
    val refund = notification.`object`
    val paymentId = refund.paymentId   // YooKassa refund object references original payment

    val existingLog = webhookLogRepository.findByExternalEventId(refund.id)
    if (existingLog?.processed == true) return

    // Find subscription by payment's user_id
    val originalPayment = paymentRepository.findByExternalId(paymentId) ?: return
    val userId = originalPayment.userId

    // Deactivate subscription
    val sub = subscriptionRepository.findByUserId(userId) ?: return
    sub.status = "cancelled"
    sub.tier = "free"
    sub.autoRenew = false
    subscriptionRepository.save(sub)

    // Publish event
    outboxRepository.save(OutboxEvent(
        aggregateType = "Subscription",
        aggregateId = userId.toString(),
        eventType = "subscription.changed",
        payload = mapOf(
            "user_id" to userId,
            "tier" to "free",
            "status" to "cancelled",
            "timestamp" to Instant.now()
        )
    ))

    webhookLogRepository.upsert(refund.id, processed = true, payload = notification)
}
```

**Webhook router (dispatches by event type):**
```kotlin
@RestController
class YookassaWebhookController(
    private val webhookService: YookassaWebhookService
) {
    @PostMapping("/webhook/yookassa")
    fun handleWebhook(@RequestBody notification: YookassaNotification): ResponseEntity<Void> {
        when (notification.event) {
            "payment.succeeded" -> webhookService.handlePaymentSucceeded(notification)
            "payment.waiting_for_capture" -> webhookService.handleWaitingForCapture(notification)
            "payment.canceled" -> webhookService.handlePaymentCanceled(notification)
            "refund.succeeded" -> webhookService.handleRefundSucceeded(notification)
            else -> log.warn("Unknown webhook event: ${notification.event}")
        }
        return ResponseEntity.ok().build()  // Always 200
    }
}
```

---

### 3.5 GET /health

```
GET /health
Response 200: {"status": "ok", "service": "subscription-service", "checks": {"database": "connected", "kafka": "connected"}}
Response 503: {"status": "degraded", ...}
```

---

## 4. Database Schema (subscription-db)

```sql
-- Subscription plans (reference data)
CREATE TABLE plans (
    id              VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    price_kopecks   INTEGER NOT NULL,
    duration_days   INTEGER NOT NULL,
    active          BOOLEAN DEFAULT TRUE
);

INSERT INTO plans VALUES 
    ('premium_monthly', 'Premium (месяц)', 29900, 30, TRUE),
    ('premium_yearly', 'Premium (год)', 299000, 365, TRUE);

-- Subscriptions (one per user, UNIQUE on user_id)
CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID UNIQUE NOT NULL,
    plan_id         VARCHAR(50) NOT NULL REFERENCES plans(id),
    tier            VARCHAR(20) NOT NULL DEFAULT 'premium',
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    auto_renew      BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_sub_user ON subscriptions (user_id);
CREATE INDEX idx_sub_expires ON subscriptions (expires_at) WHERE status = 'active';

-- Payment history
CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    plan_id         VARCHAR(50) NOT NULL REFERENCES plans(id),
    amount_kopecks  INTEGER NOT NULL,
    currency        VARCHAR(3) DEFAULT 'RUB',
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    external_id     VARCHAR(255),
    external_status VARCHAR(50),
    confirmation_url VARCHAR(500),               -- YooKassa redirect URL (for retry reuse)
    idempotency_key UUID UNIQUE NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_pay_user ON payments (user_id, created_at DESC);
CREATE INDEX idx_pay_pending ON payments (user_id, plan_id, created_at DESC) WHERE status = 'pending';
CREATE INDEX idx_pay_external ON payments (external_id);

-- Saved payment methods (for recurring/autopayments)
CREATE TABLE saved_payment_methods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID UNIQUE NOT NULL,
    yookassa_method_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50) NOT NULL,
    card_last4      VARCHAR(4),
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_spm_user ON saved_payment_methods (user_id) WHERE active = TRUE;

-- Webhook idempotency log
CREATE TABLE webhook_log (
    external_event_id VARCHAR(255) PRIMARY KEY,
    event_type        VARCHAR(50),
    received_at       TIMESTAMPTZ DEFAULT now(),
    processed         BOOLEAN DEFAULT FALSE,
    payload           JSONB
);

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
```

---

## 5. Kafka Integration

### 5.1 Produced Events (via Outbox)

#### `subscription.changed`

Published when subscription status changes.

```json
{
  "event_type": "subscription.changed",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "tier": "premium",
  "status": "active",
  "plan_id": "premium_monthly",
  "expires_at": "2026-05-03T12:00:00Z",
  "timestamp": "2026-04-03T12:00:00Z"
}
```

**Topic:** `subscription.changed`  
**Key:** `user_id`  
**Consumers:**
- auth-service (`auth-service-subscriptions`) → UPDATE users.subscription_tier → next JWT refresh includes new claims
- user-service (`user-service-subscriptions`) → UPDATE profiles.subscription_tier

**When published:**

| Trigger | tier | status |
|---------|------|--------|
| payment.succeeded webhook | premium | active |
| refund.succeeded webhook | free | cancelled |
| CheckExpiredSubscriptionsJob | free | expired |

### 5.2 Consumed Events

**None.**

---

## 6. Redis Usage

**None.** Webhook idempotency via `webhook_log` table. Low-frequency operations.

---

## 7. Scheduled Jobs (Quartz)

| Job | Schedule | Description |
|-----|----------|-------------|
| `CheckExpiredSubscriptionsJob` | Every 1 hour | Find expired subscriptions → expire + outbox event. **Grace period:** skip subscriptions with `auto_renew = true` that expired less than 3 hours ago (gives AutoRenewalJob time to retry) |
| `AutoRenewalJob` | Every 1 hour | Find `WHERE expires_at < now() + 1 day AND auto_renew = true` → create autopayment via saved method |
| `CleanupPublishedOutboxJob` | Daily 03:00 UTC | DELETE outbox WHERE published_at < now() - 3 days |
| `CleanupOldWebhookLogJob` | Daily 04:00 UTC | DELETE webhook_log WHERE received_at < now() - 30 days |

**Outbox poller:** `@Scheduled(fixedDelay = 5000)`

### AutoRenewalJob

```kotlin
@Component
class AutoRenewalJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val savedMethodRepository: SavedPaymentMethodRepository,
    private val yookassaClient: YookassaClient,
    private val paymentRepository: PaymentRepository
) : Job {
    override fun execute(context: JobExecutionContext) {
        // Find subscriptions expiring within 1 day with auto_renew=true
        val expiringSoon = subscriptionRepository.findExpiringWithAutoRenew(
            before = Instant.now().plus(1, ChronoUnit.DAYS)
        )

        expiringSoon.forEach { sub ->
            val savedMethod = savedMethodRepository.findActiveByUserId(sub.userId) ?: run {
                log.warn("No saved payment method for user ${sub.userId}, skipping auto-renewal")
                return@forEach
            }

            try {
                val plan = planRepository.findById(sub.planId)
                val idempotencyKey = UUID.randomUUID()

                // Create autopayment via YooKassa (no user confirmation needed)
                val payment = yookassaClient.createAutopayment(
                    amount = plan.priceKopecks,
                    currency = "RUB",
                    paymentMethodId = savedMethod.yookassaMethodId,
                    description = "Автопродление подписки ${plan.name}",
                    metadata = mapOf(
                        "user_id" to sub.userId.toString(),
                        "plan" to sub.planId,
                        "auto_renewal" to "true"
                    ),
                    idempotencyKey = idempotencyKey
                )

                // Save internal payment record
                paymentRepository.save(Payment(
                    userId = sub.userId,
                    planId = sub.planId,
                    amountKopecks = plan.priceKopecks,
                    status = "pending",
                    externalId = payment.id,
                    idempotencyKey = idempotencyKey
                ))

                log.info("Auto-renewal payment created for user ${sub.userId}: ${payment.id}")
            } catch (e: Exception) {
                log.error("Auto-renewal failed for user ${sub.userId}", e)
                // Will retry on next job run (1 hour)
            }
        }
    }
}
```

---

## 8. YooKassa Integration

### 8.1 Creating a Payment (first-time purchase)

```kotlin
fun createPayment(userId: UUID, plan: Plan, idempotencyKey: UUID): YookassaPaymentResponse {
    return restClient.post()
        .uri("${yookassaApiUrl}/payments")
        .headers { h ->
            h.setBasicAuth(shopId, secretKey)
            h.set("Idempotence-Key", idempotencyKey.toString())
            h.contentType = MediaType.APPLICATION_JSON
        }
        .body(mapOf(
            "amount" to mapOf(
                "value" to plan.priceRub(),     // "299.00"
                "currency" to "RUB"
            ),
            "confirmation" to mapOf(
                "type" to "redirect",
                "return_url" to "$frontendUrl/subscription/callback"
            ),
            "capture" to true,
            "description" to "Подписка ${plan.name}",
            "save_payment_method" to true,      // save for recurring
            "metadata" to mapOf(
                "user_id" to userId.toString(),
                "plan" to plan.id,
                "internal_payment_id" to idempotencyKey.toString()
            )
        ))
        .retrieve()
        .body(YookassaPaymentResponse::class.java)!!
}
```

**YooKassa response (relevant fields):**
```json
{
  "id": "22d6d597-000f-5000-9000-145f6df21d6f",
  "status": "pending",
  "confirmation": {
    "type": "redirect",
    "confirmation_url": "https://yookassa.ru/checkout/..."
  }
}
```

Frontend redirects user to `confirmation_url`. After payment, YooKassa redirects to `return_url`.

### 8.2 Creating an Autopayment (recurring, no user interaction)

```kotlin
fun createAutopayment(
    amount: Int, currency: String, paymentMethodId: String,
    description: String, metadata: Map<String, String>, idempotencyKey: UUID
): YookassaPaymentResponse {
    return restClient.post()
        .uri("${yookassaApiUrl}/payments")
        .headers { h ->
            h.setBasicAuth(shopId, secretKey)
            h.set("Idempotence-Key", idempotencyKey.toString())
            h.contentType = MediaType.APPLICATION_JSON
        }
        .body(mapOf(
            "amount" to mapOf(
                "value" to "%.2f".format(amount / 100.0),
                "currency" to currency
            ),
            "capture" to true,
            "payment_method_id" to paymentMethodId,   // saved method ID
            "description" to description,
            "metadata" to metadata
        ))
        .retrieve()
        .body(YookassaPaymentResponse::class.java)!!
}
```

**No `confirmation` block** — autopayments don't require user confirmation. YooKassa processes immediately and sends `payment.succeeded` webhook.

### 8.3 Webhook Verification

**YooKassa does NOT use HMAC signatures.** Verification is done by:

1. **IP whitelist** — accept webhooks only from YooKassa IPs
2. **Object status confirmation** — call GET /v3/payments/{id} to verify

```kotlin
@Component
class YookassaWebhookFilter(
    @Value("\${yookassa.webhook.allowed-cidrs}") private val allowedCidrsConfig: String
) : OncePerRequestFilter() {

    // Read from application.yml — no rebuild needed when YooKassa adds new IPs
    private val allowedCidrs: List<IpSubnet> = allowedCidrsConfig
        .split(",")
        .map { it.trim() }
        .map { IpSubnet(it) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        // Behind LB/reverse proxy, remoteAddr = LB IP, not YooKassa IP.
        // Use X-Forwarded-For (first IP in chain = original caller).
        // Requires: server.forward-headers-strategy=framework in application.yml
        val remoteIp = request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?: request.remoteAddr

        val allowed = allowedCidrs.any { it.contains(remoteIp) }

        if (!allowed) {
            log.warn("Webhook from non-YooKassa IP: $remoteIp")
            response.status = 403
            return
        }

        chain.doFilter(request, response)
    }
}
```

**After IP check, verify object status:**
```kotlin
fun verifyPaymentStatus(paymentId: String, expectedStatus: String): Boolean {
    val payment = restClient.get()
        .uri("${yookassaApiUrl}/payments/$paymentId")
        .headers { h -> h.setBasicAuth(shopId, secretKey) }
        .retrieve()
        .body(YookassaPaymentResponse::class.java)
    return payment?.status == expectedStatus
}
```

### 8.4 Webhook Retry Policy

YooKassa retries delivery for **24 hours** if non-200 response. Our handler always returns 200 (even on internal errors) and logs failures for manual investigation.

### 8.5 YooKassa API Reference

| Operation | Method | Endpoint | Auth |
|-----------|--------|----------|------|
| Create payment | POST | `https://api.yookassa.ru/v3/payments` | Basic Auth (shopId:secretKey) |
| Get payment | GET | `https://api.yookassa.ru/v3/payments/{id}` | Basic Auth |
| Capture payment | POST | `https://api.yookassa.ru/v3/payments/{id}/capture` | Basic Auth |
| Cancel payment | POST | `https://api.yookassa.ru/v3/payments/{id}/cancel` | Basic Auth |
| Create refund | POST | `https://api.yookassa.ru/v3/refunds` | Basic Auth |

**Authentication:** HTTP Basic Auth with `shopId:secretKey`.  
**Idempotency:** All POST requests require `Idempotence-Key` header (UUID).  
**Supported payment methods for saving:** bank cards, YooMoney wallet, Mir Pay, SberPay, T-Pay, SBP (FPS).

---

## 9. Webhook Endpoint Deployment Note

Webhook endpoint `POST /webhook/yookassa` must be accessible directly from the internet, NOT through the API Gateway (gateway adds JWT requirement). Options:

1. **Separate port/path in gateway** with `public: true` — already handled in gateway route config
2. **Direct exposure** via Docker — webhook port exposed separately

Current approach: Gateway route with `public: true` (same as `/api/auth/register`). IP filtering happens in subscription-service's `YookassaWebhookFilter`.

---

## 10. Service Dependencies

| Dependency | Protocol | Purpose | Failure Mode |
|-----------|----------|---------|--------------|
| PostgreSQL (subscription-db) | JDBC | All data | Service unavailable |
| Kafka | TCP | Publish subscription.changed via outbox | Outbox buffers events |
| YooKassa API | HTTPS | Payment creation, verification, autopayments | Checkout → 503. Webhooks retried by YooKassa (24h) |

---

## 11. Configuration (application.yml)

```yaml
server:
  port: 8080
  forward-headers-strategy: framework   # trust X-Forwarded-For from gateway/LB

spring:
  application:
    name: subscription-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:5432/subscription_db
    username: ${DB_USER:subscription}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 15
      connection-timeout: 5000
  flyway:
    enabled: true
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:kafka:9092}
    producer:
      acks: all
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: always

yookassa:
  shop-id: ${YOOKASSA_SHOP_ID}
  secret-key: ${YOOKASSA_SECRET_KEY}
  api-url: https://api.yookassa.ru/v3
  webhook:
    allowed-cidrs: 185.71.76.0/27,185.71.77.0/27,77.75.153.0/25,77.75.156.11/32,77.75.156.35/32,77.75.154.128/25

app:
  frontend-url: ${FRONTEND_URL:http://localhost:3000}

gateway:
  hmac-secret: ${GATEWAY_HMAC_SECRET}
```

### Test environment

```yaml
# .env.test (NOT committed to git)
YOOKASSA_SHOP_ID=<from YooKassa dashboard>
YOOKASSA_SECRET_KEY=<test secret key from dashboard>
```

**Test card:** `5555555555554444`, expiry `12/28`, CVV `000`

**Dashboard:** https://yookassa.ru/my — webhook URL must be configured here after deployment.

**Current webhook settings (from dashboard):**
- Events: `payment.succeeded`, `payment.waiting_for_capture`, `payment.canceled`, `refund.succeeded`
- URL: not yet configured (will be set to `https://<domain>/webhook/yookassa`)
```

---

## 12. Payment Lifecycle State Machine

```
                    ┌──────────┐
                    │ PENDING  │ ← POST /checkout (internal payment created)
                    └────┬─────┘
                         │ YooKassa webhook
                    ┌────▼─────┐
              ┌─────┤SUCCEEDED │ → activate subscription + save payment method
              │     └──────────┘
              │          │
              │     (user requests refund)
              │          │
              │     ┌────▼─────┐
              │     │ REFUNDED │ → deactivate subscription (tier=free)
              │     └──────────┘
              │
              │     ┌──────────┐
              └─────┤CANCELLED │ ← payment.canceled webhook (user cancelled)
                    └──────────┘   No subscription change
```

**Subscription lifecycle:**
```
NONE → (payment.succeeded) → ACTIVE → (expires_at reached) → EXPIRED
                                  ↑                              │
                                  │  (auto-renewal succeeds)     │
                                  └──────────────────────────────┘

ACTIVE → (refund) → CANCELLED (tier=free immediately)
ACTIVE → (cancel auto-renew) → ACTIVE until expires_at → EXPIRED
```

---

## 13. YooKassa Documentation & SDK References

**Official docs:**
- API portal: https://yookassa.ru/developers
- API reference: https://yookassa.ru/developers/api
- API reference (EN): https://yookassa.ru/developers/api?lang=en
- Webhooks: https://yookassa.ru/developers/using-api/webhooks
- HTTP codes: https://yookassa.ru/developers/using-api/response-handling/http-codes
- Testing: https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing?lang=ru
- Autopayments: https://yookassa.ru/developers/payment-acceptance/scenario-extensions/recurring-payments/pay-with-saved
- Save during payment: https://yookassa.ru/developers/payment-acceptance/scenario-extensions/recurring-payments/save-payment-method/save-during-payment
- Dashboard: https://yookassa.ru/my

**Java SDK (community, no official Java SDK):**
- dynomake/yookassa-java-sdk: https://github.com/dynomake/yookassa-java-sdk
- DeelTer/YooKassaSDK: https://github.com/DeelTer/YooKassaSDK

**Official SDKs (other languages, useful for contract reference):**
- PHP: https://github.com/yoomoney/yookassa-sdk-php
- Python: https://github.com/yoomoney/yookassa-sdk-python

**Decision: SDK vs custom RestClient**
For MVP, use custom RestClient (Spring 6.1+). YooKassa REST API is simple (Basic Auth + JSON). Community Java SDKs are not officially maintained and add dependency risk. Custom client gives full control. No WebFlux dependency needed.

---

**Краткое резюме (RU):** subscription-service — управление подписками + интеграция с YooKassa. 4 HTTP-эндпоинта (status, checkout, cancel, webhook). PostgreSQL: subscriptions, payments, saved_payment_methods, webhook_log, outbox. Kafka: публикует `subscription.changed`. 4 Quartz-задачи (проверка истекших, автопродление, cleanup×2). YooKassa: Basic Auth, IP whitelist для вебхуков (НЕ HMAC), верификация статуса через GET API, recurring через saved payment_method_id. Обрабатывает все 4 webhook-события из dashboard.
