package com.contentplatform.user.unit.kafka

import com.contentplatform.user.application.service.ProfileService
import com.contentplatform.user.infrastructure.kafka.SubscriptionChangedConsumer
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class SubscriptionChangedConsumerTest {

    private val profileService = mockk<ProfileService>(relaxed = true)
    private val consumer = SubscriptionChangedConsumer(profileService)

    @Nested
    inner class `valid subscription changed message` {

        @Test
        fun `should call profileService updateSubscriptionTier with parsed userId and tier`() {
            val userId = UUID.randomUUID()
            val message = """
                {
                  "event_type": "subscription.changed",
                  "user_id": "$userId",
                  "tier": "premium",
                  "status": "active",
                  "timestamp": "2026-04-05T10:00:00Z"
                }
            """.trimIndent()

            consumer.consume(message)

            verify(exactly = 1) {
                profileService.updateSubscriptionTier(userId, "premium")
            }
        }

        @Test
        fun `should handle free tier downgrade`() {
            val userId = UUID.randomUUID()
            val message = """
                {
                  "event_type": "subscription.changed",
                  "user_id": "$userId",
                  "tier": "free",
                  "status": "expired",
                  "timestamp": "2026-04-05T12:00:00Z"
                }
            """.trimIndent()

            consumer.consume(message)

            verify(exactly = 1) {
                profileService.updateSubscriptionTier(userId, "free")
            }
        }
    }

    @Nested
    inner class `malformed message` {

        @Test
        fun `should not throw on invalid JSON`() {
            val malformedJson = "not valid json at all"

            consumer.consume(malformedJson)

            verify(exactly = 0) {
                profileService.updateSubscriptionTier(any(), any())
            }
        }

        @Test
        fun `should not throw on missing required fields`() {
            val incompleteMessage = """
                {
                  "event_type": "subscription.changed"
                }
            """.trimIndent()

            consumer.consume(incompleteMessage)

            verify(exactly = 0) {
                profileService.updateSubscriptionTier(any(), any())
            }
        }

        @Test
        fun `should not throw on empty message`() {
            consumer.consume("")

            verify(exactly = 0) {
                profileService.updateSubscriptionTier(any(), any())
            }
        }
    }
}
