package com.skd.subscription.unit

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.db.repository.webhook.WebhookLogRepository
import com.skd.subscription.db.repository.webhook.model.WebhookLog
import com.skd.subscription.db.service.PaymentReconciler
import com.skd.subscription.db.service.WebhookService
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class WebhookServiceTest {

    private val webhookLogRepository = mockk<WebhookLogRepository>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val yookassaClient = mockk<YookassaClient>()
    private val paymentReconciler = mockk<PaymentReconciler>(relaxed = true)

    private val service = WebhookService(
        webhookLogRepository = webhookLogRepository,
        paymentRepository = paymentRepository,
        subscriptionRepository = subscriptionRepository,
        outboxRepository = outboxRepository,
        yookassaClient = yookassaClient,
        paymentReconciler = paymentReconciler
    )

    @Nested
    inner class `idempotency` {

        @Test
        fun `should skip processing when event already exists in webhook log`() {
            val eventId = "evt-123"
            every { webhookLogRepository.existsByExternalEventId(eventId) } returns true

            service.processWebhook(eventId = eventId, eventType = "payment.succeeded", paymentExternalId = "pay-1")

            verify(exactly = 0) { yookassaClient.getPayment(any()) }
            verify(exactly = 0) { paymentRepository.findByExternalId(any()) }
            verify(exactly = 0) {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            }
        }

        @Test
        fun `should log webhook event before processing`() {
            val eventId = "evt-new"
            val externalId = "pay-1"
            val webhookSlot = slot<WebhookLog>()
            every { webhookLogRepository.existsByExternalEventId(eventId) } returns false
            every { webhookLogRepository.save(capture(webhookSlot)) } answers { webhookSlot.captured }
            every { yookassaClient.getPayment(externalId) } returns createYookassaResponse("succeeded")
            every { paymentRepository.findByExternalId(externalId) } returns createPayment(externalId = externalId)
            every {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            } returns PaymentReconciler.Outcome.SUCCEEDED_APPLIED

            service.processWebhook(eventId = eventId, eventType = "payment.succeeded", paymentExternalId = externalId)

            assertThat(webhookSlot.captured.externalEventId).isEqualTo(eventId)
            assertThat(webhookSlot.captured.eventType).isEqualTo("payment.succeeded")
        }
    }

    @Nested
    inner class `payment succeeded delegation` {

        @Test
        fun `should fetch payment from YooKassa before delegating`() {
            val externalId = "pay-ext-1"
            setupReconcilerMocks(externalId, "succeeded")

            service.processWebhook(eventId = "evt-1", eventType = "payment.succeeded", paymentExternalId = externalId)

            verify(exactly = 1) { yookassaClient.getPayment(externalId) }
        }

        @Test
        fun `should load local payment by externalId before delegating`() {
            val externalId = "pay-ext-1"
            setupReconcilerMocks(externalId, "succeeded")

            service.processWebhook(eventId = "evt-1", eventType = "payment.succeeded", paymentExternalId = externalId)

            verify(exactly = 1) { paymentRepository.findByExternalId(externalId) }
        }

        @Test
        fun `should delegate terminal state application to PaymentReconciler with WEBHOOK trigger`() {
            val externalId = "pay-ext-1"
            val payment = createPayment(externalId = externalId)
            val response = createYookassaResponse("succeeded")
            every { webhookLogRepository.existsByExternalEventId(any()) } returns false
            every { webhookLogRepository.save(any()) } answers { firstArg() }
            every { yookassaClient.getPayment(externalId) } returns response
            every { paymentRepository.findByExternalId(externalId) } returns payment

            val paymentSlot = slot<Payment>()
            val responseSlot = slot<YookassaPaymentResponse>()
            val triggerSlot = slot<PaymentReconciler.Trigger>()
            every {
                paymentReconciler.applyTerminalStateIfNeeded(
                    capture(paymentSlot),
                    capture(responseSlot),
                    capture(triggerSlot)
                )
            } returns PaymentReconciler.Outcome.SUCCEEDED_APPLIED

            service.processWebhook(eventId = "evt-1", eventType = "payment.succeeded", paymentExternalId = externalId)

            assertThat(paymentSlot.captured.externalId).isEqualTo(externalId)
            assertThat(paymentSlot.captured.id).isEqualTo(payment.id)
            assertThat(responseSlot.captured.status).isEqualTo("succeeded")
            assertThat(triggerSlot.captured).isEqualTo(PaymentReconciler.Trigger.WEBHOOK)
        }

        @Test
        fun `should not touch subscription outbox or payment repositories directly on succeeded event`() {
            val externalId = "pay-ext-1"
            setupReconcilerMocks(externalId, "succeeded")

            service.processWebhook(eventId = "evt-1", eventType = "payment.succeeded", paymentExternalId = externalId)

            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { subscriptionRepository.save(any()) }
            verify(exactly = 0) { subscriptionRepository.findByUserId(any()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `should not delegate when local payment is not found`() {
            val externalId = "pay-missing"
            every { webhookLogRepository.existsByExternalEventId(any()) } returns false
            every { webhookLogRepository.save(any()) } answers { firstArg() }
            every { yookassaClient.getPayment(externalId) } returns createYookassaResponse("succeeded")
            every { paymentRepository.findByExternalId(externalId) } returns null

            service.processWebhook(eventId = "evt-1", eventType = "payment.succeeded", paymentExternalId = externalId)

            verify(exactly = 0) {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            }
        }
    }

    @Nested
    inner class `payment canceled delegation` {

        @Test
        fun `should delegate canceled event to PaymentReconciler with WEBHOOK trigger`() {
            val externalId = "pay-ext-cancel"
            val payment = createPayment(externalId = externalId)
            val response = createYookassaResponse("canceled")
            every { webhookLogRepository.existsByExternalEventId(any()) } returns false
            every { webhookLogRepository.save(any()) } answers { firstArg() }
            every { yookassaClient.getPayment(externalId) } returns response
            every { paymentRepository.findByExternalId(externalId) } returns payment

            val paymentSlot = slot<Payment>()
            val responseSlot = slot<YookassaPaymentResponse>()
            val triggerSlot = slot<PaymentReconciler.Trigger>()
            every {
                paymentReconciler.applyTerminalStateIfNeeded(
                    capture(paymentSlot),
                    capture(responseSlot),
                    capture(triggerSlot)
                )
            } returns PaymentReconciler.Outcome.CANCELED_APPLIED

            service.processWebhook(eventId = "evt-cancel", eventType = "payment.canceled", paymentExternalId = externalId)

            assertThat(paymentSlot.captured.externalId).isEqualTo(externalId)
            assertThat(responseSlot.captured.status).isEqualTo("canceled")
            assertThat(triggerSlot.captured).isEqualTo(PaymentReconciler.Trigger.WEBHOOK)
        }

        @Test
        fun `should not touch payment or subscription repositories directly on canceled event`() {
            val externalId = "pay-ext-cancel"
            setupReconcilerMocks(externalId, "canceled", outcome = PaymentReconciler.Outcome.CANCELED_APPLIED)

            service.processWebhook(eventId = "evt-cancel", eventType = "payment.canceled", paymentExternalId = externalId)

            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { subscriptionRepository.save(any()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `should not delegate canceled event when local payment is missing`() {
            val externalId = "pay-cancel-missing"
            every { webhookLogRepository.existsByExternalEventId(any()) } returns false
            every { webhookLogRepository.save(any()) } answers { firstArg() }
            every { yookassaClient.getPayment(externalId) } returns createYookassaResponse("canceled")
            every { paymentRepository.findByExternalId(externalId) } returns null

            service.processWebhook(eventId = "evt-cancel", eventType = "payment.canceled", paymentExternalId = externalId)

            verify(exactly = 0) {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            }
        }
    }

    @Nested
    inner class `payment waiting for capture` {

        @Test
        fun `should call capture on YooKassa when payment awaits capture`() {
            val externalId = "pay-ext-capture"
            setupWaitingForCaptureMocks(externalId)

            service.processWebhook(eventId = "evt-capture", eventType = "payment.waiting_for_capture", paymentExternalId = externalId)

            verify(exactly = 1) { yookassaClient.capturePayment(eq(externalId), any(), any()) }
        }

        @Test
        fun `should not delegate to PaymentReconciler on waiting_for_capture event`() {
            val externalId = "pay-ext-capture"
            setupWaitingForCaptureMocks(externalId)

            service.processWebhook(eventId = "evt-capture", eventType = "payment.waiting_for_capture", paymentExternalId = externalId)

            verify(exactly = 0) {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            }
        }
    }

    @Nested
    inner class `refund succeeded` {

        @Test
        fun `should deactivate subscription and emit outbox event on refund without delegating to reconciler`() {
            val externalId = "pay-ext-refund"
            val userId = UUID.randomUUID()
            val payment = createPayment(userId = userId, externalId = externalId)
            val existingSubscription = Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "active",
                startedAt = Instant.now().minusSeconds(86400),
                expiresAt = Instant.now().plusSeconds(86400),
                autoRenew = true
            )
            every { webhookLogRepository.existsByExternalEventId(any()) } returns false
            every { webhookLogRepository.save(any()) } answers { firstArg() }
            every { paymentRepository.findByExternalId(externalId) } returns payment
            every { subscriptionRepository.findByUserId(userId) } returns existingSubscription
            val subscriptionSlot = slot<Subscription>()
            every { subscriptionRepository.save(capture(subscriptionSlot)) } answers { subscriptionSlot.captured }
            val outboxSlot = slot<OutboxEntry>()
            every { outboxRepository.save(capture(outboxSlot)) } answers { outboxSlot.captured }

            service.processWebhook(eventId = "evt-refund", eventType = "refund.succeeded", paymentExternalId = externalId)

            assertThat(subscriptionSlot.captured.status).isEqualTo("cancelled")
            assertThat(subscriptionSlot.captured.autoRenew).isFalse()
            assertThat(outboxSlot.captured.eventType).isEqualTo("subscription.changed")
            verify(exactly = 0) {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            }
        }
    }

    @Nested
    inner class `unknown event type` {

        @Test
        fun `should not delegate to PaymentReconciler on unknown eventType`() {
            val externalId = "pay-ext-unknown"
            every { webhookLogRepository.existsByExternalEventId(any()) } returns false
            every { webhookLogRepository.save(any()) } answers { firstArg() }

            service.processWebhook(eventId = "evt-unknown", eventType = "payment.unknown", paymentExternalId = externalId)

            verify(exactly = 0) { yookassaClient.getPayment(any()) }
            verify(exactly = 0) {
                paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
            }
        }
    }

    // --- Helpers ---

    private fun createPayment(
        userId: UUID = UUID.randomUUID(),
        planId: String = "premium_monthly",
        externalId: String = "pay-ext-1"
    ) = Payment(
        id = UUID.randomUUID(),
        userId = userId,
        planId = planId,
        amountKopecks = 29900,
        status = "pending",
        externalId = externalId,
        idempotencyKey = UUID.randomUUID(),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createYookassaResponse(
        status: String,
        paymentMethodId: String? = null,
        paymentMethodType: String? = null,
        cardLast4: String? = null
    ) = YookassaPaymentResponse(
        id = "yookassa-pay-1",
        status = status,
        confirmationUrl = null,
        amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB"),
        paymentMethodId = paymentMethodId,
        paymentMethodType = paymentMethodType,
        cardLast4 = cardLast4
    )

    private fun setupReconcilerMocks(
        externalId: String,
        yookassaStatus: String,
        userId: UUID = UUID.randomUUID(),
        outcome: PaymentReconciler.Outcome = PaymentReconciler.Outcome.SUCCEEDED_APPLIED
    ) {
        val payment = createPayment(userId = userId, externalId = externalId)
        every { webhookLogRepository.existsByExternalEventId(any()) } returns false
        every { webhookLogRepository.save(any()) } answers { firstArg() }
        every { yookassaClient.getPayment(externalId) } returns createYookassaResponse(yookassaStatus)
        every { paymentRepository.findByExternalId(externalId) } returns payment
        every {
            paymentReconciler.applyTerminalStateIfNeeded(any(), any(), any())
        } returns outcome
    }

    private fun setupWaitingForCaptureMocks(externalId: String) {
        val payment = createPayment(externalId = externalId)
        every { webhookLogRepository.existsByExternalEventId(any()) } returns false
        every { webhookLogRepository.save(any()) } answers { firstArg() }
        every { yookassaClient.getPayment(externalId) } returns createYookassaResponse("waiting_for_capture")
        every { paymentRepository.findByExternalId(externalId) } returns payment
        every { yookassaClient.capturePayment(any(), any(), any()) } returns createYookassaResponse("succeeded")
    }
}
