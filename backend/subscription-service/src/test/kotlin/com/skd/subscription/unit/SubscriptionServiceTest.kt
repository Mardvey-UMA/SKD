package com.skd.subscription.unit

import com.skd.subscription.configuration.YookassaProperties
import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import com.skd.subscription.db.repository.plan.PlanRepository
import com.skd.subscription.db.repository.plan.model.Plan
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import com.skd.subscription.integration.rest.yookassa.YookassaClient
import com.skd.subscription.integration.rest.yookassa.model.YookassaCreatePaymentRequest
import com.skd.subscription.integration.rest.yookassa.model.YookassaPaymentResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import java.time.Instant
import java.util.UUID

@Tag("unit")
class SubscriptionServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val yookassaClient = mockk<YookassaClient>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val yookassaProperties = YookassaProperties()

    private val service = SubscriptionService(
        subscriptionRepository = subscriptionRepository,
        paymentRepository = paymentRepository,
        planRepository = planRepository,
        yookassaClient = yookassaClient,
        outboxRepository = outboxRepository,
        yookassaProperties = yookassaProperties
    )

    @Nested
    inner class `getStatus` {

        @Test
        fun `should return active subscription status for user with subscription`() {
            val userId = UUID.randomUUID()
            val now = Instant.now()
            val subscription = Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "active",
                startedAt = now.minusSeconds(86400),
                expiresAt = now.plusSeconds(86400 * 29L),
                autoRenew = true
            )
            every { subscriptionRepository.findByUserId(userId) } returns subscription

            val result = service.getStatus(userId)

            assertThat(result.tier).isEqualTo("premium")
            assertThat(result.status).isEqualTo("active")
            assertThat(result.planId).isEqualTo("premium_monthly")
            assertThat(result.expiresAt).isEqualTo(subscription.expiresAt)
            assertThat(result.autoRenew).isTrue()
            verify(exactly = 1) { subscriptionRepository.findByUserId(userId) }
        }

        @Test
        fun `should return free tier when user has no subscription`() {
            val userId = UUID.randomUUID()
            every { subscriptionRepository.findByUserId(userId) } returns null

            val result = service.getStatus(userId)

            assertThat(result.tier).isEqualTo("free")
            assertThat(result.status).isEqualTo("none")
            assertThat(result.planId).isNull()
            assertThat(result.expiresAt).isNull()
            assertThat(result.autoRenew).isFalse()
        }

        @Test
        fun `should return expired status when subscription is expired`() {
            val userId = UUID.randomUUID()
            val now = Instant.now()
            val subscription = Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "expired",
                startedAt = now.minusSeconds(86400 * 31L),
                expiresAt = now.minusSeconds(86400),
                autoRenew = false
            )
            every { subscriptionRepository.findByUserId(userId) } returns subscription

            val result = service.getStatus(userId)

            assertThat(result.status).isEqualTo("expired")
            assertThat(result.autoRenew).isFalse()
        }
    }

    @Nested
    inner class `checkout` {

        private val userId = UUID.randomUUID()
        private val planId = "premium_monthly"
        private val plan = Plan(
            id = planId,
            name = "Premium (month)",
            priceKopecks = 29900,
            durationDays = 30,
            active = true
        )

        @Test
        fun `should create payment via YooKassa and return checkout result`() {
            val externalPaymentId = "yookassa-payment-123"
            val confirmationUrl = "https://yookassa.ru/checkout/123"

            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            val paymentSlot = slot<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers {
                paymentSlot.captured.copy(id = UUID.randomUUID())
            }
            every { yookassaClient.createPayment(any()) } returns YookassaPaymentResponse(
                id = externalPaymentId,
                status = "pending",
                confirmationUrl = confirmationUrl,
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )
            every { paymentRepository.save(any()) } answers { firstArg() }

            val result = service.checkout(userId, planId)

            assertThat(result.confirmationUrl).isEqualTo(confirmationUrl)
            assertThat(result.paymentId).isNotNull()
            verify { yookassaClient.createPayment(any()) }
            verify(atLeast = 1) { paymentRepository.save(any()) }
        }

        @Test
        fun `should reuse existing pending payment when created within 30 minutes`() {
            val existingPaymentId = UUID.randomUUID()
            val existingConfirmationUrl = "https://yookassa.ru/checkout/existing"
            val existingPayment = Payment(
                id = existingPaymentId,
                userId = userId,
                planId = planId,
                amountKopecks = 29900,
                status = "pending",
                confirmationUrl = existingConfirmationUrl,
                idempotencyKey = UUID.randomUUID(),
                createdAt = Instant.now().minusSeconds(600),
                updatedAt = Instant.now().minusSeconds(600)
            )

            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns existingPayment

            val result = service.checkout(userId, planId)

            assertThat(result.confirmationUrl).isEqualTo(existingConfirmationUrl)
            assertThat(result.paymentId).isEqualTo(existingPaymentId.toString())
            verify(exactly = 0) { yookassaClient.createPayment(any()) }
        }

        @Test
        fun `should throw when plan not found or inactive`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns null

            assertThatThrownBy {
                service.checkout(userId, planId)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should pass configured returnUrl to YooKassa when property is set`() {
            val configuredReturnUrl = "https://my-app.com/return"
            val serviceWithReturnUrl = SubscriptionService(
                subscriptionRepository = subscriptionRepository,
                paymentRepository = paymentRepository,
                planRepository = planRepository,
                yookassaClient = yookassaClient,
                outboxRepository = outboxRepository,
                yookassaProperties = YookassaProperties(returnUrl = configuredReturnUrl)
            )

            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            val paymentSlot = slot<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers {
                paymentSlot.captured.copy(id = UUID.randomUUID())
            }
            val requestSlot = slot<YookassaCreatePaymentRequest>()
            every { yookassaClient.createPayment(capture(requestSlot)) } returns YookassaPaymentResponse(
                id = "yookassa-payment-abc",
                status = "pending",
                confirmationUrl = "https://yookassa.ru/checkout/abc",
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )
            every { paymentRepository.save(any()) } answers { firstArg() }

            serviceWithReturnUrl.checkout(userId, planId)

            assertThat(requestSlot.captured.confirmation).isNotNull
            assertThat(requestSlot.captured.confirmation!!.returnUrl).isEqualTo(configuredReturnUrl)
        }

        @Test
        fun `should fall back to legacy returnUrl when property is blank`() {
            val serviceWithBlankReturnUrl = SubscriptionService(
                subscriptionRepository = subscriptionRepository,
                paymentRepository = paymentRepository,
                planRepository = planRepository,
                yookassaClient = yookassaClient,
                outboxRepository = outboxRepository,
                yookassaProperties = YookassaProperties(returnUrl = "")
            )

            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            val paymentSlot = slot<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers {
                paymentSlot.captured.copy(id = UUID.randomUUID())
            }
            val requestSlot = slot<YookassaCreatePaymentRequest>()
            every { yookassaClient.createPayment(capture(requestSlot)) } returns YookassaPaymentResponse(
                id = "yookassa-payment-def",
                status = "pending",
                confirmationUrl = "https://yookassa.ru/checkout/def",
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )
            every { paymentRepository.save(any()) } answers { firstArg() }

            serviceWithBlankReturnUrl.checkout(userId, planId)

            assertThat(requestSlot.captured.confirmation).isNotNull
            assertThat(requestSlot.captured.confirmation!!.returnUrl).isEqualTo("https://example.com/return")
        }

        @Test
        fun `should persist pending payment before invoking external YooKassa call`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = UUID.randomUUID()) }
            every { yookassaClient.createPayment(any()) } returns YookassaPaymentResponse(
                id = "yookassa-payment-order",
                status = "pending",
                confirmationUrl = "https://yookassa.ru/checkout/order",
                amount = YookassaPaymentResponse.Amount(value = "299.00", currency = "RUB")
            )

            service.checkout(userId, planId)

            verifyOrder {
                paymentRepository.save(any())
                yookassaClient.createPayment(any())
            }
        }

        @Test
        fun `should keep pending payment row when YooKassa call fails`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            val pendingSlot = slot<Payment>()
            every { paymentRepository.save(capture(pendingSlot)) } answers {
                pendingSlot.captured.copy(id = UUID.randomUUID())
            }
            every { yookassaClient.createPayment(any()) } throws RuntimeException("YooKassa is down")

            assertThatThrownBy { service.checkout(userId, planId) }
                .isInstanceOf(RuntimeException::class.java)

            verify(exactly = 1) { paymentRepository.save(any()) }
            assertThat(pendingSlot.captured.status).isEqualTo("pending")
            assertThat(pendingSlot.captured.externalId).isNull()
            assertThat(pendingSlot.captured.confirmationUrl).isNull()
        }

        @Test
        fun `should translate ResourceAccessException from YooKassa into PaymentProviderUnavailableException`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            every { paymentRepository.save(any()) } answers {
                firstArg<Payment>().copy(id = UUID.randomUUID())
            }
            val networkCause = ResourceAccessException("I/O error: Connection timed out")
            every { yookassaClient.createPayment(any()) } throws networkCause

            assertThatThrownBy { service.checkout(userId, planId) }
                .isInstanceOf(PaymentProviderUnavailableException::class.java)
                .hasCauseReference(networkCause)
        }

        @Test
        fun `should translate HttpServerErrorException from YooKassa into PaymentProviderUnavailableException`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            every { paymentRepository.save(any()) } answers {
                firstArg<Payment>().copy(id = UUID.randomUUID())
            }
            val serverCause = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY,
                ByteArray(0),
                null
            )
            every { yookassaClient.createPayment(any()) } throws serverCause

            assertThatThrownBy { service.checkout(userId, planId) }
                .isInstanceOf(PaymentProviderUnavailableException::class.java)
                .hasCauseReference(serverCause)
        }

        @Test
        fun `should mark pending payment as failed when YooKassa throws ResourceAccessException`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            val paymentSlot = mutableListOf<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers {
                firstArg<Payment>().copy(id = paymentSlot.first().id ?: UUID.randomUUID())
            }
            every { yookassaClient.createPayment(any()) } throws
                ResourceAccessException("I/O error: Connection refused")

            assertThatThrownBy { service.checkout(userId, planId) }
                .isInstanceOf(PaymentProviderUnavailableException::class.java)

            assertThat(paymentSlot).hasSize(2)
            assertThat(paymentSlot[0].status).isEqualTo("pending")
            assertThat(paymentSlot[1].status).isEqualTo("failed")
        }

        @Test
        fun `should mark pending payment as failed when YooKassa throws HttpServerErrorException`() {
            every { planRepository.findByIdAndActiveTrue(planId) } returns plan
            every {
                paymentRepository.findPendingByUserAndPlan(userId, planId, any())
            } returns null
            val paymentSlot = mutableListOf<Payment>()
            every { paymentRepository.save(capture(paymentSlot)) } answers {
                firstArg<Payment>().copy(id = paymentSlot.first().id ?: UUID.randomUUID())
            }
            val serverCause = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY,
                "Bad Gateway",
                org.springframework.http.HttpHeaders.EMPTY,
                ByteArray(0),
                null
            )
            every { yookassaClient.createPayment(any()) } throws serverCause

            assertThatThrownBy { service.checkout(userId, planId) }
                .isInstanceOf(PaymentProviderUnavailableException::class.java)

            assertThat(paymentSlot).hasSize(2)
            assertThat(paymentSlot[0].status).isEqualTo("pending")
            assertThat(paymentSlot[1].status).isEqualTo("failed")
        }
    }

    @Nested
    inner class `cancel` {

        @Test
        fun `should disable auto renewal for active subscription`() {
            val userId = UUID.randomUUID()
            val subscription = Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "active",
                startedAt = Instant.now().minusSeconds(86400),
                expiresAt = Instant.now().plusSeconds(86400 * 29L),
                autoRenew = true
            )

            every { subscriptionRepository.findByUserId(userId) } returns subscription
            val savedSlot = slot<Subscription>()
            every { subscriptionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            service.cancel(userId)

            assertThat(savedSlot.captured.autoRenew).isFalse()
            assertThat(savedSlot.captured.status).isEqualTo("active")
            verify(exactly = 1) { subscriptionRepository.save(any()) }
        }

        @Test
        fun `should throw when no subscription exists for user`() {
            val userId = UUID.randomUUID()
            every { subscriptionRepository.findByUserId(userId) } returns null

            assertThatThrownBy {
                service.cancel(userId)
            }.isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `should throw when subscription is already expired`() {
            val userId = UUID.randomUUID()
            val subscription = Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                planId = "premium_monthly",
                tier = "premium",
                status = "expired",
                startedAt = Instant.now().minusSeconds(86400 * 31L),
                expiresAt = Instant.now().minusSeconds(86400),
                autoRenew = false
            )
            every { subscriptionRepository.findByUserId(userId) } returns subscription

            assertThatThrownBy {
                service.cancel(userId)
            }.isInstanceOf(IllegalStateException::class.java)
        }
    }
}
