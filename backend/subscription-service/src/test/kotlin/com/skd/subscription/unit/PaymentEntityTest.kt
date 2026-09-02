package com.skd.subscription.unit

import com.skd.subscription.db.repository.payment.model.Payment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class PaymentEntityTest {

    @Test
    fun `should create payment with all required fields`() {
        val userId = UUID.randomUUID()
        val idempotencyKey = UUID.randomUUID()

        val payment = Payment(
            id = null,
            userId = userId,
            planId = "premium_monthly",
            amountKopecks = 29900,
            currency = "RUB",
            status = "pending",
            externalId = null,
            externalStatus = null,
            confirmationUrl = null,
            idempotencyKey = idempotencyKey,
            metadata = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertThat(payment.userId).isEqualTo(userId)
        assertThat(payment.amountKopecks).isEqualTo(29900)
        assertThat(payment.status).isEqualTo("pending")
        assertThat(payment.idempotencyKey).isEqualTo(idempotencyKey)
        assertThat(payment.externalId).isNull()
    }

    @Test
    fun `should default currency to RUB`() {
        val payment = Payment(
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            amountKopecks = 29900,
            idempotencyKey = UUID.randomUUID(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertThat(payment.currency).isEqualTo("RUB")
    }

    @Test
    fun `should store external payment data after YooKassa response`() {
        val payment = Payment(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            amountKopecks = 29900,
            currency = "RUB",
            status = "succeeded",
            externalId = "yookassa-payment-123",
            externalStatus = "succeeded",
            confirmationUrl = "https://yookassa.ru/checkout/abc",
            idempotencyKey = UUID.randomUUID(),
            metadata = """{"user_id":"abc","plan":"premium_monthly"}""",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        assertThat(payment.externalId).isEqualTo("yookassa-payment-123")
        assertThat(payment.confirmationUrl).isNotNull()
        assertThat(payment.metadata).contains("premium_monthly")
    }
}
