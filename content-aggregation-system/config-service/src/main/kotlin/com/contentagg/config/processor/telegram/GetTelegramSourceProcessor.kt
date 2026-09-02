package com.contentagg.config.processor.telegram

import com.contentagg.config.api.model.telegram.getTelegramSource.GetTelegramSourceResponse
import com.contentagg.config.api.model.telegram.listTelegramSources.ListTelegramSourcesResponse
import com.contentagg.config.db.repository.source.model.dto.SourceResponse
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.util.UUID

/**
 * Processor for reading Telegram source configurations.
 * Validates Telegram-specific constraints and delegates to SourceService.
 */
@Component
class GetTelegramSourceProcessor(
    private val sourceService: SourceService
) {

    companion object {
        private val log = LoggerFactory.getLogger(GetTelegramSourceProcessor::class.java)
    }

    fun getById(id: UUID): GetTelegramSourceResponse {
        MDC.put("operation", "getTelegramSourceById")
        try {
            log.debug("Getting Telegram source by id: {}", id)

            val source = sourceService.findById(id)

            if (source.sourceType != SourceType.TELEGRAM) {
                throw InvalidSourceException(
                    "Source $id is not a Telegram source (type: ${source.sourceType})"
                )
            }

            return toGetResponse(source)
        } finally {
            MDC.clear()
        }
    }

    fun listAll(): List<ListTelegramSourcesResponse> {
        MDC.put("operation", "listAllTelegramSources")
        try {
            log.debug("Listing all Telegram sources")
            return sourceService.findByType(SourceType.TELEGRAM).map { toListResponse(it) }
        } finally {
            MDC.clear()
        }
    }

    fun getSupportedTypes(): List<SourceType> = listOf(SourceType.TELEGRAM)

    private fun toGetResponse(source: SourceResponse) = GetTelegramSourceResponse(
        id = runCatching { UUID.fromString(source.id) }.getOrNull(),
        sourceType = source.sourceType.name,
        name = source.name,
        url = source.url,
        channelUsername = source.parameters["channelUsername"],
        downloadMedia = source.parameters["downloadMedia"]?.toBoolean(),
        maxMessages = source.parameters["maxMessages"]?.toIntOrNull(),
        maxMediaSizeMb = source.parameters["maxMediaSizeMb"]?.toIntOrNull(),
        batchSize = source.parameters["batchSize"]?.toIntOrNull(),
        updateFrequencyMinutes = source.updateFrequencyMinutes,
        isActive = source.isActive,
        createdAt = source.createdAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime(),
        updatedAt = source.updatedAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime()
    )

    private fun toListResponse(source: SourceResponse) = ListTelegramSourcesResponse(
        id = runCatching { UUID.fromString(source.id) }.getOrNull(),
        name = source.name,
        channelUsername = source.parameters["channelUsername"],
        isActive = source.isActive,
        createdAt = source.createdAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime()
    )
}
