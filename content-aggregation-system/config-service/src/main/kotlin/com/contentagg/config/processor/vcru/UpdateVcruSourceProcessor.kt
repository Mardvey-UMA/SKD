package com.contentagg.config.processor.vcru

import com.contentagg.config.api.model.vcru.updateVcruSource.UpdateVcruSourceRequest
import com.contentagg.config.api.model.vcru.updateVcruSource.UpdateVcruSourceResponse
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for updating VC.RU source configurations.
 * Validates VC.RU-specific rules, builds parameters, and delegates to SourceService.
 */
@Component
class UpdateVcruSourceProcessor(
    private val sourceService: SourceService,
    private val vcruSourceHelper: VcruSourceHelper,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(UpdateVcruSourceProcessor::class.java)
    }

    fun process(id: UUID, request: UpdateVcruSourceRequest): UpdateVcruSourceResponse {
        MDC.put("operation", "updateVcruSource")
        try {
            log.info("Updating VC.RU source: {}", id)

            vcruSourceHelper.validateVcruSourceType(request.sourceType)

            val url = vcruSourceHelper.buildVcruUrl(request.alias)
            val parameters = vcruSourceHelper.buildParametersMap(
                request.alias,
                request.parseImages,
                request.maxArticles,
                request.sorting
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
            log.info("Updated VC.RU source: {}", updated.id)

            eventsProducer.publishUpdated(
                updated.id,
                updated.sourceType,
                updated.name,
                updated.isActive
            )

            return UpdateVcruSourceResponse(
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
