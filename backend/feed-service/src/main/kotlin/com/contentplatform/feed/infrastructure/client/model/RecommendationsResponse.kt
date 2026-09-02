package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class RecommendationsResponse(
    @JsonProperty("user_id") val userId: UUID,
    val items: List<UUID>,
    val count: Int,
    @JsonProperty("generated_at") val generatedAt: String?
)
