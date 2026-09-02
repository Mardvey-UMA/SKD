package com.contentplatform.auth.quartz

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CleanupPublishedOutboxJob(
    private val jdbcTemplate: JdbcTemplate
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanupPublishedOutboxJob::class.java)
    }

    override fun execute(context: JobExecutionContext?) {
        val deleted = jdbcTemplate.update(
            "DELETE FROM outbox WHERE published_at IS NOT NULL AND published_at < now() - INTERVAL '3 days'"
        )
        log.info("Deleted {} published outbox entries", deleted)
    }
}
