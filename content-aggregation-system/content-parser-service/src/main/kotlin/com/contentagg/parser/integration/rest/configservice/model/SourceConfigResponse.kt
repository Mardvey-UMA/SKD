package com.contentagg.parser.integration.rest.configservice.model

import com.contentagg.parser.db.service.util.SourceType
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class SourceConfigResponse(
    @JsonProperty("id") val id: String?,
    @JsonProperty("sourceType") val sourceType: SourceType?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("updateFrequencyMinutes") val updateFrequencyMinutes: Int?,
    @JsonProperty("isActive") val isActive: Boolean?,
    @JsonProperty("parameters") val parameters: Map<String, Any>?,
    @JsonProperty("createdAt") val createdAt: Instant?,
    @JsonProperty("updatedAt") val updatedAt: Instant?,
) {
    fun getParameter(key: String, defaultValue: String? = null): String? =
        parameters?.get(key)?.toString() ?: defaultValue

    fun getIntParameter(key: String, defaultValue: Int? = null): Int? {
        val value = parameters?.get(key) ?: return defaultValue
        return if (value is Number) value.toInt() else defaultValue
    }
}
