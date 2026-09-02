package com.contentagg.aggregator.api.controller

import com.contentagg.aggregator.api.ContentApi
import com.contentagg.aggregator.api.model.content.ContentBatchRequest
import com.contentagg.aggregator.api.model.content.ContentBatchResponse
import com.contentagg.aggregator.api.model.content.ContentResponse
import com.contentagg.aggregator.api.model.content.ContentSearchRequest
import com.contentagg.aggregator.api.model.content.PageResponse
import com.contentagg.aggregator.db.repository.aggregatedcontent.model.SourceType
import com.contentagg.aggregator.processor.content.QueryContentProcessor
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/content")
class ContentController(
    private val queryContentProcessor: QueryContentProcessor
) : ContentApi {

    @GetMapping
    override fun searchContent(
        @RequestParam(required = false) sourceType: SourceType?,
        @RequestParam(required = false) sourceId: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) fromDate: String?,
        @RequestParam(required = false) toDate: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "publishedAt") sortBy: String,
        @RequestParam(defaultValue = "DESC") sortDirection: String
    ): ResponseEntity<PageResponse<ContentResponse>> {
        val request = ContentSearchRequest(
            sourceType = sourceType,
            sourceId = sourceId,
            fromDate = fromDate?.let { Instant.parse(it) },
            toDate = toDate?.let { Instant.parse(it) },
            search = search,
            page = page,
            size = size,
            sortBy = sortBy,
            sortDirection = sortDirection
        )
        return ResponseEntity.ok(queryContentProcessor.searchContent(request))
    }

    @GetMapping("/{id}")
    override fun getContentById(@PathVariable id: UUID): ResponseEntity<ContentResponse> =
        ResponseEntity.ok(queryContentProcessor.getContentById(id))

    @PostMapping("/batch")
    override fun getContentBatch(@Valid @RequestBody request: ContentBatchRequest): ResponseEntity<ContentBatchResponse> =
        ResponseEntity.ok(queryContentProcessor.getContentBatch(request))

    @GetMapping("/latest")
    override fun getLatest(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ContentResponse>> =
        ResponseEntity.ok(queryContentProcessor.getLatestContent(page, size))

    @GetMapping("/by-type/{sourceType}")
    override fun getBySourceType(
        @PathVariable sourceType: SourceType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ContentResponse>> =
        ResponseEntity.ok(queryContentProcessor.getContentBySourceType(sourceType, page, size))
}
