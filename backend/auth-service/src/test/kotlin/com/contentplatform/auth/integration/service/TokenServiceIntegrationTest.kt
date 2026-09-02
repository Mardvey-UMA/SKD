package com.contentplatform.auth.integration.service

import com.contentplatform.auth.configuration.JwtProperties
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.UserEntity
import com.contentplatform.auth.db.service.TokenService
import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Tag("integration")
class TokenServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var jwtProperties: JwtProperties

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var testUser: UserEntity

    @BeforeEach
    fun setUp() {
        // Clean DB
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")

        // Clean Redis
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()

        testUser = userRepository.save(
            UserEntity(email = "token-test@example.com", passwordHash = "hashed-pw")
        )
    }

    @Nested
    inner class `access token generation and decoding` {

        @Test
        fun `should generate a JWT that can be decoded with the service JwtDecoder`() {
            val tokenValue = tokenService.generateAccessToken(
                userId = testUser.id!!,
                roles = listOf("USER"),
                subscriptionTier = "free"
            )

            val decoded = jwtDecoder.decode(tokenValue)

            assertThat(decoded.subject).isEqualTo(testUser.id.toString())
            assertThat(decoded.getClaim<String>("iss")).isEqualTo(jwtProperties.issuer)
            assertThat(decoded.audience).contains(jwtProperties.audience)
            assertThat(decoded.getClaim<List<String>>("roles")).contains("USER")
            assertThat(decoded.getClaim<String>("subscription_tier")).isEqualTo("free")
            assertThat(decoded.getClaim<String>("jti")).isNotBlank()
        }

        @Test
        fun `should generate JWT with expiration in the future`() {
            val tokenValue = tokenService.generateAccessToken(
                userId = testUser.id!!,
                roles = listOf("USER"),
                subscriptionTier = "premium"
            )

            val decoded = jwtDecoder.decode(tokenValue)

            assertThat(decoded.expiresAt).isAfter(Instant.now())
            assertThat(decoded.issuedAt).isBeforeOrEqualTo(Instant.now().plus(1, ChronoUnit.SECONDS))
        }

        @Test
        fun `should include subscription_tier claim matching the provided value`() {
            val tokenValue = tokenService.generateAccessToken(
                userId = testUser.id!!,
                roles = listOf("USER"),
                subscriptionTier = "premium"
            )

            val decoded = jwtDecoder.decode(tokenValue)

            assertThat(decoded.getClaim<String>("subscription_tier")).isEqualTo("premium")
        }
    }

    @Nested
    inner class `refresh token lifecycle` {

        @Test
        fun `should generate a refresh token and validate it successfully`() {
            val rawToken = tokenService.generateRefreshToken(testUser.id!!)

            val entity = tokenService.validateRefreshToken(rawToken)

            assertThat(entity.userId).isEqualTo(testUser.id)
            assertThat(entity.id).isNotNull()
        }

        @Test
        fun `should fail validation after refresh token is deleted`() {
            val rawToken = tokenService.generateRefreshToken(testUser.id!!)
            val entity = tokenService.validateRefreshToken(rawToken)

            tokenService.deleteRefreshToken(entity.id!!)

            assertThatThrownBy { tokenService.validateRefreshToken(rawToken) }
                .isInstanceOf(Exception::class.java)
        }

        @Test
        fun `should delete all refresh tokens for a user`() {
            val rawToken1 = tokenService.generateRefreshToken(testUser.id!!)
            val rawToken2 = tokenService.generateRefreshToken(testUser.id!!)

            tokenService.deleteAllRefreshTokens(testUser.id!!)

            assertThatThrownBy { tokenService.validateRefreshToken(rawToken1) }
                .isInstanceOf(Exception::class.java)
            assertThatThrownBy { tokenService.validateRefreshToken(rawToken2) }
                .isInstanceOf(Exception::class.java)
        }

        @Test
        fun `should store refresh token hash in DB not the raw token`() {
            val rawToken = tokenService.generateRefreshToken(testUser.id!!)

            // The raw token itself should NOT be stored as-is
            val found = refreshTokenRepository.findByTokenHash(rawToken)
            assertThat(found).isNull()

            // But validation (which hashes internally) should find it
            val entity = tokenService.validateRefreshToken(rawToken)
            assertThat(entity).isNotNull
        }
    }

    @Nested
    inner class `access token revocation via Redis` {

        @Test
        fun `should mark jti as revoked and confirm revocation status`() {
            val jti = UUID.randomUUID().toString()
            val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)

            tokenService.revokeAccessToken(jti, expiresAt)

            assertThat(tokenService.isAccessTokenRevoked(jti)).isTrue()
        }

        @Test
        fun `should return false for non-revoked jti`() {
            val jti = UUID.randomUUID().toString()

            assertThat(tokenService.isAccessTokenRevoked(jti)).isFalse()
        }

        @Test
        fun `should store revocation key with TTL in Redis`() {
            val jti = UUID.randomUUID().toString()
            val expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES)

            tokenService.revokeAccessToken(jti, expiresAt)

            val ttl = redisTemplate.getExpire("revoked:$jti")
            assertThat(ttl).isGreaterThan(0)
            assertThat(ttl).isLessThanOrEqualTo(300)
        }
    }
}
