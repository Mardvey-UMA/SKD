package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class SourceAdditionsCountResponse(
    @JsonProperty("user_id") val userId: UUID,
    val count: Int
)
