package com.contentplatform.auth.quartz

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CleanupExpiredRefreshTokensJob(
    private val jdbcTemplate: JdbcTemplate
) : Job {

    companion object {
        private val log = LoggerFactory.getLogger(CleanupExpiredRefreshTokensJob::class.java)
    }

    override fun execute(context: JobExecutionContext?) {
        val deleted = jdbcTemplate.update(
            "DELETE FROM refresh_tokens WHERE expires_at < now()"
        )
        log.info("Deleted {} expired refresh tokens", deleted)
    }
}
