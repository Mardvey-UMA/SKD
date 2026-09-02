package com.contentagg.config.api.model.habr.getHabrSource
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import java.time.Instant

/**
 * Response for getting a single Habr source.
 */
data class GetHabrSourceResponse(
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
