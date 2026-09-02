package com.skd.subscription.unit

import com.skd.subscription.db.service.CheckoutResult
import com.skd.subscription.db.service.SubscriptionService
import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import com.skd.subscription.processor.SubscriptionProcessor
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class SubscriptionProcessorMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val subscriptionService = mockk<SubscriptionService>()
    private lateinit var processor: SubscriptionProcessor

    @BeforeEach
    fun setUp() {
        processor = SubscriptionProcessor(subscriptionService, registry)
    }

    @Test
    fun `checkout increments initiated and completed counters on success`() {
        val userId = UUID.randomUUID()
        every { subscriptionService.checkout(userId, "premium_monthly") } returns
            CheckoutResult("pay-1", "https://yookassa.ru/pay/1")

        processor.checkout(userId, "premium_monthly")

        assertThat(registry.counter("subscription.checkout.count", "result", "initiated").count()).isEqualTo(1.0)
        assertThat(registry.counter("subscription.checkout.count", "result", "completed").count()).isEqualTo(1.0)
    }

    @Test
    fun `checkout increments initiated and failed counters on PaymentProviderUnavailableException`() {
        val userId = UUID.randomUUID()
        val cause = RuntimeException("provider down")
        every { subscriptionService.checkout(userId, "premium_monthly") } throws
            PaymentProviderUnavailableException(cause)

        assertThatThrownBy { processor.checkout(userId, "premium_monthly") }
            .isInstanceOf(PaymentProviderUnavailableException::class.java)

        assertThat(registry.counter("subscription.checkout.count", "result", "initiated").count()).isEqualTo(1.0)
        assertThat(registry.counter("subscription.checkout.count", "result", "failed").count()).isEqualTo(1.0)
        assertThat(registry.counter("subscription.checkout.count", "result", "completed").count()).isEqualTo(0.0)
    }

    @Test
    fun `cancel increments cancelled counter`() {
        val userId = UUID.randomUUID()
        every { subscriptionService.cancel(userId) } returns Unit

        processor.cancel(userId)

        assertThat(registry.counter("subscription.checkout.count", "result", "cancelled").count()).isEqualTo(1.0)
    }
}
