package com.contentagg.config.api.model.habr.updateHabrSource
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import java.time.Instant

/**
 * Response after updating a Habr source.
 */
data class UpdateHabrSourceResponse(
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
