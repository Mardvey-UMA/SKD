package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class BlockedSourceRequest(
    @JsonProperty("source_id") val sourceId: UUID?
)
