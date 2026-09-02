package com.contentplatform.auth.integration.kafka

import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.UserEntity
import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID

@Tag("integration")
class SubscriptionChangedConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")
    }

    @Test
    fun `should update user subscription tier when subscription changed event received`() {
        val userId = UUID.randomUUID()
        // Insert user with free tier
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, email_verified, subscription_tier) VALUES (?, ?, ?, ?, ?)",
            userId, "sub-test@example.com", "\$2a\$10\$fakehash", true, "free"
        )

        val message = """{"event_type":"subscription.changed","user_id":"$userId","tier":"premium","status":"active"}"""
        kafkaTemplate.send("subscription.changed", userId.toString(), message)

        // Wait for async consumer to process the message
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            val user = userRepository.findById(userId)
            assertThat(user).isPresent
            assertThat(user.get().subscriptionTier).isEqualTo("premium")
        }
    }

    @Test
    fun `should handle downgrade from premium to free`() {
        val userId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, email_verified, subscription_tier) VALUES (?, ?, ?, ?, ?)",
            userId, "downgrade@example.com", "\$2a\$10\$fakehash", true, "premium"
        )

        val message = """{"event_type":"subscription.changed","user_id":"$userId","tier":"free","status":"cancelled"}"""
        kafkaTemplate.send("subscription.changed", userId.toString(), message)

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            val user = userRepository.findById(userId)
            assertThat(user).isPresent
            assertThat(user.get().subscriptionTier).isEqualTo("free")
        }
    }

    @Test
    fun `should not fail when user does not exist for subscription event`() {
        val nonExistentUserId = UUID.randomUUID()
        val message = """{"event_type":"subscription.changed","user_id":"$nonExistentUserId","tier":"premium","status":"active"}"""

        // This should not cause the consumer to crash
        kafkaTemplate.send("subscription.changed", nonExistentUserId.toString(), message)

        // Give consumer time to process -- if it crashes, subsequent tests will fail
        // Verify no exception by waiting and then checking consumer is still alive
        // (send another event for a real user to confirm consumer is operational)
        val realUserId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash, email_verified, subscription_tier) VALUES (?, ?, ?, ?, ?)",
            realUserId, "alive-check@example.com", "\$2a\$10\$fakehash", true, "free"
        )

        val followUpMessage = """{"event_type":"subscription.changed","user_id":"$realUserId","tier":"premium","status":"active"}"""
        kafkaTemplate.send("subscription.changed", realUserId.toString(), followUpMessage)

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            val user = userRepository.findById(realUserId)
            assertThat(user).isPresent
            assertThat(user.get().subscriptionTier).isEqualTo("premium")
        }
    }
}
