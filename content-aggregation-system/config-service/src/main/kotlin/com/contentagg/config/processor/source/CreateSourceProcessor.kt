package com.contentagg.config.processor.source

import com.contentagg.config.api.model.source.createSource.CreateSourceEnvelope
import com.contentagg.config.api.model.source.createSource.CreateSourceRequest
import com.contentagg.config.api.model.source.createSource.CreateSourceResponse
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceAddedEventsProducer
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Processor for creating source configurations (generic endpoint).
 * Orchestrates validation, DB persistence, Kafka events, and per-user limit check.
 * Returns a {source, was_existing} envelope (Phase 2).
 */
@Component
class CreateSourceProcessor(
    private val sourceService: SourceService,
    private val eventsProducer: SourceEventsProducer,
    private val sourceAddedProducer: SourceAddedEventsProducer,
    private val sourceRequestValidator: SourceRequestValidator,
    private val limitChecker: UserSourceLimitChecker,
) {

    companion object {
        private val log = LoggerFactory.getLogger(CreateSourceProcessor::class.java)
    }

    fun process(
        request: CreateSourceRequest,
        userId: String?,
        requestId: String?,
    ): CreateSourceEnvelope<CreateSourceResponse> {
        MDC.put("operation", "createSource")
        try {
            log.info("Creating source: {} of type {} (userId={})", request.name, request.sourceType, userId)

            sourceRequestValidator.validate(request.sourceType, request.parameters, request.url)
            limitChecker.ensureUnderLimit(userId)

            val dbRequest = SourceRequest(
                sourceType = request.sourceType,
                name = request.name,
                url = request.url,
                updateFrequencyMinutes = request.updateFrequencyMinutes,
                isActive = request.isActive,
                parameters = request.parameters,
            )

            val result = sourceService.createOrGetExisting(dbRequest)
            val saved = result.source

            if (!result.wasExisting) {
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
            }

            val payload = CreateSourceResponse(
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
