package com.contentagg.config.processor.habr

import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for deleting Habr source configurations.
 * Validates the source is a Habr type before deletion, publishes Kafka event.
 */
@Component
class DeleteHabrSourceProcessor(
    private val sourceService: SourceService,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(DeleteHabrSourceProcessor::class.java)
    }

    fun process(id: UUID) {
        MDC.put("operation", "deleteHabrSource")
        try {
            log.info("Deleting Habr source: {}", id)
            val source = sourceService.findById(id)
            if (source.sourceType != SourceType.HABR) {
                throw InvalidSourceException("Source $id is not a Habr source (type: ${source.sourceType})")
            }
            sourceService.delete(id)
            eventsProducer.publishDeleted(id.toString(), SourceType.HABR, source.name)
        } finally {
            MDC.clear()
        }
    }
}
