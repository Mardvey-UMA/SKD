package com.contentagg.config.processor.source

import com.contentagg.config.api.model.source.getSource.GetSourceResponse
import com.contentagg.config.api.model.source.listSources.ListSourcesResponse
import com.contentagg.config.db.repository.source.model.dto.SourceResponse
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for reading source configurations.
 * Delegates to SourceService for DB queries.
 */
@Component
class GetSourceProcessor(
    private val sourceService: SourceService
) {

    companion object {
        private val log = LoggerFactory.getLogger(GetSourceProcessor::class.java)
    }

    fun getById(id: UUID): GetSourceResponse {
        MDC.put("operation", "getSourceById")
        try {
            log.debug("Getting source by id: {}", id)
            val source = sourceService.findById(id)
            return toGetResponse(source)
        } finally {
            MDC.clear()
        }
    }

    fun getAll(activeOnly: Boolean): List<ListSourcesResponse> {
        MDC.put("operation", "getAllSources")
        try {
            log.debug("Getting all sources, activeOnly={}", activeOnly)
            val sources = if (activeOnly) sourceService.findActive() else sourceService.findAll()
            return sources.map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    fun getByType(type: SourceType, activeOnly: Boolean): List<ListSourcesResponse> {
        MDC.put("operation", "getSourcesByType")
        try {
            log.debug("Getting sources by type={}, activeOnly={}", type, activeOnly)
            val sources = if (activeOnly) sourceService.findActiveByType(type) else sourceService.findByType(type)
            return sources.map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    fun getNeedingUpdate(): List<ListSourcesResponse> {
        MDC.put("operation", "getSourcesNeedingUpdate")
        try {
            log.debug("Getting sources needing update")
            return sourceService.findNeedingUpdate().map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    private fun toGetResponse(s: SourceResponse) = GetSourceResponse(
        id = s.id,
        sourceType = s.sourceType,
        name = s.name,
        url = s.url,
        updateFrequencyMinutes = s.updateFrequencyMinutes,
        isActive = s.isActive,
        parameters = s.parameters,
        createdAt = s.createdAt,
        updatedAt = s.updatedAt
    )

    private fun toListResponse(s: SourceResponse) = ListSourcesResponse(
        id = s.id,
        sourceType = s.sourceType,
        name = s.name,
        url = s.url,
        updateFrequencyMinutes = s.updateFrequencyMinutes,
        isActive = s.isActive,
        parameters = s.parameters,
        createdAt = s.createdAt,
        updatedAt = s.updatedAt
    )
}
