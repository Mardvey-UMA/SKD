package com.contentagg.config.processor.telegram

import com.contentagg.config.api.model.source.createSource.CreateSourceEnvelope
import com.contentagg.config.api.model.telegram.createTelegramSource.CreateTelegramSourceRequest
import com.contentagg.config.api.model.telegram.createTelegramSource.CreateTelegramSourceResponse
import com.contentagg.config.configuration.properties.TelegramValidationProperties
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.exception.TelegramChannelNotAccessibleException
import com.contentagg.config.integration.kafka.producer.SourceAddedEventsProducer
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import com.contentagg.config.integration.rest.parser.TelegramValidationClient
import com.contentagg.config.processor.source.UserSourceLimitChecker
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.util.UUID

/**
 * Processor for creating Telegram source configurations.
 * Phase 2 — TG channel accessibility validation → per-user limit check →
 * createOrGetExisting → emit source.added on new creation.
 */
@Component
class CreateTelegramSourceProcessor(
    private val sourceService: SourceService,
    private val telegramSourceHelper: TelegramSourceHelper,
    private val eventsProducer: SourceEventsProducer,
    private val sourceAddedProducer: SourceAddedEventsProducer,
    private val telegramValidationClient: TelegramValidationClient,
    private val telegramValidationProperties: TelegramValidationProperties,
    private val limitChecker: UserSourceLimitChecker,
) {

    companion object {
        private val log = LoggerFactory.getLogger(CreateTelegramSourceProcessor::class.java)
    }

    fun process(
        request: CreateTelegramSourceRequest,
        userId: String?,
        requestId: String?,
    ): CreateSourceEnvelope<CreateTelegramSourceResponse> {
        MDC.put("operation", "createTelegramSource")
        try {
            log.info("Creating Telegram source: {} of type {} (userId={})", request.name, request.sourceType, userId)

            telegramSourceHelper.validateTelegramSourceType(request.sourceType)

            if (telegramValidationProperties.enabled) {
                val response = telegramValidationClient.validate(request.channelUsername)
                if (!response.accessible) {
                    log.info("Telegram channel '{}' not accessible: reason={}", request.channelUsername, response.reason)
                    throw TelegramChannelNotAccessibleException(request.channelUsername, response.reason)
                }
                log.info("Telegram channel '{}' validated (chatId={})", request.channelUsername, response.chatId)
            } else {
                log.debug("Telegram channel-validation disabled — skipping check")
            }

            limitChecker.ensureUnderLimit(userId)

            val url = telegramSourceHelper.buildTelegramUrl(request.channelUsername)
            val parameters = telegramSourceHelper.buildParametersMap(
                request.channelUsername,
                request.downloadMedia,
                request.maxMessages,
                request.maxMediaSizeMb,
                request.batchSize,
            )

            val sourceRequest = SourceRequest(
                sourceType = request.sourceType,
                name = request.name,
                url = url,
                updateFrequencyMinutes = request.updateFrequencyMinutes,
                isActive = request.isActive,
                parameters = parameters,
            )

            val result = sourceService.createOrGetExisting(sourceRequest)
            val saved = result.source

            if (!result.wasExisting) {
                log.info("Created Telegram source: {}", saved.id)
                eventsProducer.publishCreated(saved.id, saved.sourceType, saved.name, saved.isActive)
                if (!userId.isNullOrBlank()) {
                    sourceAddedProducer.publish(
                        sourceId = saved.id,
                        userId = userId,
                        sourceType = saved.sourceType,
                        name = saved.name,
                        requestId = requestId,
                    )
                }
            } else {
                log.info("Telegram source already exists: {} — was_existing=true", saved.id)
            }

            val payload = CreateTelegramSourceResponse(
                id = runCatching { UUID.fromString(saved.id) }.getOrNull(),
                sourceType = saved.sourceType.name,
                name = saved.name,
                url = saved.url,
                updateFrequencyMinutes = saved.updateFrequencyMinutes,
                isActive = saved.isActive,
                parameters = saved.parameters.toString(),
                createdAt = saved.createdAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime(),
                updatedAt = saved.updatedAt?.atZone(ZoneId.of("UTC"))?.toLocalDateTime(),
            )
            return CreateSourceEnvelope(source = payload, wasExisting = result.wasExisting)
        } finally {
            MDC.clear()
        }
    }
}
