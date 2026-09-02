package com.contentplatform.user.unit.kafka

import com.contentplatform.user.application.service.ProfileService
import com.contentplatform.user.infrastructure.kafka.UserRegisteredConsumer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class UserRegisteredConsumerTest {

    private val profileService = mockk<ProfileService>(relaxed = true)
    private val consumer = UserRegisteredConsumer(profileService)

    @Nested
    inner class `valid user registered message` {

        @Test
        fun `should call profileService createProfileFromRegistration with parsed userId and email`() {
            val userId = UUID.randomUUID()
            val email = "newuser@example.com"
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

            consumer.consume(message)

            verify(exactly = 1) {
                profileService.createProfileFromRegistration(userId, email)
            }
        }

        @Test
        fun `should extract user_id and email from nested payload field`() {
            val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            val email = "nested@payload.com"
            val message = """
                {
                  "event_type": "user.registered",
                  "aggregate_type": "User",
                  "aggregate_id": "$userId",
                  "payload": {
                    "user_id": "$userId",
                    "email": "$email",
                    "timestamp": "2026-01-01T00:00:00Z"
                  }
                }
            """.trimIndent()

            consumer.consume(message)

            verify(exactly = 1) {
                profileService.createProfileFromRegistration(userId, email)
            }
        }
    }

    @Nested
    inner class `malformed message` {

        @Test
        fun `should throw on invalid JSON so Kafka error handler can route to DLT`() {
            val malformedJson = "this is not json"

            assertThatThrownBy { consumer.consume(malformedJson) }
                .isInstanceOf(Exception::class.java)

            verify(exactly = 0) {
                profileService.createProfileFromRegistration(any(), any())
            }
        }

        @Test
        fun `should throw IllegalArgumentException on missing payload fields so error handler can DLT immediately`() {
            val incompleteMessage = """
                {
                  "event_type": "user.registered",
                  "aggregate_type": "User"
                }
            """.trimIndent()

            assertThatThrownBy { consumer.consume(incompleteMessage) }
                .isInstanceOf(IllegalArgumentException::class.java)

            verify(exactly = 0) {
                profileService.createProfileFromRegistration(any(), any())
            }
        }

        @Test
        fun `should throw on empty message so Kafka error handler can route to DLT`() {
            assertThatThrownBy { consumer.consume("") }
                .isInstanceOf(Exception::class.java)

            verify(exactly = 0) {
                profileService.createProfileFromRegistration(any(), any())
            }
        }
    }
}
