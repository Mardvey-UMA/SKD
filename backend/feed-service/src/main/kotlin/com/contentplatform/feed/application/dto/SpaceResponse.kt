package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class SpaceResponse(
    val id: UUID,
    val name: String,
    val color: String,
    @JsonProperty("source_ids") val sourceIds: List<UUID>,
    @JsonProperty("source_count") val sourceCount: Int,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant
)
