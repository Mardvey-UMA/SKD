package com.contentplatform.user.integration.job

import com.contentplatform.user.infrastructure.job.CleanupPublishedOutboxJob
import com.contentplatform.user.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Tag("integration")
class CleanupPublishedOutboxJobIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var cleanupJob: CleanupPublishedOutboxJob

    private val jobContext = io.mockk.mockk<JobExecutionContext>(relaxed = true)

    @BeforeEach
    fun cleanOutbox() {
        jdbcTemplate.update("DELETE FROM outbox")
    }

    @Test
    fun `should delete outbox entry published more than 3 days ago`() {
        val fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS)
        insertOutboxEntry(aggregateId = "old-entry", publishedAt = fourDaysAgo)

        cleanupJob.execute(jobContext)

        val remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Int::class.java)
        assertThat(remaining).isEqualTo(0)
    }

    @Test
    fun `should keep outbox entry published less than 3 days ago`() {
        val twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS)
        insertOutboxEntry(aggregateId = "recent-entry", publishedAt = twoDaysAgo)

        cleanupJob.execute(jobContext)

        val remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Int::class.java)
        assertThat(remaining).isEqualTo(1)
    }

    @Test
    fun `should keep unpublished outbox entry`() {
        insertOutboxEntry(aggregateId = "unpublished-entry", publishedAt = null)

        cleanupJob.execute(jobContext)

        val remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Int::class.java)
        assertThat(remaining).isEqualTo(1)
    }

    @Test
    fun `should delete only old published entries when mixed rows exist`() {
        val fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS)
        val twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS)

        insertOutboxEntry(aggregateId = "old-published", publishedAt = fourDaysAgo)
        insertOutboxEntry(aggregateId = "recent-published", publishedAt = twoDaysAgo)
        insertOutboxEntry(aggregateId = "unpublished", publishedAt = null)

        cleanupJob.execute(jobContext)

        val remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Int::class.java)
        assertThat(remaining).isEqualTo(2)

        val remainingIds = jdbcTemplate.queryForList("SELECT aggregate_id FROM outbox", String::class.java)
        assertThat(remainingIds).containsExactlyInAnyOrder("recent-published", "unpublished")
    }

    private fun insertOutboxEntry(aggregateId: String, publishedAt: Instant?) {
        jdbcTemplate.update(
            """INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, created_at, published_at)
               VALUES (?, ?, ?, ?, ?, ?)""",
            "User",
            aggregateId,
            "user.created",
            """{"event_type":"user.created","user_id":"${UUID.randomUUID()}"}""",
            Timestamp.from(Instant.now().minus(5, ChronoUnit.DAYS)),
            publishedAt?.let { Timestamp.from(it) }
        )
    }
}
