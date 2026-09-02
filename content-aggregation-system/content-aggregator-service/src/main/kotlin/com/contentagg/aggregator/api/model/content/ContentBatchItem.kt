package com.contentagg.aggregator.api.model.content

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ContentBatchItem(
    @JsonProperty("id")
    val id: String,

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
    val sourceType: String,

    @JsonProperty("source_subtype")
    val sourceSubtype: String?,

    @JsonProperty("url")
    val url: String?,

    @JsonProperty("published_at")
    val publishedAt: Instant?,

    @JsonProperty("author_name")
    val authorName: String?,

    @JsonProperty("media")
    val media: List<Map<String, Any>>,

    @JsonProperty("metadata")
    val metadata: Map<String, Any>,

    @JsonProperty("related_ids")
    val relatedIds: List<String>?
)
