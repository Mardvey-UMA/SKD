package com.skd.subscription.db.service

import com.skd.subscription.configuration.YookassaProperties
import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaCreatePaymentRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.time.Instant
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val planRepository: PlanRepository,
    private val yookassaClient: YookassaClient,
    private val outboxRepository: OutboxRepository,
    private val yookassaProperties: YookassaProperties = YookassaProperties()
) {

    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionService::class.java)
        private const val PENDING_WINDOW_SECONDS = 30 * 60L
        private const val OUTBOX_BATCH_LIMIT = 100
    }

    fun getStatus(userId: UUID): SubscriptionStatusResult {
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: return SubscriptionStatusResult(
                tier = "free",
                status = "none",
                planId = null,
                expiresAt = null,
                autoRenew = false
            )
        return SubscriptionStatusResult(
            tier = subscription.tier,
            status = subscription.status,
            planId = subscription.planId,
            expiresAt = subscription.expiresAt,
            autoRenew = subscription.autoRenew
        )
    }

    fun checkout(userId: UUID, planId: String): CheckoutResult {
        val plan = planRepository.findByIdAndActiveTrue(planId)
            ?: throw IllegalArgumentException("Plan not found or inactive: $planId")

        val after = Instant.now().minusSeconds(PENDING_WINDOW_SECONDS)
        val existingPayment = paymentRepository.findPendingByUserAndPlan(userId, planId, after)
        if (existingPayment != null) {
            log.info("Reusing pending payment for userId={}, planId={}", userId, planId)
            return CheckoutResult(
                paymentId = existingPayment.id.toString(),
                confirmationUrl = existingPayment.confirmationUrl ?: ""
            )
        }

        val idempotencyKey = UUID.randomUUID()
        val now = Instant.now()

        val savedPayment = paymentRepository.save(
            Payment(
                userId = userId,
                planId = planId,
                amountKopecks = plan.priceKopecks,
                status = "pending",
                idempotencyKey = idempotencyKey,
                createdAt = now,
                updatedAt = now
            )
        )

        val amount = "%.2f".format(plan.priceKopecks / 100.0)
        val request = YookassaCreatePaymentRequest(
            amount = YookassaCreatePaymentRequest.Amount(value = amount),
            confirmation = YookassaCreatePaymentRequest.Confirmation(
                returnUrl = yookassaProperties.returnUrl.ifBlank { "https://example.com/return" }
            ),
            description = "Subscription: ${plan.name}",
            capture = true,
            idempotencyKey = idempotencyKey.toString()
        )

        val response = try {
            yookassaClient.createPayment(request)
        } catch (e: ResourceAccessException) {
            log.warn("YooKassa network error for userId={}, planId={}: {}", userId, planId, e.message)
            paymentRepository.save(savedPayment.copy(status = "failed", updatedAt = Instant.now()))
            throw PaymentProviderUnavailableException(e)
        } catch (e: HttpServerErrorException) {
            log.warn("YooKassa server error for userId={}, planId={}: {}", userId, planId, e.message)
            paymentRepository.save(savedPayment.copy(status = "failed", updatedAt = Instant.now()))
            throw PaymentProviderUnavailableException(e)
        }

        paymentRepository.save(
            savedPayment.copy(
                externalId = response.id,
                externalStatus = response.status,
                confirmationUrl = response.confirmationUrl,
                updatedAt = Instant.now()
            )
        )

        return CheckoutResult(
            paymentId = savedPayment.id.toString(),
            confirmationUrl = response.confirmationUrl ?: ""
        )
    }

    @Transactional
    fun cancel(userId: UUID) {
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw IllegalStateException("No subscription found for userId=$userId")

        if (subscription.status != "active") {
            throw IllegalStateException("Cannot cancel subscription with status=${subscription.status}")
        }

        subscriptionRepository.save(subscription.copy(autoRenew = false))
        log.info("Cancelled auto-renewal for userId={}", userId)
    }
}

data class SubscriptionStatusResult(
    val tier: String,
    val status: String,
    val planId: String?,
    val expiresAt: Instant?,
    val autoRenew: Boolean
)

data class CheckoutResult(
    val paymentId: String,
    val confirmationUrl: String
)
