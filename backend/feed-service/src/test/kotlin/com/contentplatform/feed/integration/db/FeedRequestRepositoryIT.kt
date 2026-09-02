package com.contentplatform.feed.integration.db

import com.contentplatform.feed.db.entity.FeedRequestEntity
import com.contentplatform.feed.db.repository.FeedRequestRepository
import com.contentplatform.feed.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

@Tag("integration")
class FeedRequestRepositoryIT : IntegrationTestBase() {

    @Autowired
    lateinit var repository: FeedRequestRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM feed.feed_items")
        jdbcTemplate.execute("DELETE FROM feed.feed_requests")
    }

    @Test
    fun `should save and find feed request by requestId`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val requestedAt = Instant.parse("2026-04-19T10:00:00Z")

        val entity = FeedRequestEntity(
            requestId = requestId,
            userId = userId,
            requestedAt = requestedAt,
            pageNumber = 2,
            source = "personalized",
            countRequested = 30,
            countReturned = 20,
            latencyMs = 145,
            latencyBreakdown = """{"rec_system":70,"content_agg":40,"db":35}""",
            featureFlags = """{"new_ranker":true,"show_ads":false}""",
            abBucket = 3,
            appVersion = "1.4.2",
            deviceType = "android"
        )

        repository.save(entity)

        val found = repository.findByRequestId(requestId)
        assertThat(found).isNotNull
        assertThat(found!!.requestId).isEqualTo(requestId)
        assertThat(found.userId).isEqualTo(userId)
        assertThat(found.requestedAt).isEqualTo(requestedAt)
        assertThat(found.pageNumber).isEqualTo(2)
        assertThat(found.source).isEqualTo("personalized")
        assertThat(found.countRequested).isEqualTo(30)
        assertThat(found.countReturned).isEqualTo(20)
        assertThat(found.latencyMs).isEqualTo(145)
        assertThat(found.latencyBreakdown).contains("\"rec_system\": 70")
        assertThat(found.featureFlags).contains("\"new_ranker\": true")
        assertThat(found.abBucket).isEqualTo(3)
        assertThat(found.appVersion).isEqualTo("1.4.2")
        assertThat(found.deviceType).isEqualTo("android")
    }

    @Test
    fun `should return null when requestId not found`() {
        val result = repository.findByRequestId(UUID.randomUUID())
        assertThat(result).isNull()
    }

    @Test
    fun `should save feed request with nullable fields set to null`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val requestedAt = Instant.parse("2026-04-19T12:00:00Z")

        val entity = FeedRequestEntity(
            requestId = requestId,
            userId = userId,
            requestedAt = requestedAt,
            pageNumber = 0,
            source = "cold_start",
            countRequested = 30,
            countReturned = 0,
            latencyMs = null,
            latencyBreakdown = null,
            featureFlags = null,
            abBucket = 0,
            appVersion = null,
            deviceType = null
        )

        repository.save(entity)

        val found = repository.findByRequestId(requestId)
        assertThat(found).isNotNull
        assertThat(found!!.latencyMs).isNull()
        assertThat(found.latencyBreakdown).isNull()
        assertThat(found.featureFlags).isNull()
        assertThat(found.appVersion).isNull()
        assertThat(found.deviceType).isNull()
    }

    @Test
    fun `should verify feed_requests table has correct columns`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'feed' AND table_name = 'feed_requests'
            """.trimIndent(),
            String::class.java
        )

        assertThat(columns).containsExactlyInAnyOrder(
            "request_id",
            "user_id",
            "requested_at",
            "page_number",
            "source",
            "count_requested",
            "count_returned",
            "latency_ms",
            "latency_breakdown",
            "feature_flags",
            "ab_bucket",
            "app_version",
            "device_type"
        )
    }

    @Test
    fun `should verify indexes exist on feed_requests`() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'feed' AND tablename = 'feed_requests'
            """.trimIndent(),
            String::class.java
        )

        assertThat(indexes).contains(
            "idx_feed_requests_user_ts",
            "idx_feed_requests_source_ts"
        )
    }

    @Test
    fun `should enforce CHECK constraint on source column`() {
        val requestId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO feed.feed_requests
                    (request_id, user_id, source, count_requested, count_returned, ab_bucket, page_number)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                requestId, userId, "invalid_source_value", 30, 0, 0, 0
            )
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `should accept all valid source values`() {
        val validSources = listOf("personalized", "cold_start", "cached", "fallback")
        val userId = UUID.randomUUID()

        validSources.forEach { source ->
            val requestId = UUID.randomUUID()
            val entity = FeedRequestEntity(
                requestId = requestId,
                userId = userId,
                requestedAt = Instant.now(),
                pageNumber = 0,
                source = source,
                countRequested = 30,
                countReturned = 0,
                latencyMs = null,
                latencyBreakdown = null,
                featureFlags = null,
                abBucket = 0,
                appVersion = null,
                deviceType = null
            )
            repository.save(entity)
            val found = repository.findByRequestId(requestId)
            assertThat(found).isNotNull
            assertThat(found!!.source).isEqualTo(source)
        }
    }

    @Test
    fun `should enforce NOT NULL on user_id`() {
        val requestId = UUID.randomUUID()

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO feed.feed_requests
                    (request_id, user_id, source, count_requested, count_returned, ab_bucket, page_number)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                requestId, null, "personalized", 30, 0, 0, 0
            )
        }.isInstanceOf(DataAccessException::class.java)
    }
}
