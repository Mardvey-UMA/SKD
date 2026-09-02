package com.contentagg.config.api.controller

import com.contentagg.config.api.SourceCatalogApi
import com.contentagg.config.api.model.catalog.CatalogPageResponse
import com.contentagg.config.configuration.properties.CatalogProperties
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidCursorException
import com.contentagg.config.processor.catalog.ListCatalogProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for the public source catalog (Phase 2).
 */
@RestController
class SourceCatalogController(
    private val listCatalogProcessor: ListCatalogProcessor,
    private val catalogProperties: CatalogProperties,
) : SourceCatalogApi {

    companion object {
        private val log = LoggerFactory.getLogger(SourceCatalogController::class.java)
    }

    override fun listCatalog(
        type: SourceType?,
        q: String?,
        cursor: String?,
        limit: Int,
    ): ResponseEntity<CatalogPageResponse> {
        log.debug("GET /api/sources type={} q={} cursor={} limit={}", type, q, cursor, limit)

        if (limit < 1 || limit > catalogProperties.maxPageSize) {
            throw InvalidCursorException("limit out of range (1..${catalogProperties.maxPageSize})")
        }
        val trimmedQ = q?.let { if (it.length > 100) throw InvalidCursorException("q too long") else it }

        val page = listCatalogProcessor.process(type, trimmedQ, cursor, limit)
        return ResponseEntity.ok(page)
    }
}
