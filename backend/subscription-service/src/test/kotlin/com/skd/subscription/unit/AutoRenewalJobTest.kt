package com.skd.subscription.unit

import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.paymentmethod.SavedPaymentMethodRepository
import com.skd.subscription.db.repository.paymentmethod.model.SavedPaymentMethod
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.plan.model.Plan
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import com.skd.subscription.job.AutoRenewalJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import java.time.Instant
import java.util.UUID

@Tag("unit")
class AutoRenewalJobTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val savedPaymentMethodRepository = mockk<SavedPaymentMethodRepository>()
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val planRepository = mockk<PlanRepository>()
    private val yookassaClient = mockk<YookassaClient>()

    private val job = AutoRenewalJob(
        subscriptionRepository = subscriptionRepository,
        savedPaymentMethodRepository = savedPaymentMethodRepository,
        paymentRepository = paymentRepository,
        planRepository = planRepository,
        yookassaClient = yookassaClient
    )

    private val jobContext = mockk<JobExecutionContext>(relaxed = true)

    private val plan = Plan(
        id = "premium_monthly",
        name = "Premium (month)",
        priceKopecks = 29900,
        durationDays = 30,
        active = true
    )

    @Nested
    inner class `execute` {

        @Test
        fun `should find expiring subscriptions with auto_renew and create autopayment`() {
            val userId = UUID.randomUUID()
            val subscription = createExpiringSubscription(userId = userId)
            val savedMethod = createSavedPaymentMethod(userId = userId)

            every { subscriptionRepository.findExpiringWithAutoRenew(any(), any()) } returns listOf(subscription)
            every { savedPaymentMethodRepository.findActiveByUserId(userId) } returns savedMethod
            every { planRepository.findById(subscription.planId) } returns plan
            every { yookassaClient.createAutopayment(any()) } returns YookassaPaymentResponse(
                id = "yookassa-auto-123",
                status = "pending",
                confirmationUrl = null,
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )
            val paymentSlot = slot<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers { paymentSlot.captured.copy(id = UUID.randomUUID()) }

            job.execute(jobContext)

            verify(exactly = 1) { yookassaClient.createAutopayment(any()) }
            assertThat(paymentSlot.captured.userId).isEqualTo(userId)
            assertThat(paymentSlot.captured.planId).isEqualTo("premium_monthly")
            assertThat(paymentSlot.captured.amountKopecks).isEqualTo(29900)
        }

        @Test
        fun `should skip subscription when no saved payment method found`() {
            val userId = UUID.randomUUID()
            val subscription = createExpiringSubscription(userId = userId)

            every { subscriptionRepository.findExpiringWithAutoRenew(any(), any()) } returns listOf(subscription)
            every { savedPaymentMethodRepository.findActiveByUserId(userId) } returns null

            job.execute(jobContext)

            verify(exactly = 0) { yookassaClient.createAutopayment(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
        }

        @Test
        fun `should process multiple expiring subscriptions independently`() {
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()
            val sub1 = createExpiringSubscription(userId = userId1)
            val sub2 = createExpiringSubscription(userId = userId2)
            val method1 = createSavedPaymentMethod(userId = userId1)
            val method2 = createSavedPaymentMethod(userId = userId2)

            every { subscriptionRepository.findExpiringWithAutoRenew(any(), any()) } returns listOf(sub1, sub2)
            every { savedPaymentMethodRepository.findActiveByUserId(userId1) } returns method1
            every { savedPaymentMethodRepository.findActiveByUserId(userId2) } returns method2
            every { planRepository.findById(any()) } returns plan
            every { yookassaClient.createAutopayment(any()) } returns YookassaPaymentResponse(
                id = "yookassa-auto-456",
                status = "pending",
                confirmationUrl = null,
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )
            every { paymentRepository.save(any()) } answers { firstArg() }

            job.execute(jobContext)

            verify(exactly = 2) { yookassaClient.createAutopayment(any()) }
            verify(exactly = 2) { paymentRepository.save(any()) }
        }

        @Test
        fun `should do nothing when no subscriptions are expiring soon`() {
            every { subscriptionRepository.findExpiringWithAutoRenew(any(), any()) } returns emptyList()

            job.execute(jobContext)

            verify(exactly = 0) { savedPaymentMethodRepository.findActiveByUserId(any()) }
            verify(exactly = 0) { yookassaClient.createAutopayment(any()) }
        }

        @Test
        fun `should create payment record with correct fields for autopayment`() {
            val userId = UUID.randomUUID()
            val subscription = createExpiringSubscription(userId = userId)
            val savedMethod = createSavedPaymentMethod(userId = userId)

            every { subscriptionRepository.findExpiringWithAutoRenew(any(), any()) } returns listOf(subscription)
            every { savedPaymentMethodRepository.findActiveByUserId(userId) } returns savedMethod
            every { planRepository.findById(subscription.planId) } returns plan
            every { yookassaClient.createAutopayment(any()) } returns YookassaPaymentResponse(
                id = "yookassa-auto-789",
                status = "pending",
                confirmationUrl = null,
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )
            val paymentSlot = slot<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers { paymentSlot.captured.copy(id = UUID.randomUUID()) }

            job.execute(jobContext)

            val savedPayment = paymentSlot.captured
            assertThat(savedPayment.userId).isEqualTo(userId)
            assertThat(savedPayment.planId).isEqualTo("premium_monthly")
            assertThat(savedPayment.amountKopecks).isEqualTo(29900)
            assertThat(savedPayment.status).isEqualTo("pending")
            assertThat(savedPayment.externalId).isEqualTo("yookassa-auto-789")
        }

        @Test
        fun `should skip subscription when plan not found`() {
            val userId = UUID.randomUUID()
            val subscription = createExpiringSubscription(userId = userId)
            val savedMethod = createSavedPaymentMethod(userId = userId)

            every { subscriptionRepository.findExpiringWithAutoRenew(any(), any()) } returns listOf(subscription)
            every { savedPaymentMethodRepository.findActiveByUserId(userId) } returns savedMethod
            every { planRepository.findById(subscription.planId) } returns null

            job.execute(jobContext)

            verify(exactly = 0) { yookassaClient.createAutopayment(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
        }
    }

    private fun createExpiringSubscription(
        userId: UUID = UUID.randomUUID()
    ) = Subscription(
        id = UUID.randomUUID(),
        userId = userId,
        planId = "premium_monthly",
        tier = "premium",
        status = "active",
        startedAt = Instant.now().minusSeconds(86400 * 29L),
        expiresAt = Instant.now().plusSeconds(12 * 3600), // expires in 12 hours
        autoRenew = true
    )

    private fun createSavedPaymentMethod(
        userId: UUID = UUID.randomUUID()
    ) = SavedPaymentMethod(
        id = UUID.randomUUID(),
        userId = userId,
        yookassaMethodId = "yookassa-method-${UUID.randomUUID()}",
        type = "bank_card",
        cardLast4 = "4242",
        active = true
    )
}
