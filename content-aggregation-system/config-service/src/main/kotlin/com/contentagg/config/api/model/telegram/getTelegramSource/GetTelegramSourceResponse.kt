package com.contentagg.config.api.model.telegram.getTelegramSource
import com.fasterxml.jackson.annotation.JsonProperty

import java.time.LocalDateTime
import java.util.UUID

data class GetTelegramSourceResponse(
    val id: UUID?,
    val sourceType: String?,
    val name: String?,
    val url: String?,
    val channelUsername: String?,
    val downloadMedia: Boolean?,
    val maxMessages: Int?,
    val maxMediaSizeMb: Int?,
    val batchSize: Int?,
    val updateFrequencyMinutes: Int?,
    @JsonProperty("isActive") val isActive: Boolean?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
