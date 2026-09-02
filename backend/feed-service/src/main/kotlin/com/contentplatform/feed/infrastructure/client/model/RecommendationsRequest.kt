package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecommendationsRequest(
    @JsonProperty("user_id") val userId: UUID,
    val count: Int,
    @JsonProperty("include_source_ids") val includeSourceIds: List<UUID>? = null,
    @JsonProperty("exclude_source_ids") val excludeSourceIds: List<UUID>? = null,
    @JsonProperty("ranking_mode") val rankingMode: String? = null
)
