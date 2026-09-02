package com.skd.subscription.unit

import com.skd.subscription.db.repository.subscription.model.Subscription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@Tag("unit")
class SubscriptionEntityTest {

    @Test
    fun `should create subscription with all fields`() {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        val expiresAt = now.plusSeconds(30 * 24 * 3600L)

        val subscription = Subscription(
            id = UUID.randomUUID(),
            userId = userId,
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = now,
            expiresAt = expiresAt,
            autoRenew = true
        )

        assertThat(subscription.userId).isEqualTo(userId)
        assertThat(subscription.planId).isEqualTo("premium_monthly")
        assertThat(subscription.tier).isEqualTo("premium")
        assertThat(subscription.status).isEqualTo("active")
        assertThat(subscription.autoRenew).isTrue()
    }

    @Test
    fun `should default autoRenew to true`() {
        val subscription = Subscription(
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(30 * 24 * 3600L)
        )

        assertThat(subscription.autoRenew).isTrue()
    }

    @Test
    fun `should allow null id for new subscriptions`() {
        val subscription = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_yearly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(365 * 24 * 3600L)
        )

        assertThat(subscription.id).isNull()
    }
}
