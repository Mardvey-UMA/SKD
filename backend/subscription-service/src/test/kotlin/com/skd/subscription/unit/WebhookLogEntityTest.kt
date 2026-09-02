package com.skd.subscription.unit

import com.skd.subscription.db.repository.webhook.model.WebhookLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("unit")
class WebhookLogEntityTest {

    @Test
    fun `should create webhook log entry`() {
        val now = Instant.now()

        val log = WebhookLog(
            externalEventId = "evt_abc123",
            eventType = "payment.succeeded",
            receivedAt = now,
            processed = false,
            payload = """{"type":"notification","event":"payment.succeeded"}"""
        )

        assertThat(log.externalEventId).isEqualTo("evt_abc123")
        assertThat(log.eventType).isEqualTo("payment.succeeded")
        assertThat(log.processed).isFalse()
        assertThat(log.payload).contains("payment.succeeded")
    }

    @Test
    fun `should default processed to false`() {
        val log = WebhookLog(
            externalEventId = "evt_xyz",
            eventType = "payment.canceled",
            receivedAt = Instant.now(),
            payload = "{}"
        )

        assertThat(log.processed).isFalse()
    }
}
