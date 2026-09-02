package com.contentplatform.user.unit.outbox

import com.contentplatform.user.application.service.OutboxService
import com.contentplatform.user.db.repository.model.OutboxEventEntity
import com.contentplatform.user.infrastructure.outbox.OutboxPoller
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
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Tag("unit")
class OutboxPollerTest {

    private val outboxService = mockk<OutboxService>()
    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
    private val outboxPoller = OutboxPoller(outboxService, kafkaTemplate)

    @Nested
    inner class `poll` {

        @Test
        fun `should send unpublished event to Kafka and mark as published`() {
            val userId = UUID.randomUUID()
            val payload = """{"event_type":"user.created","user_id":"$userId","email":"test@example.com","timestamp":"2026-04-05T10:00:00Z"}"""
            val event = OutboxEventEntity(
                id = 1L,
                aggregateType = "User",
                aggregateId = userId.toString(),
                eventType = "user.created",
                payload = payload,
                createdAt = Instant.now(),
                publishedAt = null
            )

            every { outboxService.findUnpublished(any()) } returns listOf(event)
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            every { outboxService.markPublished(1L) } returns Unit

            outboxPoller.poll()

            verify(exactly = 1) { outboxService.markPublished(1L) }
        }

        @Test
        fun `should not send anything to Kafka when no unpublished events exist`() {
            every { outboxService.findUnpublished(any()) } returns emptyList()

            outboxPoller.poll()

            verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        }

        @Test
        fun `should not mark event as published when Kafka send throws exception`() {
            val userId = UUID.randomUUID()
            val payload = """{"event_type":"user.created","user_id":"$userId","email":"fail@example.com","timestamp":"2026-04-05T10:00:00Z"}"""
            val event = OutboxEventEntity(
                id = 2L,
                aggregateType = "User",
                aggregateId = userId.toString(),
                eventType = "user.created",
                payload = payload,
                createdAt = Instant.now(),
                publishedAt = null
            )

            every { outboxService.findUnpublished(any()) } returns listOf(event)
            every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns
                CompletableFuture<SendResult<String, String>>().also {
                    it.completeExceptionally(RuntimeException("Kafka broker unavailable"))
                }

            outboxPoller.poll()

            verify(exactly = 0) { outboxService.markPublished(any()) }
        }

        @Test
        fun `should use user_id from payload as Kafka message key`() {
            val userId = UUID.randomUUID()
            val payload = """{"event_type":"user.created","user_id":"$userId","email":"key@example.com","timestamp":"2026-04-05T10:00:00Z"}"""
            val event = OutboxEventEntity(
                id = 3L,
                aggregateType = "User",
                aggregateId = userId.toString(),
                eventType = "user.created",
                payload = payload,
                createdAt = Instant.now(),
                publishedAt = null
            )

            val keySlot = slot<String>()
            every { outboxService.findUnpublished(any()) } returns listOf(event)
            every { kafkaTemplate.send(any<String>(), capture(keySlot), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            every { outboxService.markPublished(any()) } returns Unit

            outboxPoller.poll()

            assertThat(keySlot.captured).isEqualTo(userId.toString())
        }

        @Test
        fun `should send to user created topic`() {
            val userId = UUID.randomUUID()
            val payload = """{"event_type":"user.created","user_id":"$userId","email":"topic@example.com","timestamp":"2026-04-05T10:00:00Z"}"""
            val event = OutboxEventEntity(
                id = 4L,
                aggregateType = "User",
                aggregateId = userId.toString(),
                eventType = "user.created",
                payload = payload,
                createdAt = Instant.now(),
                publishedAt = null
            )

            val topicSlot = slot<String>()
            every { outboxService.findUnpublished(any()) } returns listOf(event)
            every { kafkaTemplate.send(capture(topicSlot), any<String>(), any<String>()) } returns
                CompletableFuture.completedFuture(mockk<SendResult<String, String>>())
            every { outboxService.markPublished(any()) } returns Unit

            outboxPoller.poll()

            assertThat(topicSlot.captured).isEqualTo("user.created")
        }
    }
}
