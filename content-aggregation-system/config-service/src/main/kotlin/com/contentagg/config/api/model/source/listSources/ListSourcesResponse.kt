package com.contentagg.config.api.model.source.listSources
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import java.time.Instant

/**
 * Response item for listing sources.
 */
data class ListSourcesResponse(
    val id: String,
    val sourceType: SourceType,
    val name: String,
    val url: String?,
    val updateFrequencyMinutes: Int?,
    @JsonProperty("isActive") val isActive: Boolean?,
    val parameters: Map<String, String>?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
