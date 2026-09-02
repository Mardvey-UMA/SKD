package com.contentagg.config.api.model.telegram.listTelegramSources
import com.fasterxml.jackson.annotation.JsonProperty

import java.time.LocalDateTime
import java.util.UUID

data class ListTelegramSourcesResponse(
    val id: UUID?,
    val name: String?,
    val channelUsername: String?,
    @JsonProperty("isActive") val isActive: Boolean?,
    val createdAt: LocalDateTime?,
)
