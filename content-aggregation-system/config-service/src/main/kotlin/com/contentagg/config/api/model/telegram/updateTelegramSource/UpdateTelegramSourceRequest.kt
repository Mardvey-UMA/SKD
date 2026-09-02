package com.contentagg.config.api.model.telegram.updateTelegramSource
import com.fasterxml.jackson.annotation.JsonProperty

import jakarta.validation.constraints.NotBlank

data class UpdateTelegramSourceRequest(
    @field:NotBlank(message = "Name must not be blank")
    val name: String,

    val channelUsername: String?,
    val downloadMedia: Boolean?,
    val maxMessages: Int?,
    val maxMediaSizeMb: Int?,
    val batchSize: Int?,
    val updateFrequencyMinutes: Int?,
    @JsonProperty("isActive") val isActive: Boolean?,
)
