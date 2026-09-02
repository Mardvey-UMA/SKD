package com.contentplatform.auth.kafka

import com.contentplatform.auth.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SubscriptionChangedConsumer(
    private val userService: UserService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionChangedConsumer::class.java)
    }

    @KafkaListener(topics = ["subscription.changed"], groupId = "auth-service-subscriptions")
    fun handleSubscriptionChanged(message: String) {
        try {
            val tree = objectMapper.readTree(message)
            val userId = UUID.fromString(tree.get("user_id").asText())
            val tier = tree.get("tier").asText()

            log.info("Processing subscription.changed for userId={} tier={}", userId, tier)
            userService.updateSubscriptionTier(userId, tier)
        } catch (ex: Exception) {
            log.error("Failed to process subscription.changed message: {}", message, ex)
        }
    }
}
