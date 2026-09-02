package com.contentagg.config.processor.habr

import com.contentagg.config.api.model.habr.createHabrSource.CreateHabrSourceRequest
import com.contentagg.config.api.model.habr.createHabrSource.CreateHabrSourceResponse
import com.contentagg.config.api.model.source.createSource.CreateSourceEnvelope
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceAddedEventsProducer
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import com.contentagg.config.processor.source.UserSourceLimitChecker
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Processor for creating Habr source configurations.
 * Phase 2 — returns {source, was_existing} envelope, emits `source.added` on new creation.
 */
@Component
class CreateHabrSourceProcessor(
    private val sourceService: SourceService,
    private val habrSourceHelper: HabrSourceHelper,
    private val eventsProducer: SourceEventsProducer,
    private val sourceAddedProducer: SourceAddedEventsProducer,
    private val limitChecker: UserSourceLimitChecker,
) {

    companion object {
        private val log = LoggerFactory.getLogger(CreateHabrSourceProcessor::class.java)
    }

    fun process(
        request: CreateHabrSourceRequest,
        userId: String?,
        requestId: String?,
    ): CreateSourceEnvelope<CreateHabrSourceResponse> {
        MDC.put("operation", "createHabrSource")
        try {
            log.info("Creating Habr source: {} of type {} (userId={})", request.name, request.sourceType, userId)

            habrSourceHelper.validateHabrSourceType(request.sourceType)
            limitChecker.ensureUnderLimit(userId)

            val url = habrSourceHelper.buildHabrUrl(request.alias)
            val parameters = habrSourceHelper.buildParametersMap(
                request.alias,
                request.parseComments,
                request.parseImages,
                request.maxArticles,
                request.minRating,
                request.hubsFilter,
                request.tagsFilter,
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
                log.info("Created Habr source: {}", saved.id)
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
                log.info("Habr source already exists: {} — was_existing=true", saved.id)
            }

            val payload = CreateHabrSourceResponse(
                id = saved.id,
                sourceType = saved.sourceType,
                name = saved.name,
                url = saved.url,
                updateFrequencyMinutes = saved.updateFrequencyMinutes,
                isActive = saved.isActive,
                parameters = saved.parameters,
                createdAt = saved.createdAt,
                updatedAt = saved.updatedAt,
            )
            return CreateSourceEnvelope(source = payload, wasExisting = result.wasExisting)
        } finally {
            MDC.clear()
        }
    }
}
