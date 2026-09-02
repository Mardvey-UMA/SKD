package com.contentplatform.feed.integration.db

import com.contentplatform.feed.application.FeedItemLog
import com.contentplatform.feed.application.FeedLogService
import com.contentplatform.feed.application.FeedRequestLog
import com.contentplatform.feed.db.repository.FeedItemRepository
import com.contentplatform.feed.db.repository.FeedRequestRepository
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Tag("integration")
class FeedLogServiceIT : IntegrationTestBase() {

    @Autowired
    lateinit var feedLogService: FeedLogService

    @Autowired
    lateinit var feedRequestRepository: FeedRequestRepository

    @Autowired
    lateinit var feedItemRepository: FeedItemRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.feed_items")
        jdbcTemplate.execute("DELETE FROM feed.feed_requests")
    }

    @Test
    fun `logFeedRequest persists feed_requests row with correct scalar fields`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val now = Instant.now()

        val log = FeedRequestLog(
            requestId = requestId,
            userId = userId,
            requestedAt = now,
            pageNumber = 0,
            source = "personalized",
            countRequested = 20,
            countReturned = 15,
            latencyMs = 200,
            latencyBreakdown = mapOf("rec_system_ms" to 95L, "content_fetch_ms" to 40L),
            featureFlags = null,
            abBucket = 0,
            appVersion = "1.2.3",
            deviceType = "android"
        )

        feedLogService.logFeedRequest(log, emptyList())

        val saved = await().atMost(Duration.ofSeconds(5))
            .until({ feedRequestRepository.findByRequestId(requestId) }) { it != null }

        assertThat(saved).isNotNull
        assertThat(saved!!.requestId).isEqualTo(requestId)
        assertThat(saved.userId).isEqualTo(userId)
        assertThat(saved.source).isEqualTo("personalized")
        assertThat(saved.countRequested).isEqualTo(20)
        assertThat(saved.countReturned).isEqualTo(15)
        assertThat(saved.latencyMs).isEqualTo(200)
        assertThat(saved.appVersion).isEqualTo("1.2.3")
        assertThat(saved.deviceType).isEqualTo("android")
    }

    @Test
    fun `logFeedRequest persists JSONB latency_breakdown as JSON string`() {
        val requestId = UUID.randomUUID()
        val log = baseRequestLog(requestId).copy(
            latencyBreakdown = mapOf(
                "rec_system_ms" to 95L,
                "content_fetch_ms" to 40L,
                "dislike_filter_ms" to 8L,
                "total_ms" to 143L
            )
        )

        feedLogService.logFeedRequest(log, emptyList())

        val saved = await().atMost(Duration.ofSeconds(5))
            .until({ feedRequestRepository.findByRequestId(requestId) }) { it != null }

        assertThat(saved).isNotNull
        assertThat(saved!!.latencyBreakdown).isNotNull
        assertThat(saved.latencyBreakdown).contains("rec_system_ms")
        assertThat(saved.latencyBreakdown).contains("total_ms")
    }

    @Test
    fun `logFeedRequest persists feed_items rows with correct position and content_id`() {
        val requestId = UUID.randomUUID()
        val contentId1 = UUID.randomUUID()
        val contentId2 = UUID.randomUUID()

        val log = baseRequestLog(requestId)
        val items = listOf(
            FeedItemLog(
                requestId = requestId,
                position = 0,
                contentId = contentId1,
                rawContentId = null,
                finalScore = 0.85f,
                scoringComponents = mapOf("topic_match" to 0.42, "freshness" to 0.15),
                rerankScore = null,
                filteredOutBy = null
            ),
            FeedItemLog(
                requestId = requestId,
                position = 1,
                contentId = contentId2,
                rawContentId = null,
                finalScore = 0.72f,
                scoringComponents = null,
                rerankScore = null,
                filteredOutBy = null
            )
        )

        feedLogService.logFeedRequest(log, items)

        val savedItems = await().atMost(Duration.ofSeconds(5))
            .until({ feedItemRepository.findByRequestId(requestId) }) { it.size == 2 }

        assertThat(savedItems).hasSize(2)
        assertThat(savedItems[0].position).isEqualTo(0.toShort())
        assertThat(savedItems[0].contentId).isEqualTo(contentId1)
        assertThat(savedItems[0].finalScore).isEqualTo(0.85f)
        assertThat(savedItems[0].scoringComponents).isNotNull
        assertThat(savedItems[1].position).isEqualTo(1.toShort())
        assertThat(savedItems[1].contentId).isEqualTo(contentId2)
    }

    @Test
    fun `logFeedRequest persists feed_items with correct cascade - items deleted when request deleted`() {
        val requestId = UUID.randomUUID()
        val contentId = UUID.randomUUID()

        val log = baseRequestLog(requestId)
        val items = listOf(
            FeedItemLog(
                requestId = requestId,
                position = 0,
                contentId = contentId,
                rawContentId = null,
                finalScore = null,
                scoringComponents = null,
                rerankScore = null,
                filteredOutBy = null
            )
        )

        feedLogService.logFeedRequest(log, items)

        // Wait for async write to complete
        await().atMost(Duration.ofSeconds(5))
            .until({ feedItemRepository.findByRequestId(requestId).size }) { it == 1 }

        // Verify saved
        assertThat(feedItemRepository.findByRequestId(requestId)).hasSize(1)

        // Delete the request (cascade should delete items)
        jdbcTemplate.update("DELETE FROM feed.feed_requests WHERE request_id = ?::uuid", requestId.toString())

        // Items should be gone
        assertThat(feedItemRepository.findByRequestId(requestId)).isEmpty()
    }

    @Test
    fun `logFeedRequest persists scoring_components JSONB with all 6 canonical keys`() {
        val requestId = UUID.randomUUID()
        val contentId = UUID.randomUUID()
        val log = baseRequestLog(requestId)
        val items = listOf(
            FeedItemLog(
                requestId = requestId,
                position = 1,
                contentId = contentId,
                rawContentId = UUID.randomUUID(),
                finalScore = 0.742f,
                scoringComponents = mapOf(
                    "topic_match" to 0.42,
                    "embedding_sim" to 0.31,
                    "entity_match" to 0.05,
                    "sentiment_match" to 0.00,
                    "freshness" to 0.15,
                    "format_match" to 0.02
                ),
                rerankScore = null,
                filteredOutBy = null
            )
        )

        feedLogService.logFeedRequest(log, items)

        val savedItems = await().atMost(Duration.ofSeconds(5))
            .until({ feedItemRepository.findByRequestId(requestId) }) { it.isNotEmpty() }

        assertThat(savedItems).hasSize(1)
        val saved = savedItems.first()
        assertThat(saved.scoringComponents).isNotNull
        assertThat(saved.scoringComponents).contains("topic_match")
        assertThat(saved.scoringComponents).contains("embedding_sim")
        assertThat(saved.scoringComponents).contains("entity_match")
        assertThat(saved.scoringComponents).contains("sentiment_match")
        assertThat(saved.scoringComponents).contains("freshness")
        assertThat(saved.scoringComponents).contains("format_match")
        assertThat(saved.finalScore).isEqualTo(0.742f)
    }

    private fun baseRequestLog(requestId: UUID) = FeedRequestLog(
        requestId = requestId,
        userId = UUID.randomUUID(),
        requestedAt = Instant.now(),
        pageNumber = 0,
        source = "personalized",
        countRequested = 20,
        countReturned = 20,
        latencyMs = 100,
        latencyBreakdown = null,
        featureFlags = null,
        abBucket = 0,
        appVersion = null,
        deviceType = null
    )
}
