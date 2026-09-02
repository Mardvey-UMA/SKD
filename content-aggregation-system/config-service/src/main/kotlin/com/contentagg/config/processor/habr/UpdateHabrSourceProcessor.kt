package com.contentagg.config.processor.habr

import com.contentagg.config.api.model.habr.updateHabrSource.UpdateHabrSourceRequest
import com.contentagg.config.api.model.habr.updateHabrSource.UpdateHabrSourceResponse
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for updating Habr source configurations.
 * Validates Habr-specific rules, builds parameters, and delegates to SourceService.
 */
@Component
class UpdateHabrSourceProcessor(
    private val sourceService: SourceService,
    private val habrSourceHelper: HabrSourceHelper,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(UpdateHabrSourceProcessor::class.java)
    }

    fun process(id: UUID, request: UpdateHabrSourceRequest): UpdateHabrSourceResponse {
        MDC.put("operation", "updateHabrSource")
        try {
            log.info("Updating Habr source: {}", id)

            habrSourceHelper.validateHabrSourceType(request.sourceType)

            val url = habrSourceHelper.buildHabrUrl(request.alias)
            val parameters = habrSourceHelper.buildParametersMap(
                request.alias,
                request.parseComments,
                request.parseImages,
                request.maxArticles,
                request.minRating,
                request.hubsFilter,
                request.tagsFilter
            )

            val sourceRequest = SourceRequest(
                sourceType = request.sourceType,
                name = request.name,
                url = url,
                updateFrequencyMinutes = request.updateFrequencyMinutes,
                isActive = request.isActive,
                parameters = parameters
            )

            val updated = sourceService.update(id, sourceRequest)
            log.info("Updated Habr source: {}", updated.id)

            eventsProducer.publishUpdated(
                updated.id,
                updated.sourceType,
                updated.name,
                updated.isActive
            )

            return UpdateHabrSourceResponse(
                id = updated.id,
                sourceType = updated.sourceType,
                name = updated.name,
                url = updated.url,
                updateFrequencyMinutes = updated.updateFrequencyMinutes,
                isActive = updated.isActive,
                parameters = updated.parameters,
                createdAt = updated.createdAt,
                updatedAt = updated.updatedAt
            )
        } finally {
            MDC.clear()
        }
    }
}
