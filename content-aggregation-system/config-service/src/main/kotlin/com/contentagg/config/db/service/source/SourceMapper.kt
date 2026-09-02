package com.contentagg.config.db.service.source

import com.contentagg.config.db.repository.source.model.Source
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.repository.source.model.dto.SourceResponse
import com.contentagg.config.enums.SourceType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mapper for converting between Source entity and DTOs.
 * Works with the immutable Source data class — produces new instances for updates.
 */
@Component
class SourceMapper(
    private val jsonConversionService: JsonConversionService
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceMapper::class.java)
    }

    /**
     * Convert SourceRequest to a new (unsaved) Source entity.
     */
    fun toEntity(request: SourceRequest): Source = Source.newSource(
        sourceType = request.sourceType.name,
        name = request.name,
        url = request.url,
        updateFrequencyMinutes = request.updateFrequencyMinutes,
        isActive = request.isActive,
        parameters = jsonConversionService.toJson(request.parameters)
    )

    /**
     * Produce an updated Source data class from an existing entity and new request data.
     * Preserves id and audit timestamps from the existing entity.
     */
    fun withUpdated(existing: Source, request: SourceRequest): Source = existing.copy(
        sourceType = request.sourceType.name,
        name = request.name,
        url = request.url,
        updateFrequencyMinutes = request.updateFrequencyMinutes,
        isActive = request.isActive,
        parameters = jsonConversionService.toJson(request.parameters)
    )

    /**
     * Convert Source entity to SourceResponse DTO.
     */
    fun toResponse(source: Source): SourceResponse = SourceResponse(
        id = source.id.toString(),
        sourceType = SourceType.valueOf(source.sourceType),
        name = source.name,
        url = source.url,
        updateFrequencyMinutes = source.updateFrequencyMinutes,
        isActive = source.isActive,
        parameters = jsonConversionService.fromJson(source.parameters),
        createdAt = toInstant(source.createdAt),
        updatedAt = toInstant(source.updatedAt)
    )

    private fun toInstant(localDateTime: LocalDateTime?): Instant? =
        localDateTime?.atZone(ZoneId.systemDefault())?.toInstant()
}
