package com.skd.subscription.integration

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Tag("integration")
@Transactional
class OutboxRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Test
    fun `should save and find unpublished entries`() {
        val entry = OutboxEntry(
            id = null,
            aggregateType = "Subscription",
            aggregateId = "user-123",
            eventType = "subscription.changed",
            payload = """{"user_id":"user-123","tier":"premium"}""",
            createdAt = Instant.now(),
            publishedAt = null
        )

        outboxRepository.save(entry)

        val unpublished = outboxRepository.findUnpublished(10)
        assertThat(unpublished).hasSize(1)
        assertThat(unpublished[0].aggregateId).isEqualTo("user-123")
        assertThat(unpublished[0].publishedAt).isNull()
    }

    @Test
    fun `should not return published entries in findUnpublished`() {
        val published = OutboxEntry(
            id = null,
            aggregateType = "Subscription",
            aggregateId = "user-published",
            eventType = "subscription.changed",
            payload = """{"user_id":"user-published"}""",
            createdAt = Instant.now().minus(1, ChronoUnit.HOURS),
            publishedAt = Instant.now()
        )
        outboxRepository.save(published)

        val unpublished = outboxRepository.findUnpublished(10)
        assertThat(unpublished).isEmpty()
    }

    @Test
    fun `should respect limit in findUnpublished`() {
        repeat(5) { i ->
            outboxRepository.save(
                OutboxEntry(
                    id = null,
                    aggregateType = "Subscription",
                    aggregateId = "user-$i",
                    eventType = "subscription.changed",
                    payload = """{"index":$i}""",
                    createdAt = Instant.now(),
                    publishedAt = null
                )
            )
        }

        val unpublished = outboxRepository.findUnpublished(3)
        assertThat(unpublished).hasSize(3)
    }

    @Test
    fun `should mark entry as published`() {
        val entry = OutboxEntry(
            id = null,
            aggregateType = "Subscription",
            aggregateId = "user-mark",
            eventType = "subscription.changed",
            payload = """{"user_id":"user-mark"}""",
            createdAt = Instant.now(),
            publishedAt = null
        )
        val saved = outboxRepository.save(entry)

        outboxRepository.markPublished(saved.id!!, Instant.now())

        val unpublished = outboxRepository.findUnpublished(10)
        assertThat(unpublished).isEmpty()
    }

    @Test
    fun `should delete published entries before given timestamp`() {
        val oldPublished = OutboxEntry(
            id = null,
            aggregateType = "Subscription",
            aggregateId = "user-old",
            eventType = "subscription.changed",
            payload = """{"old":true}""",
            createdAt = Instant.now().minus(5, ChronoUnit.DAYS),
            publishedAt = Instant.now().minus(4, ChronoUnit.DAYS)
        )
        outboxRepository.save(oldPublished)

        val recentPublished = OutboxEntry(
            id = null,
            aggregateType = "Subscription",
            aggregateId = "user-recent",
            eventType = "subscription.changed",
            payload = """{"recent":true}""",
            createdAt = Instant.now().minus(1, ChronoUnit.HOURS),
            publishedAt = Instant.now()
        )
        outboxRepository.save(recentPublished)

        val cutoff = Instant.now().minus(3, ChronoUnit.DAYS)
        val deletedCount = outboxRepository.deletePublishedBefore(cutoff)

        assertThat(deletedCount).isEqualTo(1)
    }
}
