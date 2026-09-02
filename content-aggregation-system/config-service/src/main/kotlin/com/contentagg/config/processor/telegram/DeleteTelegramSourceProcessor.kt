package com.contentagg.config.processor.telegram

import com.contentagg.config.db.service.source.SourceService
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import com.contentagg.config.integration.kafka.producer.SourceEventsProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Processor for deleting Telegram source configurations.
 * Validates the source is a TELEGRAM type before deletion, publishes Kafka event.
 */
@Component
class DeleteTelegramSourceProcessor(
    private val sourceService: SourceService,
    private val eventsProducer: SourceEventsProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(DeleteTelegramSourceProcessor::class.java)
    }

    fun process(id: UUID) {
        MDC.put("operation", "deleteTelegramSource")
        try {
            log.info("Deleting Telegram source: {}", id)
            val source = sourceService.findById(id)
            if (source.sourceType != SourceType.TELEGRAM) {
                throw InvalidSourceException("Source $id is not a Telegram source (type: ${source.sourceType})")
            }
            sourceService.delete(id)
            eventsProducer.publishDeleted(id.toString(), SourceType.TELEGRAM, source.name)
        } finally {
            MDC.clear()
        }
    }
}
