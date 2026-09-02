package com.contentagg.config.processor.source

import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for deleting source configurations.
 * Orchestrates DB deletion and Kafka event publishing.
 */
@Component
class DeleteSourceProcessor(
    private val sourceService: SourceService,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(DeleteSourceProcessor::class.java)
    }

    fun process(id: UUID) {
        MDC.put("operation", "deleteSource")
        try {
            log.info("Deleting source: {}", id)

            val deleted = sourceService.delete(id)

            eventsProducer.publishDeleted(
                deleted.id,
                deleted.sourceType,
                deleted.name
            )
        } finally {
            MDC.clear()
        }
    }
}
