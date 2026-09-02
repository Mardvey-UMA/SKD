package com.skd.subscription.job

import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.paymentmethod.SavedPaymentMethodRepository
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaCreatePaymentRequest
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@DisallowConcurrentExecution
class AutoRenewalJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val savedPaymentMethodRepository: SavedPaymentMethodRepository,
    private val paymentRepository: PaymentRepository,
    private val planRepository: PlanRepository,
    private val yookassaClient: YookassaClient
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(AutoRenewalJob::class.java)
        private const val RENEW_WINDOW_HOURS = 24L
    }

    override fun execute(context: JobExecutionContext) {
        val now = Instant.now()
        val deadline = now.plusSeconds(RENEW_WINDOW_HOURS * 3600)
        val expiring = subscriptionRepository.findExpiringWithAutoRenew(now, deadline)

        if (expiring.isEmpty()) {
            log.debug("No subscriptions expiring within {}h with auto_renew=true", RENEW_WINDOW_HOURS)
            return
        }

        log.info("Found {} subscriptions expiring within {}h to auto-renew", expiring.size, RENEW_WINDOW_HOURS)

        for (subscription in expiring) {
            val savedMethod = savedPaymentMethodRepository.findActiveByUserId(subscription.userId)
            if (savedMethod == null) {
                log.warn("No saved payment method for userId={}, skipping auto-renewal", subscription.userId)
                continue
            }

            val plan = planRepository.findById(subscription.planId)
            if (plan == null) {
                log.warn("Plan not found planId={} for userId={}, skipping auto-renewal", subscription.planId, subscription.userId)
                continue
            }

            val amountValue = "%.2f".format(plan.priceKopecks / 100.0)
            val idempotencyKey = UUID.randomUUID()

            val autopaymentRequest = YookassaCreatePaymentRequest(
                amount = YookassaCreatePaymentRequest.Amount(value = amountValue, currency = "RUB"),
                confirmation = null,
                description = "Auto-renewal for plan ${plan.id}",
                capture = true,
                idempotencyKey = idempotencyKey.toString(),
                paymentMethodId = savedMethod.yookassaMethodId
            )

            val response = yookassaClient.createAutopayment(autopaymentRequest)

            val payment = Payment(
                userId = subscription.userId,
                planId = subscription.planId,
                amountKopecks = plan.priceKopecks,
                status = "pending",
                externalId = response.id,
                idempotencyKey = idempotencyKey,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            paymentRepository.save(payment)

            log.info("Created autopayment externalId={} for userId={}", response.id, subscription.userId)
        }
    }
}
