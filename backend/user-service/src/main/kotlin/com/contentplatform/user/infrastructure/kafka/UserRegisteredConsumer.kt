package com.contentplatform.user.infrastructure.kafka

import com.contentplatform.user.application.service.ProfileService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserRegisteredConsumer(
    private val profileService: ProfileService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    companion object {
        private val log = LoggerFactory.getLogger(UserRegisteredConsumer::class.java)
    }

    @KafkaListener(topics = ["user.registered"], groupId = "user-service-auth-events")
    fun consume(message: String) {
        val tree = objectMapper.readTree(message)
        // Support both flat format {"user_id":...} and outbox-wrapped {"payload":{"user_id":...}}
        val dataNode = tree.get("payload") ?: tree
        val userIdNode = dataNode.get("user_id")
            ?: throw IllegalArgumentException("Missing user_id in user.registered message: $message")
        val emailNode = dataNode.get("email")
            ?: throw IllegalArgumentException("Missing email in user.registered message: $message")
        val userId = UUID.fromString(userIdNode.asText())
        val email = emailNode.asText()
        log.info("Processing user.registered for userId={}", userId)
        profileService.createProfileFromRegistration(userId, email)
    }
}
