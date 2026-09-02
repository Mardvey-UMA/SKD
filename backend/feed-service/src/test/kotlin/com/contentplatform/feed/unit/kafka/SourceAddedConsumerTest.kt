package com.contentplatform.feed.unit.kafka

import com.contentplatform.feed.application.SourceAdditionsService
import com.contentplatform.feed.infrastructure.kafka.SourceAddedConsumer
import com.contentplatform.feed.infrastructure.kafka.SourceAddedEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class SourceAddedConsumerTest {

    private val sourceAdditionsService = mockk<SourceAdditionsService>()
    private val consumer = SourceAddedConsumer(sourceAdditionsService)

    @Test
    fun `handle with complete event calls service`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val event = SourceAddedEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = "source.added",
            sourceId = sourceId.toString(),
            userId = userId.toString(),
            sourceType = "rss",
            name = "Kotlin Weekly",
            addedAt = "2026-04-16T10:00:00Z"
        )
        every {
            sourceAdditionsService.recordAddition(
                userId,
                sourceId,
                "rss",
                "Kotlin Weekly",
                Instant.parse("2026-04-16T10:00:00Z")
            )
        } just runs

        consumer.handle(event)

        verify {
            sourceAdditionsService.recordAddition(
                userId, sourceId, "rss", "Kotlin Weekly",
                Instant.parse("2026-04-16T10:00:00Z")
            )
        }
    }

    @Test
    fun `handle discards event with missing userId`() {
        val event = SourceAddedEvent(
            sourceId = UUID.randomUUID().toString(),
            userId = null,
            sourceType = "rss",
            name = "X"
        )

        consumer.handle(event)

        verify(exactly = 0) { sourceAdditionsService.recordAddition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handle discards event with invalid uuid`() {
        val event = SourceAddedEvent(
            sourceId = "not-a-uuid",
            userId = UUID.randomUUID().toString(),
            sourceType = "rss",
            name = "X"
        )

        consumer.handle(event)

        verify(exactly = 0) { sourceAdditionsService.recordAddition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onMessage tolerates malformed JSON without throwing`() {
        consumer.onMessage("{not-json}")
        verify(exactly = 0) { sourceAdditionsService.recordAddition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onMessage parses valid JSON`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        every {
            sourceAdditionsService.recordAddition(
                userId,
                sourceId,
                "rss",
                "Kotlin Weekly",
                Instant.parse("2026-04-16T10:00:00Z")
            )
        } just runs

        val json = """
            {
              "event_id": "${UUID.randomUUID()}",
              "event_type": "source.added",
              "source_id": "$sourceId",
              "user_id": "$userId",
              "source_type": "rss",
              "name": "Kotlin Weekly",
              "added_at": "2026-04-16T10:00:00Z"
            }
        """.trimIndent()

        consumer.onMessage(json)

        verify {
            sourceAdditionsService.recordAddition(
                userId, sourceId, "rss", "Kotlin Weekly",
                Instant.parse("2026-04-16T10:00:00Z")
            )
        }
    }
}
