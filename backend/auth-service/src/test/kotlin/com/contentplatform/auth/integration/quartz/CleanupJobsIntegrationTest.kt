package com.contentplatform.auth.integration.quartz

import com.contentplatform.auth.integration.IntegrationTestBase
import com.contentplatform.auth.quartz.CleanupExpiredRefreshTokensJob
import com.contentplatform.auth.quartz.CleanupExpiredResetTokensJob
import com.contentplatform.auth.quartz.CleanupExpiredVerificationTokensJob
import com.contentplatform.auth.quartz.CleanupPublishedOutboxJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Tag("integration")
class CleanupJobsIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var cleanupExpiredVerificationTokensJob: CleanupExpiredVerificationTokensJob

    @Autowired
    lateinit var cleanupExpiredResetTokensJob: CleanupExpiredResetTokensJob

    @Autowired
    lateinit var cleanupExpiredRefreshTokensJob: CleanupExpiredRefreshTokensJob

    @Autowired
    lateinit var cleanupPublishedOutboxJob: CleanupPublishedOutboxJob

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")

        testUserId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
            testUserId, "cleanup-test-${testUserId}@example.com", "hashed-password"
        )
    }

    @Nested
    inner class `CleanupExpiredVerificationTokens` {

        @Test
        fun `should delete verification tokens expired more than 7 days ago`() {
            // Expired 10 days ago -- should be deleted
            jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, now() - INTERVAL '10 days', now() - INTERVAL '11 days')",
                "expired-old-token", testUserId
            )
            // Expired 2 days ago -- should NOT be deleted (within 7-day grace period)
            jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, now() - INTERVAL '2 days', now() - INTERVAL '3 days')",
                "expired-recent-token", testUserId
            )
            // Not yet expired -- should NOT be deleted
            jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, now() + INTERVAL '1 day', now())",
                "valid-token", testUserId
            )

            cleanupExpiredVerificationTokensJob.execute(null)

            val remaining = jdbcTemplate.queryForList(
                "SELECT token FROM email_verification_tokens ORDER BY token"
            ).map { it["token"] as String }

            assertThat(remaining).containsExactlyInAnyOrder("expired-recent-token", "valid-token")
        }

        @Test
        fun `should not fail when no tokens exist`() {
            cleanupExpiredVerificationTokensJob.execute(null)

            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_verification_tokens", Int::class.java
            )
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    inner class `CleanupExpiredResetTokens` {

        @Test
        fun `should delete reset tokens expired more than 1 day ago`() {
            // Expired 3 days ago -- should be deleted
            jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, now() - INTERVAL '3 days', now() - INTERVAL '4 days')",
                "expired-old-reset", testUserId
            )
            // Expired 6 hours ago -- should NOT be deleted (within 1-day grace period)
            jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, now() - INTERVAL '6 hours', now() - INTERVAL '7 hours')",
                "expired-recent-reset", testUserId
            )
            // Not yet expired -- should NOT be deleted
            jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, now() + INTERVAL '1 hour', now())",
                "valid-reset", testUserId
            )

            cleanupExpiredResetTokensJob.execute(null)

            val remaining = jdbcTemplate.queryForList(
                "SELECT token FROM password_reset_tokens ORDER BY token"
            ).map { it["token"] as String }

            assertThat(remaining).containsExactlyInAnyOrder("expired-recent-reset", "valid-reset")
        }

        @Test
        fun `should not fail when no tokens exist`() {
            cleanupExpiredResetTokensJob.execute(null)

            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM password_reset_tokens", Int::class.java
            )
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    inner class `CleanupExpiredRefreshTokens` {

        @Test
        fun `should delete refresh tokens that have expired`() {
            // Expired 2 days ago -- should be deleted
            jdbcTemplate.update(
                "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, now() - INTERVAL '2 days', now() - INTERVAL '32 days')",
                UUID.randomUUID(), testUserId, "hash-expired"
            )
            // Not yet expired -- should NOT be deleted
            jdbcTemplate.update(
                "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, now() + INTERVAL '10 days', now())",
                UUID.randomUUID(), testUserId, "hash-valid"
            )

            cleanupExpiredRefreshTokensJob.execute(null)

            val remaining = jdbcTemplate.queryForList(
                "SELECT token_hash FROM refresh_tokens"
            ).map { it["token_hash"] as String }

            assertThat(remaining).containsExactly("hash-valid")
        }

        @Test
        fun `should delete all expired tokens when multiple exist`() {
            repeat(5) { i ->
                jdbcTemplate.update(
                    "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, now() - INTERVAL '1 day')",
                    UUID.randomUUID(), testUserId, "hash-expired-$i"
                )
            }
            jdbcTemplate.update(
                "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, now() + INTERVAL '1 day')",
                UUID.randomUUID(), testUserId, "hash-active"
            )

            cleanupExpiredRefreshTokensJob.execute(null)

            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens", Int::class.java
            )
            assertThat(count).isEqualTo(1)
        }

        @Test
        fun `should not fail when no tokens exist`() {
            cleanupExpiredRefreshTokensJob.execute(null)

            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens", Int::class.java
            )
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    inner class `CleanupPublishedOutbox` {

        @Test
        fun `should delete outbox entries published more than 3 days ago`() {
            // Published 5 days ago -- should be deleted
            jdbcTemplate.update(
                "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, published_at) VALUES ('User', 'u1', 'user.registered', '{\"id\":\"u1\"}', now() - INTERVAL '5 days')"
            )
            // Published 1 day ago -- should NOT be deleted (within 3-day retention)
            jdbcTemplate.update(
                "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, published_at) VALUES ('User', 'u2', 'user.registered', '{\"id\":\"u2\"}', now() - INTERVAL '1 day')"
            )
            // Not yet published -- should NOT be deleted
            jdbcTemplate.update(
                "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload) VALUES ('User', 'u3', 'user.registered', '{\"id\":\"u3\"}')"
            )

            cleanupPublishedOutboxJob.execute(null)

            val remaining = jdbcTemplate.queryForList(
                "SELECT aggregate_id FROM outbox ORDER BY aggregate_id"
            ).map { it["aggregate_id"] as String }

            assertThat(remaining).containsExactlyInAnyOrder("u2", "u3")
        }

        @Test
        fun `should not delete unpublished entries`() {
            repeat(3) { i ->
                jdbcTemplate.update(
                    "INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload) VALUES ('User', 'unpub-$i', 'user.registered', '{\"id\":\"unpub-$i\"}')"
                )
            }

            cleanupPublishedOutboxJob.execute(null)

            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox", Int::class.java
            )
            assertThat(count).isEqualTo(3)
        }

        @Test
        fun `should not fail when outbox is empty`() {
            cleanupPublishedOutboxJob.execute(null)

            val count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox", Int::class.java
            )
            assertThat(count).isEqualTo(0)
        }
    }
}
