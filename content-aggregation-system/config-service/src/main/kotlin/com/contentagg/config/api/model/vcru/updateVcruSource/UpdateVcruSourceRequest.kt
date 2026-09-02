package com.contentagg.config.api.model.vcru.updateVcruSource
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

/**
 * Request for updating a VC.RU source.
 * Supported sorting values: new, hotness, day, week, month.
 */
data class UpdateVcruSourceRequest(
    @field:NotBlank(message = "Alias is required")
    val alias: String,

    @field:NotNull(message = "Source type is required")
    val sourceType: SourceType,

    @field:NotBlank(message = "Name is required")
    val name: String,

    @field:Min(value = 1, message = "Max articles must be at least 1")
    @field:Max(value = 500, message = "Max articles cannot exceed 500")
    val maxArticles: Int = 50,

    val parseImages: Boolean = true,

    @field:Pattern(
        regexp = "^(new|hotness|day|week|month)$",
        message = "Sorting must be one of: new, hotness, day, week, month"
    )
    val sorting: String = "new",

    @field:Min(value = 1, message = "Update frequency must be at least 1 minute")
    val updateFrequencyMinutes: Int = 60,

    @JsonProperty("isActive") val isActive: Boolean = true
)
