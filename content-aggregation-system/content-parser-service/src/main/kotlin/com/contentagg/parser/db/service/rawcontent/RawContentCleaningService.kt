package com.contentagg.parser.db.service.rawcontent

import com.contentagg.parser.sanitization.HtmlSanitizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RawContentCleaningService(
    private val jdbcTemplate: JdbcTemplate,
    private val sanitizer: HtmlSanitizer,
    @Value("\${parser.cleaner.max-input-bytes:5242880}") private val maxInputBytes: Int,
) {
    companion object {
        private val log = LoggerFactory.getLogger(RawContentCleaningService::class.java)
    }

    @Transactional
    fun processBatch(batchSize: Int) {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT id, source_type,
                   raw_data->>'content' AS html,
                   raw_data->>'title'   AS title
            FROM data_flow.raw_content
            WHERE processing_status = 'COMPLETED'
              AND clean_text IS NULL
              AND cleaning_attempts < 3
            ORDER BY received_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            batchSize
        )

        if (rows.isEmpty()) {
            log.debug("No rows pending cleaning")
            return
        }

        log.info("Cleaning batch of {} rows", rows.size)
        var successCount = 0

        for (row in rows) {
            val id = row["id"]?.toString() ?: continue
            val sourceType = row["source_type"] as? String ?: ""
            val rawHtml = row["html"] as? String ?: ""
            val title = row["title"] as? String ?: ""

            try {
                val htmlBytes = rawHtml.toByteArray(Charsets.UTF_8)
                if (htmlBytes.size > maxInputBytes) {
                    log.warn("Oversized raw HTML: id={}, size={} > maxInputBytes={}", id, htmlBytes.size, maxInputBytes)
                    jdbcTemplate.update(
                        """
                        UPDATE data_flow.raw_content
                        SET cleaning_status = 'OVERSIZED',
                            cleaning_error = 'raw HTML exceeds max input bytes',
                            cleaning_attempts = cleaning_attempts + 1
                        WHERE id = ?::uuid
                        """.trimIndent(),
                        id
                    )
                    continue
                }

                val cleanHtml = sanitizer.sanitize(rawHtml, sourceType)
                val cleanText = sanitizer.toPlainText(cleanHtml)
                val previewSource = when {
                    title.isNotBlank() && cleanText.isNotBlank() -> "$title. $cleanText"
                    cleanText.isNotBlank() -> cleanText
                    else -> title
                }
                val preview = sanitizer.buildPreview(previewSource, 300)

                jdbcTemplate.update(
                    """
                    UPDATE data_flow.raw_content
                    SET clean_html       = ?,
                        clean_text       = ?,
                        preview_text     = ?,
                        clean_text_length = ?,
                        cleaning_status  = 'OK',
                        cleaning_error   = NULL,
                        cleaning_attempts = cleaning_attempts + 1,
                        cleaned_at       = now()
                    WHERE id = ?::uuid
                    """.trimIndent(),
                    cleanHtml,
                    cleanText,
                    preview,
                    cleanText.length,
                    id
                )
                successCount++
            } catch (e: Exception) {
                log.error("Cleaning failed for id={}: {}", id, e.message, e)
                jdbcTemplate.update(
                    """
                    UPDATE data_flow.raw_content
                    SET cleaning_status   = 'FAILED',
                        cleaning_error    = ?,
                        cleaning_attempts = cleaning_attempts + 1
                    WHERE id = ?::uuid
                    """.trimIndent(),
                    e.message?.take(500),
                    id
                )
            }
        }

        log.info("Cleaning batch processed: {}/{} rows succeeded", successCount, rows.size)
    }
}
