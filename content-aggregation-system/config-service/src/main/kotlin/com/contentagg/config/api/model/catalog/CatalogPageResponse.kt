package com.contentagg.config.api.model.catalog

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Page envelope for `GET /api/sources` (Phase 2 — cursor pagination).
 */
data class CatalogPageResponse(
    val items: List<CatalogItemResponse>,
    @JsonProperty("next_cursor") val nextCursor: String?,
    @JsonProperty("has_more") val hasMore: Boolean,
)
