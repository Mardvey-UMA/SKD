package com.contentagg.config.processor.catalog

import com.contentagg.config.api.model.catalog.CatalogPageResponse
import com.contentagg.config.db.service.source.CatalogCursor
import com.contentagg.config.db.service.source.SourceCatalogCacheService
import com.contentagg.config.db.service.source.SourceCatalogService
import com.contentagg.config.enums.SourceType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Orchestrates the public catalog read path (Phase 2):
 *   cache HIT → return cached page
 *   cache MISS → DB query via SourceCatalogService → store in cache → return
 */
@Component
class ListCatalogProcessor(
    private val catalogService: SourceCatalogService,
    private val cacheService: SourceCatalogCacheService,
) {

    companion object {
        private val log = LoggerFactory.getLogger(ListCatalogProcessor::class.java)
    }

    fun process(
        type: SourceType?,
        q: String?,
        cursor: String?,
        pageSize: Int,
    ): CatalogPageResponse {
        val decodedCursor = cursor?.let { CatalogCursor.decode(it) }
        val version = cacheService.currentVersion()
        val cacheKey = cacheService.buildKey(type, q?.trim()?.takeIf { it.isNotEmpty() }, cursor, pageSize, version)

        cacheService.get(cacheKey)?.let {
            log.debug("Catalog cache HIT key={}", cacheKey)
            return it
        }
        log.debug("Catalog cache MISS key={}", cacheKey)

        val page = catalogService.findCatalogPage(type, q, decodedCursor, pageSize)
        cacheService.put(cacheKey, page)
        return page
    }
}
