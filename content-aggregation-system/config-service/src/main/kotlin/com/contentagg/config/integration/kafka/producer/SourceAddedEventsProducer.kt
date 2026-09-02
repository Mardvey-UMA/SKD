package com.contentagg.config.integration.kafka.producer

import com.contentagg.config.enums.SourceType
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Publishes `source.added` events as JSON (Phase 2).
 *
 * Fires ONLY on a genuinely new source addition (not on was_existing=true path).
 * Key = user_id so feed-service consumer processes all events for a single user on one partition.
 */
@Component
class SourceAddedEventsProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${spring.kafka.topics.source-added}")
    private val topic: String,
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceAddedEventsProducer::class.java)
    }

    init {
        log.info("SourceAddedEventsProducer initialized with topic: {}", topic)
    }

    fun publish(
        sourceId: String,
        userId: String,
        sourceType: SourceType,
        name: String,
        requestId: String?,
    ) {
        val event = SourceAddedEvent(
            sourceId = sourceId,
            userId = userId,
            sourceType = sourceType.name,
            name = name,
            addedAt = Instant.now().toString(),
            requestId = requestId,
        )
        val json = objectMapper.writeValueAsString(event)
        val record = ProducerRecord(topic, userId, json)
        log.info("Sending source.added event topic={} key={} sourceId={}", topic, userId, sourceId)

        kafkaTemplate.send(record).whenComplete { result: SendResult<String, String>?, ex: Throwable? ->
            if (ex != null) {
                log.error("Failed to send source.added topic={} key={}", topic, userId, ex)
            } else {
                log.debug(
                    "source.added sent topic={} partition={} offset={}",
                    topic,
                    result!!.recordMetadata.partition(),
                    result.recordMetadata.offset(),
                )
            }
        }
    }
}
