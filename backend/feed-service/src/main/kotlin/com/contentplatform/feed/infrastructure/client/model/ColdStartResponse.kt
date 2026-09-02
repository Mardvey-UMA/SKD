package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class ColdStartResponse(
    val items: List<UUID>,
    val count: Int,
    @JsonProperty("generated_at") val generatedAt: String?
)
