package com.skd.subscription.db.service

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.paymentmethod.SavedPaymentMethodRepository
import com.skd.subscription.db.repository.paymentmethod.model.SavedPaymentMethod
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.time.Instant
import java.util.UUID

@Service
class PaymentReconciler(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val planRepository: PlanRepository,
    private val savedPaymentMethodRepository: SavedPaymentMethodRepository,
    private val outboxRepository: OutboxRepository,
    private val yookassaClient: YookassaClient,
    private val meterRegistry: MeterRegistry
) {

    companion object {
        private val log = LoggerFactory.getLogger(PaymentReconciler::class.java)
        private const val METRIC = "subscription.reconcile.count"
        private const val TRIGGER_TAG = "trigger"
        private const val RESULT_TAG = "result"
    }

    @set:Autowired
    @set:Lazy
    var self: PaymentReconciler = this

    enum class Trigger { WEBHOOK, LAZY, SCHEDULED }

    enum class Outcome { SUCCEEDED_APPLIED, CANCELED_APPLIED, STILL_PENDING, NOOP_ALREADY_APPLIED, ERROR }

    init {
        for (trigger in Trigger.values()) {
            for (result in listOf("succeeded", "canceled", "still_pending", "noop", "error")) {
                meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, result)
            }
        }
    }

    fun fetchAndApply(payment: Payment, trigger: Trigger): Outcome {
        val response = try {
            yookassaClient.getPayment(payment.externalId!!)
        } catch (e: ResourceAccessException) {
            log.warn("YooKassa unavailable for paymentId={}, trigger={}: {}", payment.id, trigger, e.message)
            meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "error").increment()
            return Outcome.ERROR
        } catch (e: HttpServerErrorException) {
            log.warn("YooKassa 5xx for paymentId={}, trigger={}: {}", payment.id, trigger, e.message)
            meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "error").increment()
            return Outcome.ERROR
        }

        return self.applyTerminalStateIfNeeded(payment, response, trigger)
    }

    @Transactional
    fun applyTerminalStateIfNeeded(payment: Payment, response: YookassaPaymentResponse, trigger: Trigger): Outcome {
        return when (response.status) {
            "succeeded" -> applySucceeded(payment, response, trigger)
            "canceled" -> applyCanceled(payment, trigger)
            else -> {
                meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "still_pending").increment()
                Outcome.STILL_PENDING
            }
        }
    }

    private fun applySucceeded(payment: Payment, response: YookassaPaymentResponse, trigger: Trigger): Outcome {
        val rowsUpdated = paymentRepository.markSucceededIfPending(payment.id!!, response.status)
        if (rowsUpdated == 0) {
            meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "noop").increment()
            return Outcome.NOOP_ALREADY_APPLIED
        }

        val plan = planRepository.findById(payment.planId)
        if (plan == null) {
            log.warn("Plan not found for planId={}, paymentId={}", payment.planId, payment.id)
            meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "noop").increment()
            return Outcome.NOOP_ALREADY_APPLIED
        }

        val now = Instant.now()
        val expiresAt = now.plusSeconds(plan.durationDays * 86400L)

        val existingSubscription = subscriptionRepository.findByUserId(payment.userId)
        try {
            if (existingSubscription != null) {
                subscriptionRepository.save(
                    existingSubscription.copy(
                        status = "active",
                        tier = "premium",
                        startedAt = now,
                        expiresAt = expiresAt,
                        autoRenew = true
                    )
                )
            } else {
                subscriptionRepository.save(
                    Subscription(
                        userId = payment.userId,
                        planId = payment.planId,
                        tier = "premium",
                        status = "active",
                        startedAt = now,
                        expiresAt = expiresAt,
                        autoRenew = true
                    )
                )
            }
        } catch (e: DuplicateKeyException) {
            log.warn("Concurrent subscription insert for userId={}, treating as NOOP", payment.userId)
            meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "noop").increment()
            return Outcome.NOOP_ALREADY_APPLIED
        }

        savePaymentMethodIfPresent(response, payment.userId)

        outboxRepository.save(
            OutboxEntry(
                aggregateType = "subscription",
                aggregateId = payment.userId.toString(),
                eventType = "subscription.changed",
                payload = """{"user_id":"${payment.userId}","tier":"premium","status":"active","expires_at":"$expiresAt","timestamp":"$now"}""",
                createdAt = now
            )
        )

        meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "succeeded").increment()
        return Outcome.SUCCEEDED_APPLIED
    }

    private fun applyCanceled(payment: Payment, trigger: Trigger): Outcome {
        paymentRepository.save(
            payment.copy(
                status = "failed",
                externalStatus = "canceled",
                updatedAt = Instant.now()
            )
        )
        meterRegistry.counter(METRIC, TRIGGER_TAG, trigger.name.lowercase(), RESULT_TAG, "canceled").increment()
        return Outcome.CANCELED_APPLIED
    }

    private fun savePaymentMethodIfPresent(response: YookassaPaymentResponse, userId: UUID) {
        val methodId = response.paymentMethodId ?: return
        val methodType = response.paymentMethodType ?: return

        val existing = savedPaymentMethodRepository.findActiveByUserId(userId)
        savedPaymentMethodRepository.save(
            SavedPaymentMethod(
                id = existing?.id,
                userId = userId,
                yookassaMethodId = methodId,
                type = methodType,
                cardLast4 = response.cardLast4,
                active = true
            )
        )
    }
}
