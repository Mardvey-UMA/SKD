package com.contentplatform.feed.infrastructure.kafka

import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RecommendationsUpdatedConsumer(
    private val feedCacheService: FeedCacheService
) {

    companion object {
        private val log = LoggerFactory.getLogger(RecommendationsUpdatedConsumer::class.java)
        private val objectMapper = jacksonObjectMapper().apply {
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    @KafkaListener(
        topics = ["recommendations.updated"],
        groupId = "feed-service-invalidation"
    )
    fun onMessage(message: String) {
        try {
            val event = objectMapper.readValue(message, RecommendationsUpdatedEvent::class.java)
            consume(event)
        } catch (e: Exception) {
            log.error("Failed to deserialize recommendations.updated message", e)
        }
    }

    fun consume(event: RecommendationsUpdatedEvent) {
        val userId = event.userId?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                log.warn("Invalid userId in recommendations.updated event: {}", it)
                return
            }
        } ?: run {
            log.warn("Missing userId in recommendations.updated event")
            return
        }
        feedCacheService.deleteFeed(userId)
        log.info("Invalidated feed cache for user: {}", userId)
    }
}
