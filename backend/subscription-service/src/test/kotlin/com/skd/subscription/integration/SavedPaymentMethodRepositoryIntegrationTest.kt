package com.skd.subscription.integration

import com.skd.subscription.db.repository.paymentmethod.SavedPaymentMethodRepository
import com.skd.subscription.db.repository.paymentmethod.model.SavedPaymentMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Tag("integration")
@Transactional
class SavedPaymentMethodRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var savedPaymentMethodRepository: SavedPaymentMethodRepository

    @Test
    fun `should save and find active payment method by userId`() {
        val userId = UUID.randomUUID()
        val method = SavedPaymentMethod(
            id = null,
            userId = userId,
            yookassaMethodId = "pm_card_123",
            type = "bank_card",
            cardLast4 = "4242",
            active = true
        )

        savedPaymentMethodRepository.save(method)

        val found = savedPaymentMethodRepository.findActiveByUserId(userId)
        assertThat(found).isNotNull
        assertThat(found!!.yookassaMethodId).isEqualTo("pm_card_123")
        assertThat(found.cardLast4).isEqualTo("4242")
        assertThat(found.active).isTrue()
    }

    @Test
    fun `should not find inactive payment method`() {
        val userId = UUID.randomUUID()
        val method = SavedPaymentMethod(
            id = null,
            userId = userId,
            yookassaMethodId = "pm_old",
            type = "bank_card",
            cardLast4 = "1111",
            active = false
        )

        savedPaymentMethodRepository.save(method)

        val found = savedPaymentMethodRepository.findActiveByUserId(userId)
        assertThat(found).isNull()
    }

    @Test
    fun `should return null when no payment method exists for user`() {
        val found = savedPaymentMethodRepository.findActiveByUserId(UUID.randomUUID())

        assertThat(found).isNull()
    }
}
