package com.contentagg.config.processor.vcru

import com.contentagg.config.api.model.vcru.getVcruSource.GetVcruSourceResponse
import com.contentagg.config.api.model.vcru.listVcruSources.ListVcruSourcesResponse
import com.contentagg.config.db.repository.source.model.dto.SourceResponse
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for reading VC.RU source configurations.
 * Validates VC.RU-specific constraints and delegates to SourceService.
 */
@Component
class GetVcruSourceProcessor(
    private val sourceService: SourceService
) {

    companion object {
        private val log = LoggerFactory.getLogger(GetVcruSourceProcessor::class.java)
    }

    fun getById(id: UUID): GetVcruSourceResponse {
        MDC.put("operation", "getVcruSourceById")
        try {
            log.debug("Getting VC.RU source by id: {}", id)

            val source = sourceService.findById(id)

            if (source.sourceType != SourceType.VCRU) {
                throw InvalidSourceException(
                    "Source $id is not a VC.RU source (type: ${source.sourceType})"
                )
            }

            return GetVcruSourceResponse(
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

    fun listAll(): List<ListVcruSourcesResponse> {
        MDC.put("operation", "listAllVcruSources")
        try {
            log.debug("Listing all VC.RU sources")
            return sourceService.findByType(SourceType.VCRU).map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    fun getSupportedTypes(): List<SourceType> = listOf(SourceType.VCRU)

    private fun toListResponse(s: SourceResponse) = ListVcruSourcesResponse(
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
