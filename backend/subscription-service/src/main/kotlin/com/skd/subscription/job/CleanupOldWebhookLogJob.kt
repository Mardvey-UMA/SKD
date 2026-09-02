package com.skd.subscription.job

import com.skd.subscription.db.repository.webhook.WebhookLogRepository
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@DisallowConcurrentExecution
class CleanupOldWebhookLogJob(
    private val webhookLogRepository: WebhookLogRepository
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanupOldWebhookLogJob::class.java)
        private val RETENTION = Duration.ofDays(30)
    }

    override fun execute(context: JobExecutionContext) {
        val cutoff = Instant.now().minus(RETENTION)
        val deleted = webhookLogRepository.deleteReceivedBefore(cutoff)
        log.info("Deleted {} webhook log entries older than {} days", deleted, RETENTION.toDays())
    }
}
