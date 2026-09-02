package com.contentagg.config.api.model.habr.updateHabrSource
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * Request for updating a Habr source.
 */
data class UpdateHabrSourceRequest(
    @field:NotBlank(message = "Alias is required")
    val alias: String,

    @field:NotNull(message = "Source type is required")
    val sourceType: SourceType,

    @field:NotBlank(message = "Name is required")
    val name: String,

    val parseComments: Boolean = false,

    val parseImages: Boolean = true,

    @field:Min(value = 1, message = "Max articles must be at least 1")
    @field:Max(value = 1000, message = "Max articles cannot exceed 1000")
    val maxArticles: Int = 100,

    val minRating: Int? = null,

    val hubsFilter: List<String>? = null,

    val tagsFilter: List<String>? = null,

    @field:Min(value = 1, message = "Update frequency must be at least 1 minute")
    val updateFrequencyMinutes: Int = 60,

    @JsonProperty("isActive") val isActive: Boolean = true
) {
    init {
        if (sourceType != SourceType.HABR) {
            throw IllegalArgumentException("Source type must be HABR")
        }
    }
}
