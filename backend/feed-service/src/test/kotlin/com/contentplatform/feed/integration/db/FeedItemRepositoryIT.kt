package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.entity.FeedItemEntity
import com.contentplatform.feed.db.entity.FeedRequestEntity
import com.contentplatform.feed.db.repository.FeedItemRepository
import com.contentplatform.feed.db.repository.FeedRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import com.contentplatform.feed.integration.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

@Tag("integration")
class FeedItemRepositoryIT : IntegrationTestBase() {

    @Autowired
    lateinit var feedItemRepository: FeedItemRepository

    @Autowired
    lateinit var feedRequestRepository: FeedRequestRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.feed_items")
        jdbcTemplate.execute("DELETE FROM feed.feed_requests")
    }

    private fun insertParentRequest(requestId: UUID = UUID.randomUUID()): UUID {
        feedRequestRepository.save(
            FeedRequestEntity(
                requestId = requestId,
                userId = UUID.randomUUID(),
                requestedAt = Instant.parse("2026-04-19T10:00:00Z"),
                pageNumber = 0,
                source = "personalized",
                countRequested = 30,
                countReturned = 3,
                latencyMs = 100,
                latencyBreakdown = null,
                featureFlags = null,
                abBucket = 0,
                appVersion = null,
                deviceType = null
            )
        )
        return requestId
    }

    @Test
    fun `should save items and find by requestId ordered by position`() {
        val requestId = insertParentRequest()
        val contentId0 = UUID.randomUUID()
        val contentId1 = UUID.randomUUID()
        val contentId2 = UUID.randomUUID()

        val items = listOf(
            FeedItemEntity(
                requestId = requestId,
                position = 2,
                contentId = contentId2,
                rawContentId = UUID.randomUUID(),
                finalScore = 0.75f,
                scoringComponents = """{"semantic":0.7,"freshness":0.8}""",
                rerankScore = 0.72f,
                filteredOutBy = null
            ),
            FeedItemEntity(
                requestId = requestId,
                position = 0,
                contentId = contentId0,
                rawContentId = UUID.randomUUID(),
                finalScore = 0.95f,
                scoringComponents = """{"semantic":0.9,"freshness":1.0}""",
                rerankScore = 0.93f,
                filteredOutBy = null
            ),
            FeedItemEntity(
                requestId = requestId,
                position = 1,
                contentId = contentId1,
                rawContentId = null,
                finalScore = 0.85f,
                scoringComponents = null,
                rerankScore = null,
                filteredOutBy = "dislike"
            )
        )

        feedItemRepository.saveAll(items)

        val found = feedItemRepository.findByRequestId(requestId)
        assertThat(found).hasSize(3)
        assertThat(found.map { it.position }).containsExactly(0.toShort(), 1.toShort(), 2.toShort())
        assertThat(found[0].contentId).isEqualTo(contentId0)
        assertThat(found[1].contentId).isEqualTo(contentId1)
        assertThat(found[2].contentId).isEqualTo(contentId2)

        assertThat(found[0].finalScore).isEqualTo(0.95f)
        assertThat(found[0].scoringComponents).contains("\"semantic\": 0.9")
        assertThat(found[0].filteredOutBy).isNull()

        assertThat(found[1].rawContentId).isNull()
        assertThat(found[1].scoringComponents).isNull()
        assertThat(found[1].rerankScore).isNull()
        assertThat(found[1].filteredOutBy).isEqualTo("dislike")
    }

    @Test
    fun `should return empty list when no items for requestId`() {
        val result = feedItemRepository.findByRequestId(UUID.randomUUID())
        assertThat(result).isEmpty()
    }

    @Test
    fun `should save empty list without error`() {
        feedItemRepository.saveAll(emptyList())
        // no exception
    }

    @Test
    fun `should cascade delete items when parent feed_request is deleted`() {
        val requestId = insertParentRequest()

        feedItemRepository.saveAll(
            listOf(
                FeedItemEntity(
                    requestId = requestId,
                    position = 0,
                    contentId = UUID.randomUUID(),
                    rawContentId = null,
                    finalScore = 0.5f,
                    scoringComponents = null,
                    rerankScore = null,
                    filteredOutBy = null
                ),
                FeedItemEntity(
                    requestId = requestId,
                    position = 1,
                    contentId = UUID.randomUUID(),
                    rawContentId = null,
                    finalScore = 0.4f,
                    scoringComponents = null,
                    rerankScore = null,
                    filteredOutBy = null
                )
            )
        )

        assertThat(feedItemRepository.findByRequestId(requestId)).hasSize(2)

        jdbcTemplate.update("DELETE FROM feed.feed_requests WHERE request_id = ?", requestId)

        assertThat(feedItemRepository.findByRequestId(requestId)).isEmpty()
    }

    @Test
    fun `should reject insert when parent feed_request does not exist`() {
        val orphanRequestId = UUID.randomUUID()
        val item = FeedItemEntity(
            requestId = orphanRequestId,
            position = 0,
            contentId = UUID.randomUUID(),
            rawContentId = null,
            finalScore = 0.5f,
            scoringComponents = null,
            rerankScore = null,
            filteredOutBy = null
        )

        assertThatThrownBy {
            feedItemRepository.saveAll(listOf(item))
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `should enforce composite primary key on requestId and position`() {
        val requestId = insertParentRequest()
        val duplicate = FeedItemEntity(
            requestId = requestId,
            position = 0,
            contentId = UUID.randomUUID(),
            rawContentId = null,
            finalScore = 0.5f,
            scoringComponents = null,
            rerankScore = null,
            filteredOutBy = null
        )

        feedItemRepository.saveAll(listOf(duplicate))

        assertThatThrownBy {
            feedItemRepository.saveAll(
                listOf(
                    FeedItemEntity(
                        requestId = requestId,
                        position = 0,
                        contentId = UUID.randomUUID(),
                        rawContentId = null,
                        finalScore = 0.6f,
                        scoringComponents = null,
                        rerankScore = null,
                        filteredOutBy = null
                    )
                )
            )
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `should verify feed_items table has correct columns`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'feed' AND table_name = 'feed_items'
            """.trimIndent(),
            String::class.java
        )

        assertThat(columns).containsExactlyInAnyOrder(
            "request_id",
            "position",
            "content_id",
            "raw_content_id",
            "final_score",
            "scoring_components",
            "rerank_score",
            "filtered_out_by"
        )
    }

    @Test
    fun `should verify idx_feed_items_content_id index exists`() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'feed' AND tablename = 'feed_items'
            """.trimIndent(),
            String::class.java
        )

        assertThat(indexes).contains("idx_feed_items_content_id")
    }
}
