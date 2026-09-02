package com.contentagg.config.processor.telegram

import com.contentagg.config.api.model.telegram.updateTelegramSource.UpdateTelegramSourceRequest
import com.contentagg.config.api.model.telegram.updateTelegramSource.UpdateTelegramSourceResponse
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.util.UUID

/**
 * Processor for updating Telegram source configurations.
 * Validates Telegram-specific rules, rebuilds parameters, and delegates to SourceService.
 */
@Component
class UpdateTelegramSourceProcessor(
    private val sourceService: SourceService,
    private val telegramSourceHelper: TelegramSourceHelper,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(UpdateTelegramSourceProcessor::class.java)
    }

    fun process(id: UUID, request: UpdateTelegramSourceRequest): UpdateTelegramSourceResponse {
        MDC.put("operation", "updateTelegramSource")
        try {
            log.info("Updating Telegram source: {}", id)

            val existing = sourceService.findById(id)
            telegramSourceHelper.validateTelegramSourceType(existing.sourceType)

            val channelUsername = request.channelUsername ?: existing.parameters["channelUsername"] ?: ""
            val downloadMedia = request.downloadMedia ?: existing.parameters["downloadMedia"]?.toBoolean()
            val maxMessages = request.maxMessages ?: existing.parameters["maxMessages"]?.toIntOrNull()
            val maxMediaSizeMb = request.maxMediaSizeMb ?: existing.parameters["maxMediaSizeMb"]?.toIntOrNull()
            val batchSize = request.batchSize ?: existing.parameters["batchSize"]?.toIntOrNull()

            val url = telegramSourceHelper.buildTelegramUrl(channelUsername)
            val parameters = telegramSourceHelper.buildParametersMap(
                channelUsername,
                downloadMedia,
                maxMessages,
                maxMediaSizeMb,
                batchSize
            )

            val sourceRequest = SourceRequest(
                sourceType = SourceType.TELEGRAM,
                name = request.name,
                url = url,
                updateFrequencyMinutes = request.updateFrequencyMinutes ?: existing.updateFrequencyMinutes,
                isActive = request.isActive ?: (existing.isActive ?: true),
                parameters = parameters
            )

            val updated = sourceService.update(id, sourceRequest)
            log.info("Updated Telegram source: {}", updated.id)

            eventsProducer.publishUpdated(
                updated.id,
                updated.sourceType,
                updated.name,
                updated.isActive
            )

            return UpdateTelegramSourceResponse(
                id = runCatching { UUID.fromString(updated.id) }.getOrNull(),
                sourceType = updated.sourceType.name,
                name = updated.name,
                url = updated.url,
                updateFrequencyMinutes = updated.updateFrequencyMinutes,
                isActive = updated.isActive,
                parameters = updated.parameters.toString(),
                createdAt = updated.createdAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime(),
                updatedAt = updated.updatedAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime()
            )
        } finally {
            MDC.clear()
        }
    }
}
