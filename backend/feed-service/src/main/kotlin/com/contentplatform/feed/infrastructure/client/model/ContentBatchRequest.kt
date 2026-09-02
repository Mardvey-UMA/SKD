package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ContentBatchRequest(
    val ids: List<String>,
    @JsonProperty("include_related") val includeRelated: Boolean = true,
    @JsonProperty("related_limit") val relatedLimit: Int = 5
)
