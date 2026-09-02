package com.contentagg.config.processor.source

import com.contentagg.config.api.model.source.updateSource.UpdateSourceRequest
import com.contentagg.config.api.model.source.updateSource.UpdateSourceResponse
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for updating source configurations.
 * Orchestrates validation, DB update, and Kafka event publishing.
 */
@Component
class UpdateSourceProcessor(
    private val sourceService: SourceService,
    private val eventsProducer: SourceEventsProducer,
    private val sourceRequestValidator: SourceRequestValidator
) {

    companion object {
        private val log = LoggerFactory.getLogger(UpdateSourceProcessor::class.java)
    }

    fun process(id: UUID, request: UpdateSourceRequest): UpdateSourceResponse {
        MDC.put("operation", "updateSource")
        try {
            log.info("Updating source: {}", id)

            sourceRequestValidator.validate(request.sourceType, request.parameters, request.url)

            val dbRequest = SourceRequest(
                sourceType = request.sourceType,
                name = request.name,
                url = request.url,
                updateFrequencyMinutes = request.updateFrequencyMinutes,
                isActive = request.isActive,
                parameters = request.parameters
            )

            val updated = sourceService.update(id, dbRequest)

            eventsProducer.publishUpdated(
                updated.id,
                updated.sourceType,
                updated.name,
                updated.isActive
            )

            return UpdateSourceResponse(
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
