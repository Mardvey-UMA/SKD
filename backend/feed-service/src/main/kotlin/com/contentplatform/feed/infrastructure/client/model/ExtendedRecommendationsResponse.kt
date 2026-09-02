package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtendedRecommendationsResponse(
    @JsonProperty("user_id") val userId: UUID,
    val items: List<UUID>,
    val count: Int,
    @JsonProperty("generated_at") val generatedAt: String? = null,
    @JsonProperty("items_detailed") val itemsDetailed: List<FeedItemDetail>? = null,
    @JsonProperty("latency_breakdown") val latencyBreakdown: Map<String, Long>? = null,
    @JsonProperty("profile_snapshot") val profileSnapshot: Map<String, Any>? = null,
    @JsonProperty("feature_flags") val featureFlags: Map<String, Any>? = null
)
