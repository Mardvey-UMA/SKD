package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class BlockedSourcesListItem(
    @JsonProperty("source_id") val sourceId: UUID,
    @JsonProperty("blocked_at") val blockedAt: String? = null
)

data class BlockedSourcesListResponse(
    val items: List<BlockedSourcesListItem> = emptyList(),
    val count: Int = 0
)
