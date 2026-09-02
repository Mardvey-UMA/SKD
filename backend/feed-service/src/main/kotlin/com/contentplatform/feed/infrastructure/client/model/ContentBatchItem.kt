package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ContentBatchItem(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val content: String? = null,
    @JsonProperty("content_html") val contentHtml: String? = null,
    @JsonProperty("content_text") val contentText: String? = null,
    @JsonProperty("preview_text") val previewText: String? = null,
    @JsonProperty("content_format") val contentFormat: String? = null,
    @JsonProperty("source_id") val sourceId: String? = null,
    @JsonProperty("source_name") val sourceName: String? = null,
    @JsonProperty("source_type") val sourceType: String? = null,
    @JsonProperty("source_subtype") val sourceSubtype: String? = null,
    val url: String? = null,
    @JsonProperty("published_at") val publishedAt: String? = null,
    @JsonProperty("author_name") val authorName: String? = null,
    val media: List<Map<String, Any>>? = null,
    val metadata: Map<String, Any>? = null,
    @JsonProperty("related_ids") val relatedIds: List<String>? = null
)
