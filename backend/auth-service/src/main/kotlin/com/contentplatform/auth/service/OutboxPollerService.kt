package com.contentplatform.auth.service

import com.contentplatform.auth.db.repository.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OutboxPollerService(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    companion object {
        private val log = LoggerFactory.getLogger(OutboxPollerService::class.java)
        private const val BATCH_SIZE = 50
    }

    @Scheduled(fixedDelay = 5000)
    fun pollAndPublish() {
        val entries = outboxRepository.findUnpublished(BATCH_SIZE)
        if (entries.isEmpty()) return

        log.debug("Polling outbox: found {} unpublished entries", entries.size)

        for (entry in entries) {
            try {
                kafkaTemplate.send(entry.eventType, entry.aggregateId, entry.payload).get()
                outboxRepository.markPublished(entry.id!!, Instant.now())
                log.info("Published outbox entry id={} topic={}", entry.id, entry.eventType)
            } catch (ex: Exception) {
                log.error("Failed to publish outbox entry id={} topic={}", entry.id, entry.eventType, ex)
            }
        }
    }
}
