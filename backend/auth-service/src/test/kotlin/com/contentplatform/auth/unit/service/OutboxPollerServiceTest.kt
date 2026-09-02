package com.contentplatform.auth.unit.service

import com.contentplatform.auth.db.repository.OutboxRepository
import com.contentplatform.auth.db.repository.model.OutboxEventEntity
import com.contentplatform.auth.service.OutboxPollerService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
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

    private val outboxPollerService = OutboxPollerService(
        outboxRepository = outboxRepository,
        kafkaTemplate = kafkaTemplate
    )

    @Nested
    inner class PollAndPublish {

        @Test
        fun `should send each unpublished entry to Kafka and mark as published`() {
            val entry1 = OutboxEventEntity(
                id = 1L,
                aggregateType = "User",
                aggregateId = "user-1",
                eventType = "user.registered",
                payload = """{"user_id":"user-1","email":"a@test.com"}""",
                createdAt = Instant.now()
            )
            val entry2 = OutboxEventEntity(
                id = 2L,
                aggregateType = "User",
                aggregateId = "user-2",
                eventType = "user.registered",
                payload = """{"user_id":"user-2","email":"b@test.com"}""",
                createdAt = Instant.now()
            )

            every { outboxRepository.findUnpublished(any()) } returns listOf(entry1, entry2)
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                completedSendResult()
            every { outboxRepository.markPublished(any(), any()) } just Runs

            outboxPollerService.pollAndPublish()

            verify(exactly = 1) { kafkaTemplate.send("user.registered", "user-1", entry1.payload) }
            verify(exactly = 1) { kafkaTemplate.send("user.registered", "user-2", entry2.payload) }
            verify(exactly = 1) { outboxRepository.markPublished(eq(1L), any()) }
            verify(exactly = 1) { outboxRepository.markPublished(eq(2L), any()) }
        }

        @Test
        fun `should do nothing when no unpublished entries exist`() {
            every { outboxRepository.findUnpublished(any()) } returns emptyList()

            outboxPollerService.pollAndPublish()

            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
            verify(exactly = 0) { outboxRepository.markPublished(any(), any()) }
        }

        @Test
        fun `should continue processing remaining entries when Kafka send fails for one`() {
            val entry1 = OutboxEventEntity(
                id = 1L,
                aggregateType = "User",
                aggregateId = "user-1",
                eventType = "user.registered",
                payload = """{"user_id":"user-1"}""",
                createdAt = Instant.now()
            )
            val entry2 = OutboxEventEntity(
                id = 2L,
                aggregateType = "User",
                aggregateId = "user-2",
                eventType = "user.registered",
                payload = """{"user_id":"user-2"}""",
                createdAt = Instant.now()
            )

            every { outboxRepository.findUnpublished(any()) } returns listOf(entry1, entry2)

            // First entry fails
            val failedFuture = CompletableFuture<SendResult<String, String>>()
            failedFuture.completeExceptionally(RuntimeException("Kafka unavailable"))
            every { kafkaTemplate.send("user.registered", "user-1", any()) } returns failedFuture

            // Second entry succeeds
            every { kafkaTemplate.send("user.registered", "user-2", any()) } returns completedSendResult()
            every { outboxRepository.markPublished(any(), any()) } just Runs

            outboxPollerService.pollAndPublish()

            // Only second entry should be marked as published
            verify(exactly = 0) { outboxRepository.markPublished(eq(1L), any()) }
            verify(exactly = 1) { outboxRepository.markPublished(eq(2L), any()) }
        }
    }

    private fun completedSendResult(): CompletableFuture<SendResult<String, String>> {
        val metadata = RecordMetadata(TopicPartition("user.registered", 0), 0, 0, 0, 0, 0)
        val record = ProducerRecord<String, String>("user.registered", "key", "value")
        return CompletableFuture.completedFuture(SendResult(record, metadata))
    }
}
