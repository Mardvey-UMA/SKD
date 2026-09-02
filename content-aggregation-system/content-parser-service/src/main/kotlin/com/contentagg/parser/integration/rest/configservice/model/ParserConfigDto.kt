package com.contentagg.parser.integration.rest.configservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class ParserConfigDto(
    @JsonProperty("config_id") val configId: String?,
    @JsonProperty("source_type") val sourceType: String?,
    @JsonProperty("enabled") val enabled: Boolean?,
    @JsonProperty("habr_config") val habrConfig: HabrConfigDto?,
    @JsonProperty("created_at") val createdAt: LocalDateTime?,
    @JsonProperty("updated_at") val updatedAt: LocalDateTime?,
)
