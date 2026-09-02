package com.contentagg.config.api.model.telegram.createTelegramSource
import com.fasterxml.jackson.annotation.JsonProperty

import java.time.LocalDateTime
import java.util.UUID

data class CreateTelegramSourceResponse(
    val id: UUID?,
    val sourceType: String?,
    val name: String?,
    val url: String?,
    val updateFrequencyMinutes: Int?,
    @JsonProperty("isActive") val isActive: Boolean?,
    val parameters: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
