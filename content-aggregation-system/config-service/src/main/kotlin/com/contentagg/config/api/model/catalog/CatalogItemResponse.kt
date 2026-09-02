package com.contentagg.config.api.model.catalog

import com.contentagg.config.enums.SourceType
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Single item in `GET /api/sources` catalog response (Phase 2).
 * Intentionally omits operational fields (is_active, update_frequency_minutes).
 */
data class CatalogItemResponse(
    val id: String,
    @JsonProperty("source_type") val sourceType: SourceType,
    val name: String,
    val url: String?,
    @JsonProperty("parameters_excerpt") val parametersExcerpt: Map<String, String>,
    @JsonProperty("created_at") val createdAt: Instant?,
)
