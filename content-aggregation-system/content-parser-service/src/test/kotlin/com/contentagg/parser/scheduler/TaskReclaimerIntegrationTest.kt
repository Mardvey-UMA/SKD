package com.contentagg.parser.scheduler

import com.contentagg.parser.db.service.parsertask.ParserTaskService
import com.contentagg.parser.integration.AbstractIntegrationTest
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Integration test for TaskReclaimer.@PreDestroy graceful-shutdown behaviour.
 *
 * TaskReclaimer.reclaimOwnTasks() issues:
 *   UPDATE parser_tasks SET status='PENDING', claimed_by=NULL, claimed_at=NULL
 *   WHERE claimed_by = ? AND status = 'RUNNING'
 *
 * We test the underlying SQL contract via ParserTaskService.resetOwnRunningTasks()
 * which executes the same query (RESET_OWN_RUNNING_SQL). This covers the behaviour
 * without needing a live Quartz Scheduler bean in the test context.
 */
class TaskReclaimerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var parserTaskService: ParserTaskService

    @BeforeEach
    fun cleanUp() {
        truncateParserTasks(jdbcTemplate)
    }

    /**
     * Insert RUNNING tasks — some owned by "this-instance", others by "other-instance".
     * Call resetOwnRunningTasks("this-instance").
     *
     * Only tasks with claimed_by == "this-instance" must be reset to PENDING.
     * Tasks owned by "other-instance" must remain RUNNING.
     */
    @Test
    fun preDestroyResetsOwnRunningTasks() {
        val ownInstance = "this-instance"
        val otherInstance = "other-instance"

        // 3 tasks owned by this instance
        val ownTask1 = insertRunningTask(claimedBy = ownInstance)
        val ownTask2 = insertRunningTask(claimedBy = ownInstance)
        val ownTask3 = insertRunningTask(claimedBy = ownInstance)
        // 2 tasks owned by another instance
        val otherTask1 = insertRunningTask(claimedBy = otherInstance)
        val otherTask2 = insertRunningTask(claimedBy = otherInstance)

        val resetCount = parserTaskService.resetOwnRunningTasks(ownInstance)

        assertThat(resetCount).isEqualTo(3)

        // Own tasks must be PENDING with claim fields cleared
        for (taskId in listOf(ownTask1, ownTask2, ownTask3)) {
            val row = fetchTask(taskId)
            assertThat(row["status"])
                .withFailMessage("Expected own task $taskId to be PENDING")
                .isEqualTo("PENDING")
            assertThat(row["claimed_by"])
                .withFailMessage("Expected claimed_by to be NULL for task $taskId")
                .isNull()
            assertThat(row["claimed_at"])
                .withFailMessage("Expected claimed_at to be NULL for task $taskId")
                .isNull()
        }

        // Other instance tasks must remain RUNNING and unchanged
        for (taskId in listOf(otherTask1, otherTask2)) {
            val row = fetchTask(taskId)
            assertThat(row["status"])
                .withFailMessage("Expected other-instance task $taskId to stay RUNNING")
                .isEqualTo("RUNNING")
            assertThat(row["claimed_by"])
                .withFailMessage("Expected claimed_by to remain '$otherInstance' for task $taskId")
                .isEqualTo(otherInstance)
        }
    }

    /**
     * Exercises TaskReclaimer directly — verifies that reclaimOwnTasks() resets ONLY the
     * tasks whose claimed_by matches the scheduler's instanceId, leaving other instances untouched.
     *
     * This test catches regressions in TaskReclaimer.init() / @PreDestroy that the
     * SQL-contract test above cannot detect, because it instantiates the component itself.
     */
    @Test
    fun `preDestroy resets own running tasks via TaskReclaimer component`() {
        // Given: one RUNNING task claimed by "this-instance", one by "other-instance"
        val myTaskId = insertRunningTask(claimedBy = "this-instance")
        val otherTaskId = insertRunningTask(claimedBy = "other-instance")

        // Mock Quartz Scheduler to return a deterministic instanceId
        val mockScheduler = mockk<Scheduler>(relaxed = true) {
            every { schedulerInstanceId } returns "this-instance"
        }

        val reclaimer = TaskReclaimer(jdbcTemplate, mockScheduler)
        reclaimer.init()
        reclaimer.reclaimOwnTasks()

        // Then: only my task is reset to PENDING with claim fields cleared
        assertTaskStatus(myTaskId, expectedStatus = "PENDING", expectedClaimedBy = null)
        // Other instance's task must remain RUNNING and unchanged
        assertTaskStatus(otherTaskId, expectedStatus = "RUNNING", expectedClaimedBy = "other-instance")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun insertRunningTask(claimedBy: String): UUID {
        val taskId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO data_flow.parser_tasks
                (id, source_id, source_type, task_type, status, scheduled_at,
                 claimed_by, claimed_at, started_at, retry_count, created_at, updated_at)
            VALUES
                (?, gen_random_uuid(), 'HABR', 'PARSE_SOURCE', 'RUNNING', NOW(),
                 ?, NOW(), NOW(), 0, NOW(), NOW())
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

    private fun assertTaskStatus(taskId: UUID, expectedStatus: String, expectedClaimedBy: String?) {
        val row = fetchTask(taskId)
        assertThat(row["status"])
            .withFailMessage("Expected task $taskId to have status=$expectedStatus but was ${row["status"]}")
            .isEqualTo(expectedStatus)
        assertThat(row["claimed_by"])
            .withFailMessage("Expected claimed_by=$expectedClaimedBy for task $taskId but was ${row["claimed_by"]}")
            .isEqualTo(expectedClaimedBy)
    }
}
