package com.contentplatform.auth.quartz

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CleanupExpiredVerificationTokensJob(
    private val jdbcTemplate: JdbcTemplate
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanupExpiredVerificationTokensJob::class.java)
    }

    override fun execute(context: JobExecutionContext?) {
        val deleted = jdbcTemplate.update(
            "DELETE FROM email_verification_tokens WHERE expires_at < now() - INTERVAL '7 days'"
        )
        log.info("Deleted {} expired verification tokens", deleted)
    }
}
