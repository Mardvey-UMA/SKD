package com.contentagg.config.db.repository.source.model.dto
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import java.time.Instant

/**
 * Internal DTO for source query results at the DB service layer.
 */
data class SourceResponse(
    val id: String,
    val sourceType: SourceType,
    val name: String,
    val url: String?,
    val updateFrequencyMinutes: Int?,
    @JsonProperty("isActive") val isActive: Boolean?,
    val parameters: Map<String, String>,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
