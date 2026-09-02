package com.contentagg.parser.db.repository.publishedcontent.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("published_content")
data class PublishedContent(
    @Id val id: UUID? = null,
    val contentId: UUID,
    val externalId: String,
    val title: String?,
    val description: String?,
    val content: String?,
    val contentFormat: String = "HTML",
    val sourceId: UUID,
    val sourceType: String,
    val sourceSubtype: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val authorId: String?,
    val authorName: String?,
    val media: String?,
    val metadata: String?,
    val dedupArticleId: Long?,
    val contentHash: String?,
    val contentHtml: String? = null,
    val contentText: String? = null,
    val previewText: String? = null,
    val contentTextLength: Int? = null,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
)
