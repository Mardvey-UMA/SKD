package com.skd.subscription.processor

import com.skd.subscription.configuration.ReconcileProperties
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.service.CheckoutResult
import com.skd.subscription.db.service.PaymentReconciler
import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.db.service.SubscriptionStatusResult
import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class SubscriptionProcessor(
    private val subscriptionService: SubscriptionService,
    private val meterRegistry: MeterRegistry,
    private val paymentRepository: PaymentRepository? = null,
    private val paymentReconciler: PaymentReconciler? = null,
    private val reconcileProperties: ReconcileProperties? = null
) {

    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionProcessor::class.java)
        private const val CHECKOUT_METRIC = "subscription.checkout.count"
        private const val RESULT_TAG = "result"
    }

    init {
        listOf("initiated", "completed", "cancelled", "failed").forEach { result ->
            meterRegistry.counter(CHECKOUT_METRIC, RESULT_TAG, result)
        }
    }

    fun getStatus(userId: UUID): SubscriptionStatusResult {
        log.debug("Processing getStatus for userId={}", userId)
        val status = subscriptionService.getStatus(userId)
        if (status.status != "none") {
            return status
        }

        val props = reconcileProperties ?: return status
        if (!props.lazy.enabled) {
            return status
        }

        val repo = paymentRepository ?: return status
        val reconciler = paymentReconciler ?: return status

        val after = Instant.now().minusSeconds(props.lazy.pendingWindowMinutes * 60L)
        val pendingPayment = repo.findMostRecentPendingByUser(userId, after)
        if (pendingPayment != null) {
            try {
                reconciler.fetchAndApply(pendingPayment, PaymentReconciler.Trigger.LAZY)
            } catch (e: Exception) {
                log.warn("Lazy reconcile failed for userId={}, paymentId={}: {}", userId, pendingPayment.id, e.message)
                return status
            }
            return subscriptionService.getStatus(userId)
        }

        return status
    }

    fun checkout(userId: UUID, plan: String): CheckoutResult {
        log.debug("Processing checkout for userId={}, plan={}", userId, plan)
        meterRegistry.counter(CHECKOUT_METRIC, RESULT_TAG, "initiated").increment()
        return try {
            val result = subscriptionService.checkout(userId, plan)
            meterRegistry.counter(CHECKOUT_METRIC, RESULT_TAG, "completed").increment()
            result
        } catch (e: PaymentProviderUnavailableException) {
            meterRegistry.counter(CHECKOUT_METRIC, RESULT_TAG, "failed").increment()
            throw e
        }
    }

    fun cancel(userId: UUID) {
        log.debug("Processing cancel for userId={}", userId)
        subscriptionService.cancel(userId)
        meterRegistry.counter(CHECKOUT_METRIC, RESULT_TAG, "cancelled").increment()
    }
}
