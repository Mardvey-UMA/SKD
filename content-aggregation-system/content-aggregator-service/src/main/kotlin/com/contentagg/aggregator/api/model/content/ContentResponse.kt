package com.contentagg.aggregator.api.model.content

import com.contentagg.aggregator.db.repository.aggregatedcontent.model.SourceType
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class ContentResponse(
    @JsonProperty("id")
    val id: String,

    @JsonProperty("content_id")
    val contentId: UUID,

    @JsonProperty("external_id")
    val externalId: String,

    @JsonProperty("title")
    val title: String?,

    @JsonProperty("description")
    val description: String?,

    @JsonProperty("content")
    val content: String?,

    @JsonProperty("content_html")
    val contentHtml: String?,

    @JsonProperty("content_text")
    val contentText: String?,

    @JsonProperty("preview_text")
    val previewText: String?,

    @JsonProperty("content_format")
    val contentFormat: String,

    @JsonProperty("source_id")
    val sourceId: String,

    @JsonProperty("source_type")
    val sourceType: SourceType,

    @JsonProperty("source_subtype")
    val sourceSubtype: String?,

    @JsonProperty("url")
    val url: String?,

    @JsonProperty("published_at")
    val publishedAt: Instant?,

    @JsonProperty("author_id")
    val authorId: String?,

    @JsonProperty("author_name")
    val authorName: String?,

    @JsonProperty("media")
    val media: List<Map<String, Any>>,

    @JsonProperty("metadata")
    val metadata: Map<String, Any>,

    @JsonProperty("dedup_article_id")
    val dedupArticleId: Long?,

    @JsonProperty("content_hash")
    val contentHash: String?,

    @JsonProperty("created_at")
    val createdAt: Instant?,

    @JsonProperty("updated_at")
    val updatedAt: Instant?
)
