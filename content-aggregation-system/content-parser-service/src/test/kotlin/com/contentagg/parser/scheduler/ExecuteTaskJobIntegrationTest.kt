package com.contentagg.parser.scheduler

import com.contentagg.parser.db.service.parsertask.ParserTaskService
import com.contentagg.parser.integration.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the claim-one-pending logic that backs ExecuteTaskJob.
 *
 * Tests verify:
 * 1. Concurrent workers claiming tasks result in zero duplicate claims
 *    (FOR UPDATE SKIP LOCKED guarantee).
 * 2. Only tasks whose source_type matches the enabled-types filter are claimed.
 *
 * Tests exercise ParserTaskService.claimOnePending() directly — no Quartz scheduler firing.
 */
class ExecuteTaskJobIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var parserTaskService: ParserTaskService

    @BeforeEach
    fun cleanUp() {
        truncateParserTasks(jdbcTemplate)
    }

    /**
     * Insert N PENDING tasks then launch 3 concurrent threads that each claim
     * tasks in a loop until the queue is empty.
     *
     * Assertion: no task is claimed more than once across all threads (FOR UPDATE
     * SKIP LOCKED prevents double-claim), and the total distinct IDs == N.
     */
    @Test
    fun parallelClaimsNoDuplicates() {
        val n = 20
        val sourceId = UUID.randomUUID()
        insertPendingTasks(n, sourceId, "HABR")

        val claimedIds: MutableList<UUID> = Collections.synchronizedList(mutableListOf())
        val allTypesArray = arrayOf("HABR", "VCRU", "TELEGRAM")

        val threadCount = 3
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { workerIdx ->
            executor.submit {
                try {
                    startLatch.await()
                    while (true) {
                        val claimed = claimOne(allTypesArray, "worker-$workerIdx") ?: break
                        claimedIds.add(claimed)
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()

        assertThat(claimedIds).hasSize(n)
        assertThat(claimedIds.toSet()).hasSize(n)
    }

    /**
     * Insert tasks of types HABR, VCRU, TELEGRAM.
     * Claim with filter ["HABR"] only.
     *
     * Assertion: only HABR tasks are claimed; VCRU and TELEGRAM remain PENDING.
     */
    @Test
    fun sourceTypeFilterRespected() {
        val sourceId = UUID.randomUUID()
        insertPendingTasks(3, sourceId, "HABR")
        insertPendingTasks(2, UUID.randomUUID(), "VCRU")
        insertPendingTasks(2, UUID.randomUUID(), "TELEGRAM")

        val habrTypesArray = arrayOf("HABR")
        val claimedIds = mutableListOf<UUID>()
        while (true) {
            val claimed = claimOne(habrTypesArray, "filter-worker") ?: break
            claimedIds.add(claimed)
        }

        assertThat(claimedIds).hasSize(3)

        // VCRU and TELEGRAM tasks must remain PENDING
        val remainingVcruTelegram = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM data_flow.parser_tasks
            WHERE status = 'PENDING' AND source_type IN ('VCRU','TELEGRAM')
            """.trimIndent(),
            Int::class.java,
        )
        assertThat(remainingVcruTelegram).isEqualTo(4)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Insert `count` PENDING tasks, each with a UNIQUE source_id to avoid
     * conflicting with the partial unique index uq_parser_tasks_active_source
     * (only one PENDING/RUNNING task per source_id is permitted).
     * The `baseSourceId` parameter is unused — kept for call-site readability.
     */
    private fun insertPendingTasks(count: Int, @Suppress("UNUSED_PARAMETER") baseSourceId: UUID, sourceType: String) {
        repeat(count) {
            val uniqueSourceId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO data_flow.parser_tasks
                    (id, source_id, source_type, task_type, status, scheduled_at, retry_count, created_at, updated_at)
                VALUES
                    (gen_random_uuid(), ?, ?, 'PARSE_SOURCE', 'PENDING', NOW(), 0, NOW(), NOW())
                """.trimIndent(),
                uniqueSourceId, sourceType,
            )
        }
    }

    /**
     * Delegates to the production ParserTaskService.claimOnePending() so that regressions
     * in the actual claim SQL (FOR UPDATE SKIP LOCKED + UPDATE) are caught by these tests.
     * Each call runs in its own REQUIRES_NEW transaction (enforced by the service method).
     */
    private fun claimOne(enabledTypes: Array<String>, instanceId: String): UUID? {
        return parserTaskService.claimOnePending(enabledTypes.toList(), instanceId)?.id
    }
}
