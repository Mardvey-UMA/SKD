package com.contentplatform.feed.infrastructure.kafka

import com.fasterxml.jackson.annotation.JsonProperty

data class RecommendationsUpdatedEvent(
    @JsonProperty("event_type") val eventType: String?,
    @JsonProperty("user_id") val userId: String?,
    val reason: String?,
    val timestamp: String?
)
