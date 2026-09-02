package com.skd.subscription.unit

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import com.skd.subscription.db.repository.subscription.model.Subscription
import com.skd.subscription.job.CheckExpiredSubscriptionsJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import java.time.Instant
import java.util.UUID

@Tag("unit")
class CheckExpiredSubscriptionsJobTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)

    private val job = CheckExpiredSubscriptionsJob(
        subscriptionRepository = subscriptionRepository,
        outboxRepository = outboxRepository
    )

    private val jobContext = mockk<JobExecutionContext>(relaxed = true)

    @Nested
    inner class `execute` {

        @Test
        fun `should find expired active subscriptions and mark them as expired`() {
            val userId = UUID.randomUUID()
            val subscription = createSubscription(
                userId = userId,
                status = "active",
                expiresAt = Instant.now().minusSeconds(7200), // expired 2h ago
                autoRenew = false
            )

            every { subscriptionRepository.findExpiredActive(any()) } returns listOf(subscription)
            val savedSlot = slot<Subscription>()
            every { subscriptionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            job.execute(jobContext)

            assertThat(savedSlot.captured.status).isEqualTo("expired")
            assertThat(savedSlot.captured.userId).isEqualTo(userId)
            verify(exactly = 1) { subscriptionRepository.save(any()) }
        }

        @Test
        fun `should write outbox event for each expired subscription`() {
            val userId = UUID.randomUUID()
            val subscription = createSubscription(
                userId = userId,
                status = "active",
                expiresAt = Instant.now().minusSeconds(7200),
                autoRenew = false
            )

            every { subscriptionRepository.findExpiredActive(any()) } returns listOf(subscription)
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            val outboxSlot = slot<OutboxEntry>()
            every { outboxRepository.save(capture(outboxSlot)) } answers { outboxSlot.captured }

            job.execute(jobContext)

            assertThat(outboxSlot.captured.aggregateId).isEqualTo(userId.toString())
            assertThat(outboxSlot.captured.eventType).isEqualTo("subscription.changed")
            assertThat(outboxSlot.captured.aggregateType).isEqualTo("Subscription")
            verify(exactly = 1) { outboxRepository.save(any()) }
        }

        @Test
        fun `should skip subscription with auto_renew true that expired less than 3 hours ago`() {
            val subscription = createSubscription(
                userId = UUID.randomUUID(),
                status = "active",
                expiresAt = Instant.now().minusSeconds(3600), // expired 1h ago, within 3h grace
                autoRenew = true
            )

            every { subscriptionRepository.findExpiredActive(any()) } returns listOf(subscription)

            job.execute(jobContext)

            verify(exactly = 0) { subscriptionRepository.save(any()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `should expire subscription with auto_renew true that expired more than 3 hours ago`() {
            val userId = UUID.randomUUID()
            val subscription = createSubscription(
                userId = userId,
                status = "active",
                expiresAt = Instant.now().minusSeconds(4 * 3600), // expired 4h ago, beyond 3h grace
                autoRenew = true
            )

            every { subscriptionRepository.findExpiredActive(any()) } returns listOf(subscription)
            val savedSlot = slot<Subscription>()
            every { subscriptionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            job.execute(jobContext)

            assertThat(savedSlot.captured.status).isEqualTo("expired")
            verify(exactly = 1) { subscriptionRepository.save(any()) }
            verify(exactly = 1) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `should handle multiple expired subscriptions independently`() {
            val sub1 = createSubscription(
                userId = UUID.randomUUID(),
                status = "active",
                expiresAt = Instant.now().minusSeconds(7200),
                autoRenew = false
            )
            val sub2 = createSubscription(
                userId = UUID.randomUUID(),
                status = "active",
                expiresAt = Instant.now().minusSeconds(10800),
                autoRenew = false
            )

            every { subscriptionRepository.findExpiredActive(any()) } returns listOf(sub1, sub2)
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            job.execute(jobContext)

            verify(exactly = 2) { subscriptionRepository.save(any()) }
            verify(exactly = 2) { outboxRepository.save(any<OutboxEntry>()) }
        }

        @Test
        fun `should do nothing when no expired subscriptions exist`() {
            every { subscriptionRepository.findExpiredActive(any()) } returns emptyList()

            job.execute(jobContext)

            verify(exactly = 0) { subscriptionRepository.save(any()) }
            verify(exactly = 0) { outboxRepository.save(any<OutboxEntry>()) }
        }
    }

    private fun createSubscription(
        userId: UUID = UUID.randomUUID(),
        status: String = "active",
        expiresAt: Instant = Instant.now().plusSeconds(86400),
        autoRenew: Boolean = true
    ) = Subscription(
        id = UUID.randomUUID(),
        userId = userId,
        planId = "premium_monthly",
        tier = "premium",
        status = status,
        startedAt = Instant.now().minusSeconds(86400 * 30L),
        expiresAt = expiresAt,
        autoRenew = autoRenew
    )
}
