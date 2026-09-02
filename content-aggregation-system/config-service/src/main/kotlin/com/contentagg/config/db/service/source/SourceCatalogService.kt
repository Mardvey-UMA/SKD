package com.contentagg.config.db.service.source

import com.contentagg.config.api.model.catalog.CatalogItemResponse
import com.contentagg.config.api.model.catalog.CatalogPageResponse
import com.contentagg.config.configuration.properties.CatalogProperties
import com.contentagg.config.db.repository.source.SourceRepository
import com.contentagg.config.db.repository.source.model.Source
import com.contentagg.config.enums.SourceType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId

/**
 * Read-only service for the public source catalog (Phase 2).
 * Keyset pagination via SourceRepository.findCatalogPage().
 */
@Service
class SourceCatalogService(
    private val repository: SourceRepository,
    private val jsonConversionService: JsonConversionService,
    private val catalogProperties: CatalogProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceCatalogService::class.java)
    }

    @Transactional(readOnly = true)
    fun findCatalogPage(
        type: SourceType?,
        q: String?,
        cursor: CatalogCursor?,
        pageSize: Int,
    ): CatalogPageResponse {
        val effectivePageSize = pageSize.coerceIn(1, catalogProperties.maxPageSize)
        val fetchSize = effectivePageSize + 1
        val normalizedQ = q?.trim()?.takeIf { it.isNotEmpty() }
        log.debug(
            "findCatalogPage type={} q={} cursorAt={} cursorId={} pageSize={}",
            type, normalizedQ, cursor?.createdAt, cursor?.id, effectivePageSize,
        )

        val rows = repository.findCatalogPage(
            type = type?.name,
            q = normalizedQ,
            cursorCreatedAt = cursor?.createdAt,
            cursorId = cursor?.id,
            pageSize = fetchSize,
        )

        val hasMore = rows.size > effectivePageSize
        val kept = if (hasMore) rows.take(effectivePageSize) else rows

        val items = kept.map { row -> toCatalogItem(row) }

        val nextCursor = if (hasMore) {
            val last = kept.last()
            if (last.createdAt != null && last.id != null) {
                CatalogCursor.encode(CatalogCursor(last.createdAt, last.id))
            } else null
        } else null

        return CatalogPageResponse(
            items = items,
            nextCursor = nextCursor,
            hasMore = hasMore,
        )
    }

    private fun toCatalogItem(source: Source): CatalogItemResponse {
        val parameters = jsonConversionService.fromJson(source.parameters)
        val type = SourceType.valueOf(source.sourceType)
        val excerpt = excerptParameters(type, parameters)
        return CatalogItemResponse(
            id = source.id.toString(),
            sourceType = type,
            name = source.name,
            url = source.url,
            parametersExcerpt = excerpt,
            createdAt = source.createdAt?.atZone(ZoneId.of("UTC"))?.toInstant(),
        )
    }

    private fun excerptParameters(type: SourceType, parameters: Map<String, String>): Map<String, String> {
        val keys: List<String> = when (type) {
            SourceType.HABR -> listOf("companyAlias", "userLogin", "hubAlias", "alias")
            SourceType.VCRU -> listOf("alias")
            SourceType.TELEGRAM -> listOf("channelUsername")
            SourceType.RSS -> emptyList()
        }
        if (keys.isEmpty()) return emptyMap()
        return parameters.filterKeys { it in keys }
    }
}
