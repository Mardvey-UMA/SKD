package com.contentagg.parser.integration.kafka.listener

import com.contentagg.parser.db.service.util.SourceType
import com.contentagg.parser.integration.rest.configservice.cache.ParserConfigService
import com.contentagg.parser.proto.SourceConfigUpdatedEvent
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class SourceConfigEventsListener(
    private val parserConfigService: ParserConfigService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SourceConfigEventsListener::class.java)
    }

    @KafkaListener(
        topics = ["\${spring.kafka.topics.source-config-updated}"],
        containerFactory = "configUpdateKafkaListenerContainerFactory",
    )
    fun onSourceConfigUpdated(
        @Payload data: ByteArray,
        acknowledgment: Acknowledgment,
    ) {
        val event = try {
            SourceConfigUpdatedEvent.parseFrom(data)
        } catch (e: Exception) {
            log.error("Failed to parse SourceConfigUpdatedEvent: {}", e.message)
            acknowledgment.acknowledge()
            return
        }

        MDC.put("source_id", event.id)
        MDC.put("event_type", event.eventType.name)
        MDC.put("source_type", event.sourceType)

        try {
            log.info(
                "Received config update event: sourceType={}, eventType={}, id={}",
                event.sourceType, event.eventType, event.id
            )

            val sourceType = try {
                SourceType.valueOf(event.sourceType.uppercase())
            } catch (e: IllegalArgumentException) {
                log.error("Invalid source type in config update event: {}", event.sourceType, e)
                acknowledgment.acknowledge()
                return
            }

            when (event.eventType.name) {
                "CREATED", "UPDATED" -> {
                    parserConfigService.reloadConfig(sourceType)
                    log.info("Configuration reloaded for source: {}", sourceType)
                }
                "DELETED" -> {
                    parserConfigService.reloadConfig(sourceType)
                    log.warn("Configuration deleted for source: {}", sourceType)
                }
                else -> log.warn("Unknown config event type: {}", event.eventType)
            }

            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("Error processing config update event: id={}, error={}", event.id, e.message, e)
            // Do NOT acknowledge on failure — let error handler retry
        } finally {
            MDC.remove("source_id")
            MDC.remove("event_type")
            MDC.remove("source_type")
        }
    }
}
