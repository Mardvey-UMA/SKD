package com.skd.subscription.integration

import com.skd.subscription.db.repository.webhook.WebhookLogRepository
import com.skd.subscription.db.repository.webhook.model.WebhookLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Tag("integration")
@Transactional
class WebhookLogRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var webhookLogRepository: WebhookLogRepository

    @Test
    fun `should save webhook log and check existence by externalEventId`() {
        val log = WebhookLog(
            externalEventId = "evt_test_001",
            eventType = "payment.succeeded",
            receivedAt = Instant.now(),
            processed = false,
            payload = """{"type":"notification"}"""
        )

        webhookLogRepository.save(log)

        val exists = webhookLogRepository.existsByExternalEventId("evt_test_001")
        assertThat(exists).isTrue()
    }

    @Test
    fun `should return false for non-existent externalEventId`() {
        val exists = webhookLogRepository.existsByExternalEventId("non_existent_event")

        assertThat(exists).isFalse()
    }

    @Test
    fun `should handle duplicate save for idempotency check`() {
        val log = WebhookLog(
            externalEventId = "evt_duplicate",
            eventType = "payment.succeeded",
            receivedAt = Instant.now(),
            processed = true,
            payload = """{"data":"first"}"""
        )
        webhookLogRepository.save(log)

        val exists = webhookLogRepository.existsByExternalEventId("evt_duplicate")
        assertThat(exists).isTrue()
    }
}
