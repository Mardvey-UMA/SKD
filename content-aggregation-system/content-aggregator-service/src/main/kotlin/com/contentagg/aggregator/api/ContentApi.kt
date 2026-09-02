package com.contentagg.aggregator.api

import com.contentagg.aggregator.api.model.content.ContentBatchRequest
import com.contentagg.aggregator.api.model.content.ContentBatchResponse
import com.contentagg.aggregator.api.model.content.ContentResponse
import com.contentagg.aggregator.api.model.content.PageResponse
import com.contentagg.aggregator.db.repository.aggregatedcontent.model.SourceType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@Tag(name = "Content", description = "Published content API")
interface ContentApi {

    @Operation(summary = "Search content", description = "Search content with filters and pagination")
    fun searchContent(
        @Parameter(description = "Source type filter") @RequestParam(required = false) sourceType: SourceType?,
        @Parameter(description = "Source ID filter") @RequestParam(required = false) sourceId: String?,
        @Parameter(description = "Search in title and description") @RequestParam(required = false) search: String?,
        @Parameter(description = "From date (ISO format)") @RequestParam(required = false) fromDate: String?,
        @Parameter(description = "To date (ISO format)") @RequestParam(required = false) toDate: String?,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "Sort field") @RequestParam(defaultValue = "publishedAt") sortBy: String,
        @Parameter(description = "Sort direction (ASC/DESC)") @RequestParam(defaultValue = "DESC") sortDirection: String
    ): ResponseEntity<PageResponse<ContentResponse>>

    @Operation(summary = "Get content by ID", description = "Retrieve specific content item by ID")
    fun getContentById(
        @Parameter(description = "Content ID") @PathVariable id: UUID
    ): ResponseEntity<ContentResponse>

    @Operation(summary = "Get content batch", description = "Retrieve content items by list of IDs with optional related content")
    fun getContentBatch(
        @Parameter(description = "Batch request with IDs and options") @Valid @RequestBody request: ContentBatchRequest
    ): ResponseEntity<ContentBatchResponse>

    @Operation(summary = "Get latest content", description = "Retrieve most recent content")
    fun getLatest(
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ContentResponse>>

    @Operation(summary = "Get content by source type", description = "Retrieve content filtered by source type")
    fun getBySourceType(
        @Parameter(description = "Source type") @PathVariable sourceType: SourceType,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ContentResponse>>
}
