package com.skd.userinteractions.api.model.interactions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InteractionEvent(
    @JsonProperty("content_id") val contentId: String,
    @JsonProperty("action_type") val actionType: String,
    @JsonProperty("duration_sec") val durationSec: Int?,
    val timestamp: Instant,
    @JsonProperty("feed_request_id") val feedRequestId: String? = null,
    @JsonProperty("position_in_feed") val positionInFeed: Int? = null,
    @JsonProperty("device_type") val deviceType: String? = null,
    @JsonProperty("app_version") val appVersion: String? = null,
    @JsonProperty("ab_bucket") val abBucket: Int? = null,
    @JsonProperty("scroll_depth") val scrollDepth: Double? = null,
    val metadata: Map<String, Any?>? = null
)
