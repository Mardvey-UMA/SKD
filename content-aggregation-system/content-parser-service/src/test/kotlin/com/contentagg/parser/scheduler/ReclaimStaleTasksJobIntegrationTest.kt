package com.contentagg.parser.scheduler

import com.contentagg.parser.db.service.parsertask.ParserTaskService
import com.contentagg.parser.integration.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Integration tests for ReclaimStaleTasksJob behaviour — specifically the
 * ParserTaskService.resetStale(thresholdMinutes) SQL logic.
 *
 * Tests exercise ParserTaskService directly; no Quartz scheduler firing.
 */
class ReclaimStaleTasksJobIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var parserTaskService: ParserTaskService

    @BeforeEach
    fun cleanUp() {
        truncateParserTasks(jdbcTemplate)
    }

    /**
     * A RUNNING task with claimed_at 15 minutes in the past exceeds the 10-minute
     * threshold and must be reset to PENDING with claim fields cleared.
     */
    @Test
    fun staleRunningTasksReset() {
        val taskId = insertRunningTask(claimedMinutesAgo = 15, claimedBy = "old-instance")

        val resetCount = parserTaskService.resetStale(thresholdMinutes = 10)

        assertThat(resetCount).isEqualTo(1)
        val row = fetchTask(taskId)
        assertThat(row["status"]).isEqualTo("PENDING")
        assertThat(row["claimed_by"]).isNull()
        assertThat(row["claimed_at"]).isNull()
    }

    /**
     * A RUNNING task with claimed_at 2 minutes in the past is within the 10-minute
     * threshold and must NOT be touched.
     */
    @Test
    fun freshRunningTasksKept() {
        val taskId = insertRunningTask(claimedMinutesAgo = 2, claimedBy = "active-instance")

        val resetCount = parserTaskService.resetStale(thresholdMinutes = 10)

        assertThat(resetCount).isEqualTo(0)
        val row = fetchTask(taskId)
        assertThat(row["status"]).isEqualTo("RUNNING")
        assertThat(row["claimed_by"]).isEqualTo("active-instance")
        assertThat(row["claimed_at"]).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun insertRunningTask(claimedMinutesAgo: Int, claimedBy: String): UUID {
        val taskId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO data_flow.parser_tasks
                (id, source_id, source_type, task_type, status, scheduled_at,
                 claimed_by, claimed_at, started_at, retry_count, created_at, updated_at)
            VALUES
                (?, gen_random_uuid(), 'HABR', 'PARSE_SOURCE', 'RUNNING', NOW(),
                 ?, NOW() - INTERVAL '$claimedMinutesAgo minutes', NOW() - INTERVAL '$claimedMinutesAgo minutes',
                 0, NOW(), NOW())
            """.trimIndent(),
            taskId, claimedBy,
        )
        return taskId
    }

    private fun fetchTask(taskId: UUID): Map<String, Any?> {
        return jdbcTemplate.queryForMap(
            "SELECT status, claimed_by, claimed_at FROM data_flow.parser_tasks WHERE id = ?",
            taskId,
        )
    }
}
