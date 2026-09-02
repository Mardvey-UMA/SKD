package com.skd.subscription.job

import com.skd.subscription.db.repository.outbox.OutboxRepository
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@DisallowConcurrentExecution
class CleanupPublishedOutboxJob(
    private val outboxRepository: OutboxRepository
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanupPublishedOutboxJob::class.java)
        private val RETENTION = Duration.ofDays(3)
    }

    override fun execute(context: JobExecutionContext) {
        val cutoff = Instant.now().minus(RETENTION)
        val deleted = outboxRepository.deletePublishedBefore(cutoff)
        log.info("Deleted {} published outbox entries older than {} days", deleted, RETENTION.toDays())
    }
}
