package com.contentagg.config.api.model.vcru.listVcruSources
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import java.time.Instant

/**
 * Response item for listing VC.RU sources.
 */
data class ListVcruSourcesResponse(
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
