package com.contentplatform.auth.unit.kafka

import com.contentplatform.auth.kafka.SubscriptionChangedConsumer
import com.contentplatform.auth.service.UserService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("unit")
class SubscriptionChangedConsumerTest {

    private val userService = mockk<UserService>()

    private val consumer = SubscriptionChangedConsumer(
        userService = userService
    )

    @Nested
    inner class HandleSubscriptionChanged {

        @Test
        fun `should update subscription tier for valid message`() {
            val userId = UUID.randomUUID()
            val message = """{"event_type":"subscription.changed","user_id":"$userId","tier":"premium","status":"active"}"""

            every { userService.updateSubscriptionTier(userId, "premium") } just Runs

            consumer.handleSubscriptionChanged(message)

            verify(exactly = 1) { userService.updateSubscriptionTier(userId, "premium") }
        }

        @Test
        fun `should not throw when user does not exist`() {
            val userId = UUID.randomUUID()
            val message = """{"event_type":"subscription.changed","user_id":"$userId","tier":"free","status":"cancelled"}"""

            every { userService.updateSubscriptionTier(userId, "free") } throws
                IllegalArgumentException("User not found: $userId")

            // Should not propagate exception -- consumer must be resilient
            consumer.handleSubscriptionChanged(message)

            verify(exactly = 1) { userService.updateSubscriptionTier(userId, "free") }
        }

        @Test
        fun `should handle downgrade from premium to free`() {
            val userId = UUID.randomUUID()
            val message = """{"event_type":"subscription.changed","user_id":"$userId","tier":"free","status":"cancelled"}"""

            every { userService.updateSubscriptionTier(userId, "free") } just Runs

            consumer.handleSubscriptionChanged(message)

            verify(exactly = 1) { userService.updateSubscriptionTier(userId, "free") }
        }
    }
}
