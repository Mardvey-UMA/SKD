package com.contentagg.config.api.model.telegram.createTelegramSource
import com.fasterxml.jackson.annotation.JsonProperty

import com.contentagg.config.enums.SourceType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateTelegramSourceRequest(
    @field:NotNull(message = "Source type must not be null")
    val sourceType: SourceType = SourceType.TELEGRAM,

    @field:NotBlank(message = "Name must not be blank")
    val name: String,

    @field:NotBlank(message = "Channel username must not be blank")
    val channelUsername: String,

    val downloadMedia: Boolean? = true,
    val maxMessages: Int? = 100,
    val maxMediaSizeMb: Int? = 50,
    val batchSize: Int? = 50,

    @field:Positive(message = "Update frequency must be positive")
    val updateFrequencyMinutes: Int = 5,

    @JsonProperty("isActive") val isActive: Boolean = true,
)
