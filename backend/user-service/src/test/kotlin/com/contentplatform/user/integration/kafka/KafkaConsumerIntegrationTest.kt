package com.contentplatform.user.integration.kafka

import com.contentplatform.user.db.repository.ProfileRepository
import com.contentplatform.user.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID

@Tag("integration")
class KafkaConsumerIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var profileRepository: ProfileRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM profiles")
    }

    @Nested
    inner class `user registered consumer` {

        @Test
        fun `should create profile in database when user registered message is consumed`() {
            val userId = UUID.randomUUID()
            val email = "kafka-test@example.com"
            val message = """
                {
                  "event_type": "user.registered",
                  "aggregate_type": "User",
                  "aggregate_id": "$userId",
                  "payload": {
                    "user_id": "$userId",
                    "email": "$email",
                    "timestamp": "2026-04-05T10:00:00Z"
                  }
                }
            """.trimIndent()

            kafkaTemplate.send("user.registered", userId.toString(), message).get()

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val profile = profileRepository.findById(userId)
                assertThat(profile).isPresent
                assertThat(profile.get().email).isEqualTo(email)
                assertThat(profile.get().subscriptionTier).isEqualTo("free")
                assertThat(profile.get().onboardingCompleted).isFalse()
            }
        }

        @Test
        fun `should create outbox event for user created after profile creation`() {
            val userId = UUID.randomUUID()
            val email = "outbox-test@example.com"
            val message = """
                {
                  "event_type": "user.registered",
                  "aggregate_type": "User",
                  "aggregate_id": "$userId",
                  "payload": {
                    "user_id": "$userId",
                    "email": "$email",
                    "timestamp": "2026-04-05T10:00:00Z"
                  }
                }
            """.trimIndent()

            kafkaTemplate.send("user.registered", userId.toString(), message).get()

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val outboxCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = 'user.created'",
                    Int::class.java,
                    userId.toString()
                )
                assertThat(outboxCount).isEqualTo(1)
            }
        }

        @Test
        fun `should be idempotent - duplicate messages do not create duplicate profiles`() {
            val userId = UUID.randomUUID()
            val email = "idempotent@example.com"
            val message = """
                {
                  "event_type": "user.registered",
                  "aggregate_type": "User",
                  "aggregate_id": "$userId",
                  "payload": {
                    "user_id": "$userId",
                    "email": "$email",
                    "timestamp": "2026-04-05T10:00:00Z"
                  }
                }
            """.trimIndent()

            // Send the same message twice
            kafkaTemplate.send("user.registered", userId.toString(), message).get()
            kafkaTemplate.send("user.registered", userId.toString(), message).get()

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val profile = profileRepository.findById(userId)
                assertThat(profile).isPresent
                assertThat(profile.get().email).isEqualTo(email)
            }

            // Only one profile should exist
            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM profiles WHERE id = ?",
                Int::class.java,
                userId
            )
            assertThat(count).isEqualTo(1)
        }
    }

    @Nested
    inner class `subscription changed consumer` {

        @Test
        fun `should update subscription tier when subscription changed message is consumed`() {
            // Pre-create profile
            val userId = UUID.randomUUID()
            jdbcTemplate.update(
                "INSERT INTO profiles (id, email, subscription_tier) VALUES (?, ?, 'free')",
                userId,
                "sub-test@example.com"
            )

            val message = """
                {
                  "event_type": "subscription.changed",
                  "user_id": "$userId",
                  "tier": "premium",
                  "status": "active",
                  "timestamp": "2026-04-05T10:00:00Z"
                }
            """.trimIndent()

            kafkaTemplate.send("subscription.changed", userId.toString(), message).get()

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val profile = profileRepository.findById(userId)
                assertThat(profile).isPresent
                assertThat(profile.get().subscriptionTier).isEqualTo("premium")
            }
        }

        @Test
        fun `should handle downgrade from premium to free`() {
            // Pre-create profile with premium tier
            val userId = UUID.randomUUID()
            jdbcTemplate.update(
                "INSERT INTO profiles (id, email, subscription_tier) VALUES (?, ?, 'premium')",
                userId,
                "downgrade@example.com"
            )

            val message = """
                {
                  "event_type": "subscription.changed",
                  "user_id": "$userId",
                  "tier": "free",
                  "status": "expired",
                  "timestamp": "2026-04-05T12:00:00Z"
                }
            """.trimIndent()

            kafkaTemplate.send("subscription.changed", userId.toString(), message).get()

            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                val profile = profileRepository.findById(userId)
                assertThat(profile).isPresent
                assertThat(profile.get().subscriptionTier).isEqualTo("free")
            }
        }
    }
}
