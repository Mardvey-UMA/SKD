package com.skd.subscription.unit

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.paymentmethod.SavedPaymentMethodRepository
import com.skd.subscription.db.repository.paymentmethod.model.SavedPaymentMethod
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.plan.model.Plan
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.db.service.PaymentReconciler
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.time.Instant
import java.util.UUID

@Tag("unit")
class PaymentReconcilerTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val savedPaymentMethodRepository = mockk<SavedPaymentMethodRepository>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val yookassaClient = mockk<YookassaClient>()
    private val meterRegistry = SimpleMeterRegistry()

    private lateinit var reconciler: PaymentReconciler

    @BeforeEach
    fun setUp() {
        reconciler = PaymentReconciler(
            paymentRepository = paymentRepository,
            subscriptionRepository = subscriptionRepository,
            planRepository = planRepository,
            savedPaymentMethodRepository = savedPaymentMethodRepository,
            outboxRepository = outboxRepository,
            yookassaClient = yookassaClient,
            meterRegistry = meterRegistry
        )
    }

    @Nested
    inner class `fetchAndApply succeeded` {

        @Test
        fun `when YooKassa returns succeeded, calls apply and returns SUCCEEDED_APPLIED`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(payment.userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.SUCCEEDED_APPLIED)
            verify(exactly = 1) { yookassaClient.getPayment(payment.externalId!!) }
            verify(exactly = 1) { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") }
            verify(exactly = 1) { subscriptionRepository.save(any<Subscription>()) }
            verify(exactly = 1) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `counter is incremented with trigger=webhook and result=succeeded`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(payment.userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "webhook", "result", "succeeded")
                    .count()
            ).isEqualTo(1.0)
        }

        @Test
        fun `counter is incremented with trigger=lazy and result=succeeded`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(payment.userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.LAZY)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "lazy", "result", "succeeded")
                    .count()
            ).isEqualTo(1.0)
        }

        @Test
        fun `counter is incremented with trigger=scheduled and result=succeeded`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(payment.userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.SCHEDULED)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "scheduled", "result", "succeeded")
                    .count()
            ).isEqualTo(1.0)
        }

        @Test
        fun `creates new subscription when user has no existing one`() {
            val userId = UUID.randomUUID()
            val payment = createPendingPayment(userId = userId)
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(userId) } returns null
            val subscriptionSlot = slot<Subscription>()
            every { subscriptionRepository.save(capture(subscriptionSlot)) } answers { subscriptionSlot.captured }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(subscriptionSlot.captured.userId).isEqualTo(userId)
            assertThat(subscriptionSlot.captured.status).isEqualTo("active")
            assertThat(subscriptionSlot.captured.tier).isEqualTo("premium")
            assertThat(subscriptionSlot.captured.expiresAt).isAfter(Instant.now().plusSeconds(29 * 86400L))
        }

        @Test
        fun `upserts existing subscription when user already has one`() {
            val userId = UUID.randomUUID()
            val payment = createPendingPayment(userId = userId)
            val existingId = UUID.randomUUID()
            val existing = Subscription(
                id = existingId,
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "expired",
                startedAt = Instant.now().minusSeconds(86400 * 60L),
                expiresAt = Instant.now().minusSeconds(86400),
                autoRenew = false
            )
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(userId) } returns existing
            val subscriptionSlot = slot<Subscription>()
            every { subscriptionRepository.save(capture(subscriptionSlot)) } answers { subscriptionSlot.captured }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(subscriptionSlot.captured.id).isEqualTo(existingId)
            assertThat(subscriptionSlot.captured.status).isEqualTo("active")
            assertThat(subscriptionSlot.captured.expiresAt).isAfter(Instant.now())
        }

        @Test
        fun `saves outbox entry with exact payload shape`() {
            val userId = UUID.randomUUID()
            val payment = createPendingPayment(userId = userId)
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            val outboxSlot = slot<OutboxEntry>()
            every { outboxRepository.save(capture(outboxSlot)) } answers { outboxSlot.captured }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(outboxSlot.captured.aggregateType).isEqualTo("subscription")
            assertThat(outboxSlot.captured.aggregateId).isEqualTo(userId.toString())
            assertThat(outboxSlot.captured.eventType).isEqualTo("subscription.changed")
            assertThat(outboxSlot.captured.payload).contains("\"status\":\"active\"")
            assertThat(outboxSlot.captured.payload).contains("\"tier\":\"premium\"")
            assertThat(outboxSlot.captured.payload).contains("\"user_id\":\"$userId\"")
        }

        @Test
        fun `saves new payment method when YooKassa response has method fields and no existing method`() {
            val userId = UUID.randomUUID()
            val payment = createPendingPayment(userId = userId)
            val response = createYookassaResponse("succeeded").copy(
                paymentMethodId = "pm-xyz",
                paymentMethodType = "bank_card",
                cardLast4 = "4242"
            )
            every { yookassaClient.getPayment(payment.externalId!!) } returns response
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }
            every { savedPaymentMethodRepository.findActiveByUserId(userId) } returns null
            val methodSlot = slot<SavedPaymentMethod>()
            every { savedPaymentMethodRepository.save(capture(methodSlot)) } answers { methodSlot.captured }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(methodSlot.captured.id).isNull()
            assertThat(methodSlot.captured.userId).isEqualTo(userId)
            assertThat(methodSlot.captured.yookassaMethodId).isEqualTo("pm-xyz")
            assertThat(methodSlot.captured.type).isEqualTo("bank_card")
            assertThat(methodSlot.captured.cardLast4).isEqualTo("4242")
        }

        @Test
        fun `reuses existing payment method id when active method already exists`() {
            val userId = UUID.randomUUID()
            val payment = createPendingPayment(userId = userId)
            val existingMethodId = UUID.randomUUID()
            val existing = SavedPaymentMethod(
                id = existingMethodId,
                userId = userId,
                yookassaMethodId = "pm-old",
                type = "bank_card",
                cardLast4 = "0000",
                active = true
            )
            val response = createYookassaResponse("succeeded").copy(
                paymentMethodId = "pm-new",
                paymentMethodType = "bank_card",
                cardLast4 = "1234"
            )
            every { yookassaClient.getPayment(payment.externalId!!) } returns response
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }
            every { savedPaymentMethodRepository.findActiveByUserId(userId) } returns existing
            val methodSlot = slot<SavedPaymentMethod>()
            every { savedPaymentMethodRepository.save(capture(methodSlot)) } answers { methodSlot.captured }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(methodSlot.captured.id).isEqualTo(existingMethodId)
            assertThat(methodSlot.captured.yookassaMethodId).isEqualTo("pm-new")
        }

        @Test
        fun `does not save payment method when YooKassa response has no method fields`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(payment.userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } answers { firstArg() }
            every { outboxRepository.save(any<OutboxEntry>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            verify(exactly = 0) { savedPaymentMethodRepository.save(any<SavedPaymentMethod>()) }
        }
    }

    @Nested
    inner class `fetchAndApply canceled` {

        @Test
        fun `when YooKassa returns canceled, marks payment as failed and returns CANCELED_APPLIED`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("canceled")
            val paymentSlot = slot<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers { paymentSlot.captured }

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.CANCELED_APPLIED)
            assertThat(paymentSlot.captured.status).isEqualTo("failed")
            assertThat(paymentSlot.captured.externalStatus).isEqualTo("canceled")
        }

        @Test
        fun `canceled path does not write subscription or outbox`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("canceled")
            every { paymentRepository.save(any<Payment>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            verify(exactly = 0) { subscriptionRepository.save(any<Subscription>()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `counter is incremented with result=canceled`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("canceled")
            every { paymentRepository.save(any<Payment>()) } answers { firstArg() }

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "webhook", "result", "canceled")
                    .count()
            ).isEqualTo(1.0)
        }
    }

    @Nested
    inner class `fetchAndApply still pending` {

        @Test
        fun `when YooKassa returns pending, no DB writes and returns STILL_PENDING`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("pending")

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.LAZY)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.STILL_PENDING)
            verify(exactly = 0) { paymentRepository.markSucceededIfPending(any(), any()) }
            verify(exactly = 0) { paymentRepository.save(any<Payment>()) }
            verify(exactly = 0) { subscriptionRepository.save(any<Subscription>()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `when YooKassa returns waiting_for_capture, returns STILL_PENDING`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("waiting_for_capture")

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.SCHEDULED)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.STILL_PENDING)
        }

        @Test
        fun `counter is incremented with result=still_pending`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("pending")

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.SCHEDULED)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "scheduled", "result", "still_pending")
                    .count()
            ).isEqualTo(1.0)
        }
    }

    @Nested
    inner class `fetchAndApply idempotency` {

        @Test
        fun `second call returns NOOP_ALREADY_APPLIED when markSucceededIfPending returns 0`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 0

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.NOOP_ALREADY_APPLIED)
            verify(exactly = 0) { subscriptionRepository.save(any<Subscription>()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `NOOP path increments counter with result=noop`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 0

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "webhook", "result", "noop")
                    .count()
            ).isEqualTo(1.0)
        }

        @Test
        fun `DuplicateKeyException on subscription insert returns NOOP_ALREADY_APPLIED`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns createPlan()
            every { subscriptionRepository.findByUserId(payment.userId) } returns null
            every { subscriptionRepository.save(any<Subscription>()) } throws DuplicateKeyException("concurrent insert")

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.NOOP_ALREADY_APPLIED)
            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "webhook", "result", "noop")
                    .count()
            ).isEqualTo(1.0)
        }
    }

    @Nested
    inner class `fetchAndApply plan missing` {

        @Test
        fun `when plan is not found, returns NOOP_ALREADY_APPLIED and counter=noop`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } returns createYookassaResponse("succeeded")
            every { paymentRepository.markSucceededIfPending(payment.id!!, "succeeded") } returns 1
            every { planRepository.findById(payment.planId) } returns null

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.WEBHOOK)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.NOOP_ALREADY_APPLIED)
            verify(exactly = 0) { subscriptionRepository.save(any<Subscription>()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "webhook", "result", "noop")
                    .count()
            ).isEqualTo(1.0)
        }
    }

    @Nested
    inner class `fetchAndApply YooKassa errors` {

        @Test
        fun `ResourceAccessException returns ERROR and no writes`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } throws ResourceAccessException("network down")

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.LAZY)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.ERROR)
            verify(exactly = 0) { paymentRepository.markSucceededIfPending(any(), any()) }
            verify(exactly = 0) { paymentRepository.save(any<Payment>()) }
            verify(exactly = 0) { subscriptionRepository.save(any<Subscription>()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `HttpServerErrorException returns ERROR and no writes`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } throws
                HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)

            val outcome = reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.SCHEDULED)

            assertThat(outcome).isEqualTo(PaymentReconciler.Outcome.ERROR)
            verify(exactly = 0) { paymentRepository.save(any<Payment>()) }
            verify(exactly = 0) { subscriptionRepository.save(any<Subscription>()) }
        }

        @Test
        fun `ERROR path increments counter with result=error and correct trigger`() {
            val payment = createPendingPayment()
            every { yookassaClient.getPayment(payment.externalId!!) } throws ResourceAccessException("network down")

            reconciler.fetchAndApply(payment, PaymentReconciler.Trigger.LAZY)

            assertThat(
                meterRegistry.counter("subscription.reconcile.count", "trigger", "lazy", "result", "error")
                    .count()
            ).isEqualTo(1.0)
        }
    }

    // --- Helpers ---

    private fun createPendingPayment(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        planId: String = "premium_monthly",
        externalId: String = "yk-ext-1"
    ) = Payment(
        id = id,
        userId = userId,
        planId = planId,
        amountKopecks = 29900,
        status = "pending",
        externalId = externalId,
        idempotencyKey = UUID.randomUUID(),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun createPlan(
        id: String = "premium_monthly",
        durationDays: Int = 30
    ) = Plan(
        id = id,
        name = "Premium (month)",
        priceKopecks = 29900,
        durationDays = durationDays,
        active = true
    )

    private fun createYookassaResponse(
        status: String
    ) = YookassaPaymentResponse(
        id = "yookassa-pay-1",
        status = status,
        confirmationUrl = null,
        amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
    )
}
