package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class UpdateSpaceRequest(
    val name: String? = null,
    val color: String? = null,
    @JsonProperty("source_ids") val sourceIds: List<UUID>? = null
)
