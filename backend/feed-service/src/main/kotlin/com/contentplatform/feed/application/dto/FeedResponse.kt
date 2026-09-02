package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.contentplatform.feed.infrastructure.client.model.ContentBatchItem
import com.contentplatform.feed.infrastructure.client.model.FeedItemDetail

data class FeedResponse(
    val items: List<ContentBatchItem>,
    val cursor: String?,
    val hasNext: Boolean,
    val message: String? = null,
    @JsonIgnore val source: String = "personalized",
    @JsonIgnore val latencyMs: Int? = null,
    @JsonIgnore val latencyBreakdown: Map<String, Long>? = null,
    @JsonIgnore val itemsDetailed: List<FeedItemDetail>? = null,
    @JsonIgnore val profileSnapshot: Map<String, Any>? = null,
    @JsonIgnore val featureFlags: Map<String, Any>? = null,
    @JsonIgnore val countRequested: Int? = null
)
