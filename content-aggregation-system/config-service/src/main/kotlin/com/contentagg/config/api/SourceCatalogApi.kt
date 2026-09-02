package com.contentagg.config.api

import com.contentagg.config.api.model.catalog.CatalogPageResponse
import com.contentagg.config.enums.SourceType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Public source catalog API (Phase 2) — `GET /api/sources`.
 * Accessible to any authenticated user (not only SUBSCRIBER — premium gate lives on CRUD).
 */
@Tag(name = "Sources Catalog", description = "Public source catalog API")
@RequestMapping("/api/sources")
interface SourceCatalogApi {

    @Operation(summary = "List sources (public catalog with search + cursor pagination)")
    @GetMapping
    fun listCatalog(
        @Parameter(description = "Filter by source type (HABR|VCRU|TELEGRAM|RSS)")
        @RequestParam(required = false) type: SourceType?,

        @Parameter(description = "Search query — case-insensitive substring match on name")
        @RequestParam(required = false) q: String?,

        @Parameter(description = "Opaque cursor from previous page's next_cursor")
        @RequestParam(required = false) cursor: String?,

        @Parameter(description = "Page size (1-50, default 20)")
        @RequestParam(required = false, defaultValue = "20") limit: Int,
    ): ResponseEntity<CatalogPageResponse>
}
