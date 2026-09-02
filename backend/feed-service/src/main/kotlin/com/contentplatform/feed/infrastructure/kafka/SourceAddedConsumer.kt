package com.contentplatform.feed.infrastructure.kafka

import com.contentplatform.feed.application.SourceAdditionsService
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

@Component
class SourceAddedConsumer(
    private val sourceAdditionsService: SourceAdditionsService
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceAddedConsumer::class.java)
        private val objectMapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    @KafkaListener(
        topics = ["source.added"],
        groupId = "feed-source-additions-consumer"
    )
    fun onMessage(message: String) {
        val event = try {
            objectMapper.readValue(message, SourceAddedEvent::class.java)
        } catch (e: Exception) {
            log.error("Failed to deserialize source.added message: {}", message, e)
            return
        }
        handle(event)
    }

    fun handle(event: SourceAddedEvent) {
        val userId = event.userId?.let { safeUuid(it) }
        val sourceId = event.sourceId?.let { safeUuid(it) }
        val sourceType = event.sourceType
        val sourceName = event.name

        if (userId == null || sourceId == null || sourceType.isNullOrBlank() || sourceName.isNullOrBlank()) {
            log.warn(
                "Discarding malformed source.added event (userId={}, sourceId={}, type={}, name={})",
                event.userId, event.sourceId, event.sourceType, event.name
            )
            return
        }

        val addedAt = parseInstant(event.addedAt) ?: Instant.now()
        sourceAdditionsService.recordAddition(
            userId = userId,
            sourceId = sourceId,
            sourceType = sourceType,
            sourceName = sourceName,
            addedAt = addedAt
        )
        log.info("Recorded source addition: userId={} sourceId={} type={}", userId, sourceId, sourceType)
    }

    private fun safeUuid(value: String): UUID? = try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            log.warn("Failed to parse added_at timestamp: {}", value)
            null
        }
    }
}
