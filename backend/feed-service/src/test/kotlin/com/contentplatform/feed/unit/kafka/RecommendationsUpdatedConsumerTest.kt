package com.contentplatform.feed.unit.kafka

import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.contentplatform.feed.infrastructure.kafka.RecommendationsUpdatedConsumer
import com.contentplatform.feed.infrastructure.kafka.RecommendationsUpdatedEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class RecommendationsUpdatedConsumerTest {

    private val feedCacheService = mockk<FeedCacheService>()
    private val consumer = RecommendationsUpdatedConsumer(feedCacheService)

    @Test
    fun `should delete feed cache when receiving valid recommendations updated event`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val event = RecommendationsUpdatedEvent(
            eventType = "recommendations.updated",
            userId = userId.toString(),
            reason = "interactions_processed",
            timestamp = "2026-04-05T10:00:00Z"
        )

        every { feedCacheService.deleteFeed(userId) } just runs

        consumer.consume(event)

        verify(exactly = 1) { feedCacheService.deleteFeed(userId) }
    }

    @Test
    fun `should process event with unknown event type without crashing`() {
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val event = RecommendationsUpdatedEvent(
            eventType = "some.unknown.event",
            userId = userId.toString(),
            reason = "unknown_reason",
            timestamp = "2026-04-05T11:00:00Z"
        )

        every { feedCacheService.deleteFeed(userId) } just runs

        assertThatCode { consumer.consume(event) }.doesNotThrowException()

        verify(exactly = 1) { feedCacheService.deleteFeed(userId) }
    }

    @Test
    fun `should handle malformed user id gracefully without throwing`() {
        val event = RecommendationsUpdatedEvent(
            eventType = "recommendations.updated",
            userId = "not-a-valid-uuid",
            reason = "interactions_processed",
            timestamp = "2026-04-05T12:00:00Z"
        )

        assertThatCode { consumer.consume(event) }.doesNotThrowException()
    }
}
