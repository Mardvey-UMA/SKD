package com.contentplatform.auth.unit.quartz

import com.contentplatform.auth.quartz.CleanupExpiredRefreshTokensJob
import com.contentplatform.auth.quartz.CleanupExpiredResetTokensJob
import com.contentplatform.auth.quartz.CleanupExpiredVerificationTokensJob
import com.contentplatform.auth.quartz.CleanupPublishedOutboxJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

@Tag("unit")
class CleanupJobsTest {

    private val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)

    @Nested
    inner class `CleanupExpiredVerificationTokensJob` {

        private val job = CleanupExpiredVerificationTokensJob(jdbcTemplate)

        @Test
        fun `should delete verification tokens expired more than 7 days ago`() {
            every { jdbcTemplate.update(any<String>()) } returns 3

            job.execute(null)

            verify(exactly = 1) {
                jdbcTemplate.update(match<String> { sql ->
                    sql.contains("email_verification_tokens") &&
                        sql.contains("expires_at") &&
                        sql.contains("7 days")
                })
            }
        }
    }

    @Nested
    inner class `CleanupExpiredResetTokensJob` {

        private val job = CleanupExpiredResetTokensJob(jdbcTemplate)

        @Test
        fun `should delete reset tokens expired more than 1 day ago`() {
            every { jdbcTemplate.update(any<String>()) } returns 2

            job.execute(null)

            verify(exactly = 1) {
                jdbcTemplate.update(match<String> { sql ->
                    sql.contains("password_reset_tokens") &&
                        sql.contains("expires_at") &&
                        sql.contains("1 day")
                })
            }
        }
    }

    @Nested
    inner class `CleanupExpiredRefreshTokensJob` {

        private val job = CleanupExpiredRefreshTokensJob(jdbcTemplate)

        @Test
        fun `should delete refresh tokens that have expired`() {
            every { jdbcTemplate.update(any<String>()) } returns 5

            job.execute(null)

            verify(exactly = 1) {
                jdbcTemplate.update(match<String> { sql ->
                    sql.contains("refresh_tokens") &&
                        sql.contains("expires_at")
                })
            }
        }
    }

    @Nested
    inner class `CleanupPublishedOutboxJob` {

        private val job = CleanupPublishedOutboxJob(jdbcTemplate)

        @Test
        fun `should delete outbox entries published more than 3 days ago`() {
            every { jdbcTemplate.update(any<String>()) } returns 10

            job.execute(null)

            verify(exactly = 1) {
                jdbcTemplate.update(match<String> { sql ->
                    sql.contains("outbox") &&
                        sql.contains("published_at") &&
                        sql.contains("3 days")
                })
            }
        }
    }
}
