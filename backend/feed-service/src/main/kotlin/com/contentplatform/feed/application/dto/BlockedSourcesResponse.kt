package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class BlockedSourceItem(
    @JsonProperty("source_id") val sourceId: UUID,
    @JsonProperty("blocked_at") val blockedAt: String?,
    @JsonProperty("source_type") val sourceType: String? = null,
    @JsonProperty("source_name") val sourceName: String? = null
)

data class BlockedSourcesResponse(
    val items: List<BlockedSourceItem>,
    val count: Int
)
