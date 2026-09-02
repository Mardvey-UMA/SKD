package com.skd.subscription.unit

import com.skd.subscription.db.repository.paymentmethod.model.SavedPaymentMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class SavedPaymentMethodEntityTest {

    @Test
    fun `should create saved payment method with card details`() {
        val userId = UUID.randomUUID()

        val method = SavedPaymentMethod(
            id = null,
            userId = userId,
            yookassaMethodId = "pm_abc123",
            type = "bank_card",
            cardLast4 = "4242",
            active = true
        )

        assertThat(method.userId).isEqualTo(userId)
        assertThat(method.yookassaMethodId).isEqualTo("pm_abc123")
        assertThat(method.type).isEqualTo("bank_card")
        assertThat(method.cardLast4).isEqualTo("4242")
        assertThat(method.active).isTrue()
    }

    @Test
    fun `should allow null cardLast4 for non-card payment methods`() {
        val method = SavedPaymentMethod(
            userId = UUID.randomUUID(),
            yookassaMethodId = "pm_yoomoney_456",
            type = "yoo_money",
            cardLast4 = null,
            active = true
        )

        assertThat(method.cardLast4).isNull()
        assertThat(method.type).isEqualTo("yoo_money")
    }

    @Test
    fun `should default active to true`() {
        val method = SavedPaymentMethod(
            userId = UUID.randomUUID(),
            yookassaMethodId = "pm_test",
            type = "bank_card",
            cardLast4 = "1234"
        )

        assertThat(method.active).isTrue()
    }
}
