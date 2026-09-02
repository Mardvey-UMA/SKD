package com.contentplatform.user.infrastructure.kafka

import com.contentplatform.user.application.service.ProfileService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SubscriptionChangedConsumer(
    private val profileService: ProfileService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionChangedConsumer::class.java)
    }

    @KafkaListener(topics = ["subscription.changed"], groupId = "user-service-subscriptions")
    fun consume(message: String) {
        try {
            val tree = objectMapper.readTree(message)
            val userIdNode = tree.get("user_id") ?: run {
                log.warn("Missing user_id in subscription.changed message")
                return
            }
            val tierNode = tree.get("tier") ?: run {
                log.warn("Missing tier in subscription.changed message")
                return
            }
            val userId = UUID.fromString(userIdNode.asText())
            val tier = tierNode.asText()

            log.info("Processing subscription.changed for userId={} tier={}", userId, tier)
            profileService.updateSubscriptionTier(userId, tier)
        } catch (ex: Exception) {
            log.error("Failed to process subscription.changed message: {}", message, ex)
        }
    }
}
