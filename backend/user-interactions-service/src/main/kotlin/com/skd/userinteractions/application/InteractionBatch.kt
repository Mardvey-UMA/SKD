package com.skd.userinteractions.application

import com.fasterxml.jackson.annotation.JsonProperty
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import java.time.Instant
import java.util.UUID

data class InteractionBatch(
    @JsonProperty("event_type") val eventType: String = "user.interactions.batch",
    @JsonProperty("schema_version") val schemaVersion: Int = 2,
    @JsonProperty("user_id") val userId: UUID,
    val interactions: List<InteractionEvent>,
    @JsonProperty("batch_ts") val batchTs: Instant
)
