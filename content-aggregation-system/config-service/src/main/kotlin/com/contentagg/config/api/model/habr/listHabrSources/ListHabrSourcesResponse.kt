package com.contentagg.config.api.model.habr.listHabrSources
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import java.time.Instant

/**
 * Response item for listing Habr sources.
 */
data class ListHabrSourcesResponse(
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
