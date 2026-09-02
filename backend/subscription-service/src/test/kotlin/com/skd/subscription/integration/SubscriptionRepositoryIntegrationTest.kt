package com.skd.subscription.integration

import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Tag("integration")
@Transactional
class SubscriptionRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var subscriptionRepository: SubscriptionRepository

    @Test
    fun `should save and find subscription by userId`() {
        val userId = UUID.randomUUID()
        val sub = Subscription(
            id = null,
            userId = userId,
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now(),
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
            autoRenew = true
        )

        subscriptionRepository.save(sub)

        val found = subscriptionRepository.findByUserId(userId)
        assertThat(found).isNotNull
        assertThat(found!!.userId).isEqualTo(userId)
        assertThat(found.tier).isEqualTo("premium")
        assertThat(found.status).isEqualTo("active")
    }

    @Test
    fun `should return null when no subscription for userId`() {
        val found = subscriptionRepository.findByUserId(UUID.randomUUID())

        assertThat(found).isNull()
    }

    @Test
    fun `should find expired active subscriptions`() {
        val expiredSub = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now().minus(31, ChronoUnit.DAYS),
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
            autoRenew = false
        )
        subscriptionRepository.save(expiredSub)

        val activeSub = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_yearly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now(),
            expiresAt = Instant.now().plus(365, ChronoUnit.DAYS),
            autoRenew = true
        )
        subscriptionRepository.save(activeSub)

        val expired = subscriptionRepository.findExpiredActive(Instant.now())

        assertThat(expired).hasSize(1)
        assertThat(expired[0].userId).isEqualTo(expiredSub.userId)
    }

    @Test
    fun `should find subscriptions expiring soon with autoRenew enabled`() {
        val expiringSub = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now().minus(29, ChronoUnit.DAYS),
            expiresAt = Instant.now().plus(12, ChronoUnit.HOURS),
            autoRenew = true
        )
        subscriptionRepository.save(expiringSub)

        val notExpiringSub = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_yearly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now(),
            expiresAt = Instant.now().plus(200, ChronoUnit.DAYS),
            autoRenew = true
        )
        subscriptionRepository.save(notExpiringSub)

        val noAutoRenew = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now().minus(29, ChronoUnit.DAYS),
            expiresAt = Instant.now().plus(12, ChronoUnit.HOURS),
            autoRenew = false
        )
        subscriptionRepository.save(noAutoRenew)

        val withinOneDay = Instant.now().plus(1, ChronoUnit.DAYS)
        val found = subscriptionRepository.findExpiringWithAutoRenew(Instant.now(), withinOneDay)

        assertThat(found).hasSize(1)
        assertThat(found[0].userId).isEqualTo(expiringSub.userId)
        assertThat(found[0].autoRenew).isTrue()
    }

    @Test
    fun `should not include already expired subscriptions in expiring soon query`() {
        val alreadyExpired = Subscription(
            id = null,
            userId = UUID.randomUUID(),
            planId = "premium_monthly",
            tier = "premium",
            status = "active",
            startedAt = Instant.now().minus(31, ChronoUnit.DAYS),
            expiresAt = Instant.now().minus(2, ChronoUnit.HOURS),
            autoRenew = true
        )
        subscriptionRepository.save(alreadyExpired)

        val withinOneDay = Instant.now().plus(1, ChronoUnit.DAYS)
        val found = subscriptionRepository.findExpiringWithAutoRenew(Instant.now(), withinOneDay)

        assertThat(found).isEmpty()
    }
}
