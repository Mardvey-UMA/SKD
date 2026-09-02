package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class SourceAdditionResponse(
    @JsonProperty("source_id") val sourceId: UUID,
    @JsonProperty("source_type") val sourceType: String,
    @JsonProperty("source_name") val sourceName: String,
    @JsonProperty("added_at") val addedAt: Instant
)
