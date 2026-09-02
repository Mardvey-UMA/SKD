package com.contentplatform.user.infrastructure.outbox

import com.contentplatform.user.application.service.OutboxService
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OutboxPoller(
    private val outboxService: OutboxService,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    companion object {
        private val log = LoggerFactory.getLogger(OutboxPoller::class.java)
    }

    @Scheduled(fixedDelay = 5000)
    fun poll() {
        val events = outboxService.findUnpublished(100)
        for (event in events) {
            try {
                kafkaTemplate.send(event.eventType, event.aggregateId, event.payload).get()
                outboxService.markPublished(event.id!!)
            } catch (e: Exception) {
                log.error("Failed to send outbox event id={} to Kafka topic={}", event.id, event.eventType, e)
            }
        }
    }
}
