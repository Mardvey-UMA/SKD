package com.skd.subscription.integration

import com.skd.subscription.db.repository.payment.PaymentRepository
import com.skd.subscription.db.repository.payment.model.Payment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Tag("integration")
@Transactional
class PaymentRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Test
    fun `should save payment and find by externalId`() {
        val payment = Payment(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            amountKopecks = 29900,
            currency = "RUB",
            status = "pending",
            externalId = "yookassa-ext-123",
            externalStatus = null,
            confirmationUrl = "https://yookassa.ru/checkout/abc",
            idempotencyKey = UUID.randomUUID(),
            metadata = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        paymentRepository.save(payment)

        val found = paymentRepository.findByExternalId("yookassa-ext-123")
        assertThat(found).isNotNull
        assertThat(found!!.amountKopecks).isEqualTo(29900)
        assertThat(found.status).isEqualTo("pending")
    }

    @Test
    fun `should return null for non-existent externalId`() {
        val found = paymentRepository.findByExternalId("non-existent")

        assertThat(found).isNull()
    }

    @Test
    fun `should find pending payment for user and plan within 30 minutes`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()

        val recentPending = Payment(
            id = null,
            userId = userId,
            planId = "premium_monthly",
            amountKopecks = 29900,
            currency = "RUB",
            status = "pending",
            externalId = "yookassa-recent",
            externalStatus = null,
            confirmationUrl = "https://yookassa.ru/checkout/recent",
            idempotencyKey = UUID.randomUUID(),
            metadata = null,
            createdAt = now.minus(10, ChronoUnit.MINUTES),
            updatedAt = now
        )
        paymentRepository.save(recentPending)

        val thirtyMinAgo = now.minus(30, ChronoUnit.MINUTES)
        val found = paymentRepository.findPendingByUserAndPlan(userId, "premium_monthly", thirtyMinAgo)

        assertThat(found).isNotNull
        assertThat(found!!.confirmationUrl).isEqualTo("https://yookassa.ru/checkout/recent")
    }

    @Test
    fun `should not find pending payment older than 30 minutes`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()

        val oldPending = Payment(
            id = null,
            userId = userId,
            planId = "premium_monthly",
            amountKopecks = 29900,
            currency = "RUB",
            status = "pending",
            externalId = "yookassa-old",
            externalStatus = null,
            confirmationUrl = "https://yookassa.ru/checkout/old",
            idempotencyKey = UUID.randomUUID(),
            metadata = null,
            createdAt = now.minus(45, ChronoUnit.MINUTES),
            updatedAt = now
        )
        paymentRepository.save(oldPending)

        val thirtyMinAgo = now.minus(30, ChronoUnit.MINUTES)
        val found = paymentRepository.findPendingByUserAndPlan(userId, "premium_monthly", thirtyMinAgo)

        assertThat(found).isNull()
    }

    @Test
    fun `should not find succeeded payment when looking for pending`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()

        val succeededPayment = Payment(
            id = null,
            userId = userId,
            planId = "premium_monthly",
            amountKopecks = 29900,
            currency = "RUB",
            status = "succeeded",
            externalId = "yookassa-succeeded",
            externalStatus = "succeeded",
            confirmationUrl = null,
            idempotencyKey = UUID.randomUUID(),
            metadata = null,
            createdAt = now.minus(5, ChronoUnit.MINUTES),
            updatedAt = now
        )
        paymentRepository.save(succeededPayment)

        val thirtyMinAgo = now.minus(30, ChronoUnit.MINUTES)
        val found = paymentRepository.findPendingByUserAndPlan(userId, "premium_monthly", thirtyMinAgo)

        assertThat(found).isNull()
    }
}
