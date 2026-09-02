package com.contentagg.parser.db.repository.rawcontent.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Projection for publish-ready raw_content rows with optional dedup info from LEFT JOIN articles.
 */
data class RawContentPublishRow(
    val id: UUID,
    val externalId: String,
    val sourceId: UUID,
    val sourceType: String,
    val rawData: String,
    val downloadedMedia: String?,
    val receivedAt: LocalDateTime,
    val dedupArticleId: Long?,
    val dedupContentHash: String?,
    val cleanHtml: String?,
    val cleanText: String?,
    val previewText: String?,
    val cleanTextLength: Int?,
)
