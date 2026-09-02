package com.skd.subscription.unit

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.service.OutboxPollerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Tag("unit")
class OutboxPollerServiceTest {

    private val outboxRepository = mockk<OutboxRepository>()
    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()

    private val service = OutboxPollerService(
        outboxRepository = outboxRepository,
        kafkaTemplate = kafkaTemplate
    )

    @Nested
    inner class `pollAndPublish` {

        @Test
        fun `should fetch unpublished entries and send to Kafka`() {
            val entry = OutboxEntry(
                id = 1L,
                aggregateType = "subscription",
                aggregateId = "user-123",
                eventType = "subscription.changed",
                payload = """{"user_id":"user-123","tier":"premium","status":"active"}""",
                createdAt = Instant.now()
            )
            every { outboxRepository.findUnpublished(any()) } returns listOf(entry)
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            every { outboxRepository.markPublished(any(), any()) } returns Unit

            service.pollAndPublish()

            verify(exactly = 1) {
                kafkaTemplate.send("subscription.changed", "user-123", entry.payload)
            }
        }

        @Test
        fun `should mark entry as published after successful send`() {
            val entryId = 42L
            val entry = OutboxEntry(
                id = entryId,
                aggregateType = "subscription",
                aggregateId = "user-456",
                eventType = "subscription.changed",
                payload = "{}",
                createdAt = Instant.now()
            )
            every { outboxRepository.findUnpublished(any()) } returns listOf(entry)
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            val publishedAtSlot = slot<Instant>()
            every { outboxRepository.markPublished(eq(entryId), capture(publishedAtSlot)) } returns Unit

            service.pollAndPublish()

            verify(exactly = 1) { outboxRepository.markPublished(entryId, any()) }
            assertThat(publishedAtSlot.captured).isBeforeOrEqualTo(Instant.now())
        }

        @Test
        fun `should do nothing when no unpublished entries exist`() {
            every { outboxRepository.findUnpublished(any()) } returns emptyList()

            service.pollAndPublish()

            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
            verify(exactly = 0) { outboxRepository.markPublished(any(), any()) }
        }

        @Test
        fun `should process multiple entries in order`() {
            val entries = (1L..3L).map { i ->
                OutboxEntry(
                    id = i,
                    aggregateType = "subscription",
                    aggregateId = "user-$i",
                    eventType = "subscription.changed",
                    payload = """{"user_id":"user-$i"}""",
                    createdAt = Instant.now().minusSeconds(3 - i)
                )
            }
            every { outboxRepository.findUnpublished(any()) } returns entries
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            every { outboxRepository.markPublished(any(), any()) } returns Unit

            service.pollAndPublish()

            verify(exactly = 3) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
            verify(exactly = 3) { outboxRepository.markPublished(any(), any()) }
        }

        @Test
        fun `should not mark entry as published when Kafka send fails`() {
            val entry = OutboxEntry(
                id = 1L,
                aggregateType = "subscription",
                aggregateId = "user-err",
                eventType = "subscription.changed",
                payload = "{}",
                createdAt = Instant.now()
            )
            every { outboxRepository.findUnpublished(any()) } returns listOf(entry)
            val failedFuture = CompletableFuture<SendResult<String, String>>()
            failedFuture.completeExceptionally(RuntimeException("Kafka unavailable"))
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture

            service.pollAndPublish()

            verify(exactly = 0) { outboxRepository.markPublished(any(), any()) }
        }

        @Test
        fun `should use event type as Kafka topic and aggregate id as key`() {
            val entry = OutboxEntry(
                id = 1L,
                aggregateType = "subscription",
                aggregateId = "specific-user-id",
                eventType = "subscription.changed",
                payload = """{"data":"test"}""",
                createdAt = Instant.now()
            )
            every { outboxRepository.findUnpublished(any()) } returns listOf(entry)
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            every { outboxRepository.markPublished(any(), any()) } returns Unit

            service.pollAndPublish()

            verify(exactly = 1) {
                kafkaTemplate.send("subscription.changed", "specific-user-id", """{"data":"test"}""")
            }
        }
    }
}
