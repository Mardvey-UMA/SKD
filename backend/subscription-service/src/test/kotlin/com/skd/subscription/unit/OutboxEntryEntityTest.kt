package com.skd.subscription.unit

import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("unit")
class OutboxEntryEntityTest {

    @Test
    fun `should create unpublished outbox entry`() {
        val now = Instant.now()

        val entry = OutboxEntry(
            id = null,
            aggregateType = "Subscription",
            aggregateId = "user-123",
            eventType = "subscription.changed",
            payload = """{"user_id":"user-123","tier":"premium","status":"active"}""",
            createdAt = now,
            publishedAt = null
        )

        assertThat(entry.aggregateType).isEqualTo("Subscription")
        assertThat(entry.eventType).isEqualTo("subscription.changed")
        assertThat(entry.publishedAt).isNull()
    }

    @Test
    fun `should mark entry as published`() {
        val now = Instant.now()

        val entry = OutboxEntry(
            id = 1L,
            aggregateType = "Subscription",
            aggregateId = "user-456",
            eventType = "subscription.changed",
            payload = """{"user_id":"user-456","tier":"free","status":"expired"}""",
            createdAt = now.minusSeconds(60),
            publishedAt = now
        )

        assertThat(entry.publishedAt).isNotNull()
        assertThat(entry.publishedAt).isEqualTo(now)
    }

    @Test
    fun `should allow null id for new entries`() {
        val entry = OutboxEntry(
            aggregateType = "Subscription",
            aggregateId = "user-789",
            eventType = "subscription.changed",
            payload = "{}",
            createdAt = Instant.now()
        )

        assertThat(entry.id).isNull()
    }
}
