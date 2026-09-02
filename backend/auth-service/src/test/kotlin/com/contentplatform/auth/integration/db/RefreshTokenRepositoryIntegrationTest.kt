package com.contentplatform.auth.integration.db

import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.RefreshTokenEntity
import com.contentplatform.auth.db.repository.model.UserEntity
import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Tag("integration")
class RefreshTokenRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testUser: UserEntity

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")

        testUser = userRepository.save(
            UserEntity(email = "tokenuser@example.com", passwordHash = "hash")
        )
    }

    @Nested
    inner class `save and find` {

        @Test
        fun `should save refresh token and generate id`() {
            val token = RefreshTokenEntity(
                userId = testUser.id!!,
                tokenHash = "sha256-hash-value",
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )

            val saved = refreshTokenRepository.save(token)

            assertThat(saved.id).isNotNull()
            assertThat(saved.tokenHash).isEqualTo("sha256-hash-value")
            assertThat(saved.userId).isEqualTo(testUser.id)
        }

        @Test
        fun `should find refresh token by token hash`() {
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = testUser.id!!,
                    tokenHash = "find-by-hash",
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
            )

            val found = refreshTokenRepository.findByTokenHash("find-by-hash")

            assertThat(found).isNotNull
            assertThat(found!!.tokenHash).isEqualTo("find-by-hash")
            assertThat(found.userId).isEqualTo(testUser.id)
        }

        @Test
        fun `should return null when token hash not found`() {
            val found = refreshTokenRepository.findByTokenHash("nonexistent-hash")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `deleteByUserId` {

        @Test
        fun `should delete all refresh tokens for a user`() {
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = testUser.id!!,
                    tokenHash = "hash-1",
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
            )
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = testUser.id!!,
                    tokenHash = "hash-2",
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
            )

            refreshTokenRepository.deleteByUserId(testUser.id!!)

            assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isNull()
            assertThat(refreshTokenRepository.findByTokenHash("hash-2")).isNull()
        }

        @Test
        fun `should not delete tokens belonging to other users`() {
            val otherUser = userRepository.save(
                UserEntity(email = "other@example.com", passwordHash = "hash")
            )

            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = testUser.id!!,
                    tokenHash = "user1-hash",
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
            )
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = otherUser.id!!,
                    tokenHash = "user2-hash",
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
            )

            refreshTokenRepository.deleteByUserId(testUser.id!!)

            assertThat(refreshTokenRepository.findByTokenHash("user1-hash")).isNull()
            assertThat(refreshTokenRepository.findByTokenHash("user2-hash")).isNotNull
        }
    }

    @Nested
    inner class `deleteExpired` {

        @Test
        fun `should delete expired tokens`() {
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = testUser.id!!,
                    tokenHash = "expired-hash",
                    expiresAt = Instant.now().minus(1, ChronoUnit.DAYS)
                )
            )
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = testUser.id!!,
                    tokenHash = "valid-hash",
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
            )

            val deletedCount = refreshTokenRepository.deleteExpired()

            assertThat(deletedCount).isEqualTo(1)
            assertThat(refreshTokenRepository.findByTokenHash("expired-hash")).isNull()
            assertThat(refreshTokenRepository.findByTokenHash("valid-hash")).isNotNull
        }
    }
}
