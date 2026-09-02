package com.skd.subscription.db.service

import com.skd.subscription.db.repository.outbox.OutboxRepository
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
        private const val BATCH_LIMIT = 100
    }

    @Scheduled(fixedDelayString = "\${outbox.poller.fixed-delay-ms:5000}")
    fun pollAndPublish() {
        val entries = outboxRepository.findUnpublished(BATCH_LIMIT)
        if (entries.isEmpty()) return

        for (entry in entries) {
            try {
                val future = kafkaTemplate.send(entry.eventType, entry.aggregateId, entry.payload)
                future.get()
                outboxRepository.markPublished(entry.id!!, Instant.now())
                log.info("Published outbox entry id={}, topic={}", entry.id, entry.eventType)
            } catch (e: Exception) {
                log.error("Failed to publish outbox entry id={}", entry.id, e)
            }
        }
    }
}
