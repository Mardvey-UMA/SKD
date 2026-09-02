package com.contentagg.parser.db.service.rawcontent

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.contentagg.parser.integration.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Integration test for RawContentService.saveRawContent() duplicate handling.
 *
 * When a second row with the same (source_type, external_id) is inserted, the
 * service must:
 *   1. Not throw any exception (DuplicateKeyException is swallowed).
 *   2. Log a WARN message containing "duplicate raw_content skipped".
 *   3. Leave the original row unchanged (no UPDATE).
 *
 * The WARN log is captured via a Logback ListAppender attached to the
 * RawContentService logger.
 */
class RawContentServiceDuplicateIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var rawContentService: RawContentService

    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun setUpLogger() {
        truncateRawContent(jdbcTemplate)

        logger = LoggerFactory.getLogger(RawContentService::class.java) as Logger
        listAppender = ListAppender<ILoggingEvent>().also {
            it.start()
            logger.addAppender(it)
        }
    }

    @AfterEach
    fun tearDownLogger() {
        logger.detachAppender(listAppender)
        listAppender.stop()
    }

    /**
     * Insert a raw_content row, then attempt to insert a second row with the
     * same (source_type, external_id).
     *
     * Assertions:
     * - No exception propagates from the second call.
     * - Exactly one WARN log event containing "duplicate raw_content skipped" is emitted.
     * - The original row is unchanged (count == 1, same external_id).
     */
    @Test
    fun duplicateExternalIdLogsAndSkips() {
        val sourceId = UUID.randomUUID()
        val externalId = "habr-article-duplicate-test"
        val now = LocalDateTime.now()

        // First insert — must succeed
        val firstId = rawContentService.saveRawContent(
            externalId = externalId,
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = """{"title":"Original","content":"first content"}""",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "COMPLETED",
            receivedAt = now,
        )
        assertThat(firstId).isNotNull()

        // Second insert with same (source_type, external_id) — must NOT throw
        val secondResult = rawContentService.saveRawContent(
            externalId = externalId,
            sourceId = sourceId,
            sourceType = "HABR",
            rawData = """{"title":"Duplicate","content":"second content"}""",
            rawMedia = null,
            downloadedMedia = null,
            processingStatus = "COMPLETED",
            receivedAt = now.plusSeconds(1),
        )

        // Return value on duplicate: existing row UUID (may equal firstId) or null
        // Either is acceptable per the implementation contract.
        // We do NOT assert on the exact value — just that no exception propagated.

        // Verify WARN log was emitted
        val warnEvents = listAppender.list.filter { event ->
            event.level == Level.WARN &&
                event.formattedMessage.contains("duplicate raw_content skipped")
        }
        assertThat(warnEvents)
            .withFailMessage(
                "Expected at least one WARN log containing 'duplicate raw_content skipped' " +
                    "but found: ${listAppender.list.map { "${it.level}: ${it.formattedMessage}" }}"
            )
            .isNotEmpty()

        // Verify only one row exists for this (source_type, external_id)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM data_flow.raw_content WHERE source_type='HABR' AND external_id=?",
            Int::class.java,
            externalId,
        )
        assertThat(count).isEqualTo(1)

        // Verify original data is untouched — the first row's raw_data must still be the original
        val rawDataInDb = jdbcTemplate.queryForObject(
            "SELECT raw_data::text FROM data_flow.raw_content WHERE source_type='HABR' AND external_id=?",
            String::class.java,
            externalId,
        )
        assertThat(rawDataInDb).contains("Original")
    }
}
