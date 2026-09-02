package com.contentagg.config.processor.vcru

import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for deleting VC.RU source configurations.
 * Validates the source is a VC.RU type before deletion, publishes Kafka event.
 */
@Component
class DeleteVcruSourceProcessor(
    private val sourceService: SourceService,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(DeleteVcruSourceProcessor::class.java)
    }

    fun process(id: UUID) {
        MDC.put("operation", "deleteVcruSource")
        try {
            log.info("Deleting VC.RU source: {}", id)
            val source = sourceService.findById(id)
            if (source.sourceType != SourceType.VCRU) {
                throw InvalidSourceException("Source $id is not a VC.RU source (type: ${source.sourceType})")
            }
            sourceService.delete(id)
            eventsProducer.publishDeleted(id.toString(), SourceType.VCRU, source.name)
        } finally {
            MDC.clear()
        }
    }
}
