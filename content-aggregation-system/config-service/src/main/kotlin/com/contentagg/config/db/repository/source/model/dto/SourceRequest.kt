package com.contentagg.config.db.repository.source.model.dto
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * Internal DTO for source create/update operations at the DB service layer.
 */
data class SourceRequest(
    @field:NotNull(message = "Source type is required")
    val sourceType: SourceType,

    @field:NotBlank(message = "Name is required")
    val name: String,

    val url: String?,

    @field:Min(value = 1, message = "Update frequency must be at least 1 minute")
    val updateFrequencyMinutes: Int? = 60,

    @field:NotNull(message = "Active status is required")
    @JsonProperty("isActive") val isActive: Boolean,

    @field:NotNull(message = "Parameters are required")
    val parameters: Map<String, String>
)
