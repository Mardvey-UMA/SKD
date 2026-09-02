package com.contentagg.parser.processor.publisher

import com.contentagg.parser.db.repository.publishedcontent.PublishedContentRepository
import com.contentagg.parser.db.repository.rawcontent.RawContentRepository
import com.contentagg.parser.db.repository.rawcontent.model.RawContentPublishRow
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class PublishContentProcessor(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val publishedContentRepository: PublishedContentRepository,
    private val rawContentRepository: RawContentRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PublishContentProcessor::class.java)

        private const val FIND_PUBLISH_READY_SQL = """
            SELECT rc.id,
                   rc.external_id,
                   rc.source_id,
                   rc.source_type,
                   rc.raw_data,
                   rc.downloaded_media,
                   rc.received_at,
                   rc.clean_html,
                   rc.clean_text,
                   rc.preview_text,
                   rc.clean_text_length,
                   a.id           AS dedup_article_id,
                   a.content_hash AS dedup_content_hash
            FROM raw_content rc
            LEFT JOIN articles a ON a.raw_content_id = rc.id
            WHERE rc.processing_status = 'COMPLETED'
              AND rc.is_processed_by_dedup = true
              AND rc.is_processed_by_rec = true
              AND rc.is_published = false
              AND rc.clean_text IS NOT NULL
            ORDER BY rc.received_at
            LIMIT :batchSize
            FOR UPDATE OF rc SKIP LOCKED
        """
    }

    @Transactional
    fun publishBatch(batchSize: Int): Int {
        val rows = findPublishReady(batchSize)
        if (rows.isEmpty()) {
            log.debug("No publish-ready rows found")
            return 0
        }

        log.info("Publishing batch of {} rows", rows.size)
        val publishedIds = mutableListOf<UUID>()

        for (row in rows) {
            try {
                val rawDataMap = parseJsonMap(row.rawData)
                val entity = mapToPublishedContent(row, rawDataMap)

                publishedContentRepository.insertOnConflictDoNothing(
                    contentId = entity.contentId,
                    externalId = entity.externalId,
                    title = entity.title,
                    description = entity.description,
                    content = entity.content,
                    contentFormat = entity.contentFormat,
                    sourceId = entity.sourceId,
                    sourceType = entity.sourceType,
                    sourceSubtype = entity.sourceSubtype,
                    url = entity.url,
                    publishedAt = entity.publishedAt,
                    authorId = entity.authorId,
                    authorName = entity.authorName,
                    media = entity.media,
                    metadata = entity.metadata,
                    dedupArticleId = entity.dedupArticleId,
                    contentHash = entity.contentHash,
                    contentHtml = entity.contentHtml,
                    contentText = entity.contentText,
                    previewText = entity.previewText,
                    contentTextLength = entity.contentTextLength,
                )
                publishedIds.add(row.id)
            } catch (e: Exception) {
                log.error("Failed to publish row id={}: {}", row.id, e.message, e)
            }
        }

        if (publishedIds.isNotEmpty()) {
            rawContentRepository.markPublished(publishedIds.toTypedArray())
            log.info("Marked {} raw_content rows as published", publishedIds.size)
        }

        return publishedIds.size
    }

    private fun findPublishReady(batchSize: Int): List<RawContentPublishRow> {
        val params = MapSqlParameterSource("batchSize", batchSize)
        return jdbcTemplate.query(FIND_PUBLISH_READY_SQL, params) { rs, _ ->
            RawContentPublishRow(
                id = UUID.fromString(rs.getString("id")),
                externalId = rs.getString("external_id"),
                sourceId = UUID.fromString(rs.getString("source_id")),
                sourceType = rs.getString("source_type"),
                rawData = rs.getString("raw_data") ?: "{}",
                downloadedMedia = rs.getString("downloaded_media"),
                receivedAt = rs.getTimestamp("received_at").toLocalDateTime(),
                dedupArticleId = rs.getLong("dedup_article_id").takeIf { !rs.wasNull() },
                dedupContentHash = rs.getString("dedup_content_hash"),
                cleanHtml = rs.getString("clean_html"),
                cleanText = rs.getString("clean_text"),
                previewText = rs.getString("preview_text"),
                cleanTextLength = rs.getInt("clean_text_length").takeIf { !rs.wasNull() },
            )
        }
    }

    private fun mapToPublishedContent(
        row: RawContentPublishRow,
        rawData: Map<String, Any?>,
    ): com.contentagg.parser.db.repository.publishedcontent.model.PublishedContent {
        val contentFormat = rawData["contentFormat"] as? String ?: "HTML"
        val publishedAt = parsePublishedAt(rawData["publishedAt"])

        val metadataFields = setOf(
            "title", "lead", "content", "contentFormat", "url",
            "authorId", "authorName", "publishedAt", "sourceSubtype",
        )
        val metadataMap = rawData.filterKeys { it !in metadataFields && rawData[it] != null }
        val metadataJson = if (metadataMap.isNotEmpty()) {
            objectMapper.writeValueAsString(metadataMap)
        } else {
            null
        }

        return com.contentagg.parser.db.repository.publishedcontent.model.PublishedContent(
            id = null,
            contentId = row.id,
            externalId = row.externalId,
            title = rawData["title"] as? String,
            description = rawData["lead"] as? String,
            content = row.cleanHtml ?: (rawData["content"] as? String),
            contentFormat = contentFormat,
            sourceId = row.sourceId,
            sourceType = row.sourceType,
            sourceSubtype = rawData["sourceSubtype"] as? String,
            url = rawData["url"] as? String,
            publishedAt = publishedAt,
            authorId = rawData["authorId"] as? String,
            authorName = rawData["authorName"] as? String,
            media = row.downloadedMedia,
            metadata = metadataJson,
            dedupArticleId = row.dedupArticleId,
            contentHash = row.dedupContentHash,
            contentHtml = row.cleanHtml,
            contentText = row.cleanText,
            previewText = row.previewText,
            contentTextLength = row.cleanTextLength,
        )
    }

    private fun parsePublishedAt(value: Any?): OffsetDateTime? {
        if (value == null) return null
        return when (value) {
            is Number -> {
                val epochMillis = value.toLong()
                if (epochMillis <= 0L) null
                else OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
            }
            is String -> {
                if (value.isBlank()) return null
                runCatching { OffsetDateTime.parse(value) }.getOrElse {
                    runCatching {
                        val epochMillis = value.toLong()
                        if (epochMillis <= 0L) null
                        else OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                    }.getOrElse {
                        log.warn("Cannot parse publishedAt value: {}", value)
                        null
                    }
                }
            }
            else -> null
        }
    }

    private fun parseJsonMap(json: String): Map<String, Any?> =
        runCatching {
            objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
        }.getOrElse {
            log.warn("Failed to parse rawData JSON: {}", it.message)
            emptyMap()
        }
}
