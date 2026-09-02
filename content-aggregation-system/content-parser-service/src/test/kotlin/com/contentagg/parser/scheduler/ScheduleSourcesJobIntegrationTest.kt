package com.contentagg.parser.scheduler

import com.contentagg.parser.db.service.util.SourceType
import com.contentagg.parser.integration.AbstractIntegrationTest
import com.contentagg.parser.integration.rest.configservice.cache.ParserConfigService
import com.contentagg.parser.integration.rest.configservice.model.SourceConfigResponse
import com.contentagg.parser.processor.scheduler.ScheduleSourcesProcessor
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Integration test for ScheduleSourcesJob — specifically the idempotent insert
 * behaviour: if a PENDING or RUNNING task already exists for a given source_id,
 * the partial unique index `uq_parser_tasks_active_source` causes an ON CONFLICT
 * DO NOTHING and no duplicate row is inserted.
 */
class ScheduleSourcesJobIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var scheduleSourcesProcessor: ScheduleSourcesProcessor

    @MockkBean
    private lateinit var parserConfigService: ParserConfigService

    @BeforeEach
    fun cleanUp() {
        truncateParserTasks(jdbcTemplate)
    }

    /**
     * A PENDING task for source S1 already exists in the queue.
     * When scheduleActiveSources() is called with S1 in the active-sources list,
     * it must NOT insert a second task — the ON CONFLICT DO NOTHING via the
     * partial unique index `uq_parser_tasks_active_source(source_id) WHERE status IN ('PENDING','RUNNING')`
     * prevents the duplicate.
     *
     * After the call exactly ONE PENDING task for S1 must exist.
     */
    @Test
    fun noDuplicateTasksForActiveSource() {
        val sourceId = UUID.randomUUID()
        insertPendingTask(sourceId, "HABR")

        // Mock ParserConfigService to return one active source matching sourceId
        val sourceConfig = SourceConfigResponse(
            id = sourceId.toString(),
            sourceType = SourceType.HABR,
            name = "Test Habr Source",
            url = "https://habr.com",
            updateFrequencyMinutes = 60,
            isActive = true,
            parameters = emptyMap(),
            createdAt = null,
            updatedAt = null,
        )
        every { parserConfigService.getActiveSources() } returns listOf(sourceConfig)

        scheduleSourcesProcessor.scheduleActiveSources()

        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM data_flow.parser_tasks
            WHERE source_id = ? AND status IN ('PENDING','RUNNING')
            """.trimIndent(),
            Int::class.java,
            sourceId,
        )
        assertThat(count)
            .withFailMessage("Expected exactly 1 active task for source $sourceId but found $count")
            .isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun insertPendingTask(sourceId: UUID, sourceType: String) {
        jdbcTemplate.update(
            """
            INSERT INTO data_flow.parser_tasks
                (id, source_id, source_type, task_type, status, scheduled_at, retry_count, created_at, updated_at)
            VALUES
                (gen_random_uuid(), ?, ?, 'PARSE_SOURCE', 'PENDING', NOW(), 0, NOW(), NOW())
            """.trimIndent(),
            sourceId, sourceType,
        )
    }
}
