package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class CreateSpaceRequest(
    val name: String?,
    val color: String?,
    @JsonProperty("source_ids") val sourceIds: List<UUID>? = null
)
