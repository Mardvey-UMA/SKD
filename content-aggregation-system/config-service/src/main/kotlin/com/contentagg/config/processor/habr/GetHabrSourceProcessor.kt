package com.contentagg.config.processor.habr

import com.contentagg.config.api.model.habr.getHabrSource.GetHabrSourceResponse
import com.contentagg.config.api.model.habr.listHabrSources.ListHabrSourcesResponse
import com.contentagg.config.db.repository.source.model.dto.SourceResponse
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for reading Habr source configurations.
 * Validates Habr-specific constraints and delegates to SourceService.
 */
@Component
class GetHabrSourceProcessor(
    private val sourceService: SourceService
) {

    companion object {
        private val log = LoggerFactory.getLogger(GetHabrSourceProcessor::class.java)
    }

    fun getById(id: UUID): GetHabrSourceResponse {
        MDC.put("operation", "getHabrSourceById")
        try {
            log.debug("Getting Habr source by id: {}", id)

            val source = sourceService.findById(id)

            if (!isHabrSourceType(source.sourceType)) {
                throw InvalidSourceException(
                    "Source $id is not a Habr source (type: ${source.sourceType})"
                )
            }

            return GetHabrSourceResponse(
                id = source.id,
                sourceType = source.sourceType,
                name = source.name,
                url = source.url,
                updateFrequencyMinutes = source.updateFrequencyMinutes,
                isActive = source.isActive,
                parameters = source.parameters,
                createdAt = source.createdAt,
                updatedAt = source.updatedAt
            )
        } finally {
            MDC.clear()
        }
    }

    fun listAll(): List<ListHabrSourcesResponse> {
        MDC.put("operation", "listAllHabrSources")
        try {
            log.debug("Listing all Habr sources")
            return sourceService.findByType(SourceType.HABR).map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    fun listByType(sourceType: SourceType): List<ListHabrSourcesResponse> {
        MDC.put("operation", "listHabrSourcesByType")
        try {
            log.debug("Listing Habr sources by type: {}", sourceType)

            if (!isHabrSourceType(sourceType)) {
                throw InvalidSourceException("Invalid Habr source type: $sourceType")
            }

            return sourceService.findByType(sourceType).map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    fun getSupportedTypes(): List<SourceType> = listOf(SourceType.HABR)

    private fun isHabrSourceType(sourceType: SourceType) = sourceType == SourceType.HABR

    private fun toListResponse(s: SourceResponse) = ListHabrSourcesResponse(
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
