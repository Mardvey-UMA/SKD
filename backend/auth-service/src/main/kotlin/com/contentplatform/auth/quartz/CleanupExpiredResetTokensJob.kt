package com.contentplatform.auth.quartz

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CleanupExpiredResetTokensJob(
    private val jdbcTemplate: JdbcTemplate
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanupExpiredResetTokensJob::class.java)
    }

    override fun execute(context: JobExecutionContext?) {
        val deleted = jdbcTemplate.update(
            "DELETE FROM password_reset_tokens WHERE expires_at < now() - INTERVAL '1 day'"
        )
        log.info("Deleted {} expired reset tokens", deleted)
    }
}
