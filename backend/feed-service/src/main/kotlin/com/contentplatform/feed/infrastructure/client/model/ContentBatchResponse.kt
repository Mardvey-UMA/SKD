package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ContentBatchResponse(
    val items: Map<String, ContentBatchItem> = emptyMap(),
    @JsonProperty("not_found") val notFound: List<String> = emptyList()
)
