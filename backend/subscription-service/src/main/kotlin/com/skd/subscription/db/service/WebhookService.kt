package com.skd.subscription.db.service

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.webhook.WebhookLogRepository
import com.skd.subscription.db.repository.webhook.model.WebhookLog
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WebhookService(
    private val webhookLogRepository: WebhookLogRepository,
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val outboxRepository: OutboxRepository,
    private val yookassaClient: YookassaClient,
    private val paymentReconciler: PaymentReconciler
) {

    companion object {
        private val log = LoggerFactory.getLogger(WebhookService::class.java)
    }

    @Transactional
    fun processWebhook(eventId: String, eventType: String, paymentExternalId: String) {
        if (webhookLogRepository.existsByExternalEventId(eventId)) {
            log.info("Skipping duplicate webhook eventId={}", eventId)
            return
        }

        webhookLogRepository.save(
            WebhookLog(
                externalEventId = eventId,
                eventType = eventType,
                receivedAt = Instant.now(),
                processed = false,
                payload = """{"event_type":"$eventType","payment_id":"$paymentExternalId"}"""
            )
        )

        when (eventType) {
            "payment.succeeded" -> handlePaymentSucceeded(paymentExternalId)
            "payment.canceled" -> handlePaymentCanceled(paymentExternalId)
            "payment.waiting_for_capture" -> handlePaymentWaitingForCapture(paymentExternalId)
            "refund.succeeded" -> handleRefundSucceeded(paymentExternalId)
            else -> log.warn("Unknown webhook eventType={}", eventType)
        }
    }

    private fun handlePaymentSucceeded(externalId: String) {
        val yookassaResponse = yookassaClient.getPayment(externalId)
        val payment = paymentRepository.findByExternalId(externalId) ?: return
        paymentReconciler.applyTerminalStateIfNeeded(payment, yookassaResponse, PaymentReconciler.Trigger.WEBHOOK)
    }

    private fun handlePaymentCanceled(externalId: String) {
        val yookassaResponse = yookassaClient.getPayment(externalId)
        val payment = paymentRepository.findByExternalId(externalId) ?: return
        paymentReconciler.applyTerminalStateIfNeeded(payment, yookassaResponse, PaymentReconciler.Trigger.WEBHOOK)
    }

    private fun handlePaymentWaitingForCapture(externalId: String) {
        val yookassaResponse = yookassaClient.getPayment(externalId)
        val payment = paymentRepository.findByExternalId(externalId) ?: return

        val amount = yookassaResponse.amount.value
        val idempotencyKey = UUID.randomUUID().toString()
        yookassaClient.capturePayment(externalId, amount, idempotencyKey)
    }

    private fun handleRefundSucceeded(externalId: String) {
        val payment = paymentRepository.findByExternalId(externalId) ?: return
        val subscription = subscriptionRepository.findByUserId(payment.userId) ?: return
        val now = Instant.now()

        subscriptionRepository.save(
            subscription.copy(
                status = "cancelled",
                autoRenew = false
            )
        )

        outboxRepository.save(
            OutboxEntry(
                aggregateType = "subscription",
                aggregateId = payment.userId.toString(),
                eventType = "subscription.changed",
                payload = """{"user_id":"${payment.userId}","tier":"free","status":"cancelled","expires_at":"${subscription.expiresAt}","timestamp":"$now"}""",
                createdAt = now
            )
        )

        log.info("Refund succeeded: deactivated subscription for userId={}", payment.userId)
    }

}
