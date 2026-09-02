package com.contentagg.config.processor.vcru

import com.contentagg.config.api.model.source.createSource.CreateSourceEnvelope
import com.contentagg.config.api.model.vcru.createVcruSource.CreateVcruSourceRequest
import com.contentagg.config.api.model.vcru.createVcruSource.CreateVcruSourceResponse
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceAddedEventsProducer
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import com.contentagg.config.processor.source.UserSourceLimitChecker
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Processor for creating VC.RU source configurations.
 * Phase 2 — returns {source, was_existing} envelope, emits `source.added` on new creation.
 */
@Component
class CreateVcruSourceProcessor(
    private val sourceService: SourceService,
    private val vcruSourceHelper: VcruSourceHelper,
    private val eventsProducer: SourceEventsProducer,
    private val sourceAddedProducer: SourceAddedEventsProducer,
    private val limitChecker: UserSourceLimitChecker,
) {

    companion object {
        private val log = LoggerFactory.getLogger(CreateVcruSourceProcessor::class.java)
    }

    fun process(
        request: CreateVcruSourceRequest,
        userId: String?,
        requestId: String?,
    ): CreateSourceEnvelope<CreateVcruSourceResponse> {
        MDC.put("operation", "createVcruSource")
        try {
            log.info("Creating VC.RU source: {} of type {} (userId={})", request.name, request.sourceType, userId)

            vcruSourceHelper.validateVcruSourceType(request.sourceType)
            limitChecker.ensureUnderLimit(userId)

            val url = vcruSourceHelper.buildVcruUrl(request.alias)
            val parameters = vcruSourceHelper.buildParametersMap(
                request.alias,
                request.parseImages,
                request.maxArticles,
                request.sorting,
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
                log.info("Created VC.RU source: {}", saved.id)
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
                log.info("VC.RU source already exists: {} — was_existing=true", saved.id)
            }

            val payload = CreateVcruSourceResponse(
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
