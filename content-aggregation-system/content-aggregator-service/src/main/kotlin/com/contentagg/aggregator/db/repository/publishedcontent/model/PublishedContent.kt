package com.contentagg.aggregator.db.repository.publishedcontent.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("published_content")
data class PublishedContent(
    @Id
    val id: UUID? = null,

    @Column("content_id")
    val contentId: UUID,

    @Column("external_id")
    val externalId: String,

    val title: String? = null,

    val description: String? = null,

    val content: String? = null,

    @Column("content_format")
    val contentFormat: String = "HTML",

    @Column("source_id")
    val sourceId: UUID,

    @Column("source_type")
    val sourceType: String,

    @Column("source_subtype")
    val sourceSubtype: String? = null,

    val url: String? = null,

    @Column("published_at")
    val publishedAt: OffsetDateTime? = null,

    @Column("author_id")
    val authorId: String? = null,

    @Column("author_name")
    val authorName: String? = null,

    val media: String? = null,       // JSONB as String

    val metadata: String? = null,    // JSONB as String

    @Column("dedup_article_id")
    val dedupArticleId: Long? = null,

    @Column("content_hash")
    val contentHash: String? = null,

    @Column("content_html")
    val contentHtml: String? = null,

    @Column("content_text")
    val contentText: String? = null,

    @Column("preview_text")
    val previewText: String? = null,

    @Column("content_text_length")
    val contentTextLength: Int? = null,

    @Column("created_at")
    val createdAt: OffsetDateTime? = null,

    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null,
)
