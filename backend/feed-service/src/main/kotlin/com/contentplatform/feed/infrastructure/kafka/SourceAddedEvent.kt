package com.contentplatform.feed.infrastructure.kafka

import com.fasterxml.jackson.annotation.JsonProperty

data class SourceAddedEvent(
    @JsonProperty("event_id") val eventId: String? = null,
    @JsonProperty("event_type") val eventType: String? = null,
    @JsonProperty("source_id") val sourceId: String?,
    @JsonProperty("user_id") val userId: String?,
    @JsonProperty("source_type") val sourceType: String?,
    val name: String?,
    @JsonProperty("added_at") val addedAt: String? = null
)
