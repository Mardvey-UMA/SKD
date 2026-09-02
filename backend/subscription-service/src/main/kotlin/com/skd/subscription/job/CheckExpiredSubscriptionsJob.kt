package com.skd.subscription.job

import com.skd.subscription.db.repository.outbox.OutboxRepository
import com.skd.subscription.db.repository.outbox.model.OutboxEntry
import com.skd.subscription.db.repository.subscription.SubscriptionRepository
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@DisallowConcurrentExecution
class CheckExpiredSubscriptionsJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val outboxRepository: OutboxRepository
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CheckExpiredSubscriptionsJob::class.java)
        private val GRACE_PERIOD = Duration.ofHours(3)
    }

    override fun execute(context: JobExecutionContext) {
        val now = Instant.now()
        val expired = subscriptionRepository.findExpiredActive(now)

        if (expired.isEmpty()) {
            log.debug("No expired active subscriptions found")
            return
        }

        log.info("Found {} expired active subscriptions to process", expired.size)

        for (subscription in expired) {
            // Apply 3-hour grace period for auto_renew=true subscriptions
            if (subscription.autoRenew) {
                val expiredFor = Duration.between(subscription.expiresAt, now)
                if (expiredFor < GRACE_PERIOD) {
                    log.debug(
                        "Skipping subscription userId={} — within 3h grace period (expired {})",
                        subscription.userId, subscription.expiresAt
                    )
                    continue
                }
            }

            val updated = subscription.copy(status = "expired")
            subscriptionRepository.save(updated)

            val outboxEntry = OutboxEntry(
                aggregateType = "Subscription",
                aggregateId = subscription.userId.toString(),
                eventType = "subscription.changed",
                payload = """{"user_id":"${subscription.userId}","tier":"free","status":"expired","expires_at":"${subscription.expiresAt}","timestamp":"${Instant.now()}"}""",
                createdAt = Instant.now()
            )
            outboxRepository.save(outboxEntry)

            log.info("Marked subscription userId={} as expired", subscription.userId)
        }
    }
}
