package com.contentplatform.auth.unit.service

import com.contentplatform.auth.configuration.JwtProperties
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.model.RefreshTokenEntity
import com.contentplatform.auth.db.service.TokenService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("unit")
class TokenServiceTest {

    private val jwtEncoder = mockk<JwtEncoder>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val redisTemplate = mockk<StringRedisTemplate>()
    private val valueOperations = mockk<ValueOperations<String, String>>()
    private val jwtProperties = JwtProperties().apply {
        accessTokenTtl = 900
        refreshTokenTtl = 2592000
        issuer = "auth-service"
        audience = "content-platform"
    }

    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        every { redisTemplate.opsForValue() } returns valueOperations
        tokenService = TokenService(jwtEncoder, refreshTokenRepository, redisTemplate, jwtProperties)
    }

    @Nested
    inner class `generateAccessToken` {

        @Test
        fun `should encode JWT with correct claims and return token string`() {
            val userId = UUID.randomUUID()
            val subscriptionTier = "premium"

            val paramsSlot = slot<JwtEncoderParameters>()
            val mockJwt = mockk<Jwt>()
            every { mockJwt.tokenValue } returns "encoded-jwt-token"
            every { jwtEncoder.encode(capture(paramsSlot)) } returns mockJwt

            val result = tokenService.generateAccessToken(userId, listOf("USER"), subscriptionTier)

            assertThat(result).isEqualTo("encoded-jwt-token")

            val claims = paramsSlot.captured.claims.claims
            assertThat(claims["sub"]).isEqualTo(userId.toString())
            assertThat(claims["iss"]).isEqualTo("auth-service")
            assertThat(claims["aud"]).isEqualTo(listOf("content-platform"))
            assertThat(claims["roles"]).isEqualTo(listOf("USER"))
            assertThat(claims["subscription_tier"]).isEqualTo("premium")
            assertThat(claims["jti"]).isNotNull
        }

        @Test
        fun `should set expiration based on access token TTL from properties`() {
            val userId = UUID.randomUUID()
            val before = Instant.now()

            val paramsSlot = slot<JwtEncoderParameters>()
            val mockJwt = mockk<Jwt>()
            every { mockJwt.tokenValue } returns "token"
            every { jwtEncoder.encode(capture(paramsSlot)) } returns mockJwt

            tokenService.generateAccessToken(userId, listOf("USER"), "free")

            val claims = paramsSlot.captured.claims.claims
            val exp = claims["exp"] as Instant
            val iat = claims["iat"] as Instant

            // exp should be approximately iat + 900 seconds
            assertThat(Duration.between(iat, exp).seconds).isBetween(899L, 901L)
            assertThat(iat).isAfterOrEqualTo(before)
        }
    }

    @Nested
    inner class `generateRefreshToken` {

        @Test
        fun `should save refresh token entity with hashed token to repository`() {
            val userId = UUID.randomUUID()
            val entitySlot = slot<RefreshTokenEntity>()

            every { refreshTokenRepository.save(capture(entitySlot)) } answers {
                entitySlot.captured.copy(id = UUID.randomUUID(), createdAt = Instant.now())
            }

            val rawToken = tokenService.generateRefreshToken(userId)

            assertThat(rawToken).isNotBlank()
            // Raw token should be hex string of sufficient length (64 chars = 32 bytes hex)
            assertThat(rawToken.length).isEqualTo(64)
            assertThat(rawToken).containsPattern("[a-f0-9]+")

            val saved = entitySlot.captured
            assertThat(saved.userId).isEqualTo(userId)
            // Token hash should NOT equal raw token (it's SHA-256 hashed)
            assertThat(saved.tokenHash).isNotEqualTo(rawToken)
            assertThat(saved.tokenHash).isNotBlank()
        }

        @Test
        fun `should set expiration based on refresh token TTL from properties`() {
            val userId = UUID.randomUUID()
            val entitySlot = slot<RefreshTokenEntity>()
            val before = Instant.now()

            every { refreshTokenRepository.save(capture(entitySlot)) } answers {
                entitySlot.captured.copy(id = UUID.randomUUID())
            }

            tokenService.generateRefreshToken(userId)

            val expiresAt = entitySlot.captured.expiresAt
            val expectedExpiry = before.plus(2592000, ChronoUnit.SECONDS)
            // Allow 5 second tolerance
            assertThat(expiresAt).isBetween(
                expectedExpiry.minus(5, ChronoUnit.SECONDS),
                expectedExpiry.plus(5, ChronoUnit.SECONDS)
            )
        }
    }

    @Nested
    inner class `validateRefreshToken` {

        @Test
        fun `should return entity when token is valid and not expired`() {
            val rawToken = "a".repeat(64)
            val entity = RefreshTokenEntity(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                tokenHash = "some-hash",
                expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
            )

            // TokenService will hash the raw token and look it up
            every { refreshTokenRepository.findByTokenHash(any()) } returns entity

            val result = tokenService.validateRefreshToken(rawToken)

            assertThat(result).isEqualTo(entity)
        }

        @Test
        fun `should throw exception when token hash not found in repository`() {
            every { refreshTokenRepository.findByTokenHash(any()) } returns null

            assertThatThrownBy { tokenService.validateRefreshToken("nonexistent-token") }
                .isInstanceOf(Exception::class.java)
        }

        @Test
        fun `should throw exception when token is expired`() {
            val expiredEntity = RefreshTokenEntity(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                tokenHash = "hash",
                expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
            )

            every { refreshTokenRepository.findByTokenHash(any()) } returns expiredEntity

            assertThatThrownBy { tokenService.validateRefreshToken("some-token") }
                .isInstanceOf(Exception::class.java)
        }
    }

    @Nested
    inner class `deleteRefreshToken` {

        @Test
        fun `should delete token by id from repository`() {
            val tokenId = UUID.randomUUID()
            every { refreshTokenRepository.deleteById(tokenId) } returns Unit

            tokenService.deleteRefreshToken(tokenId)

            verify(exactly = 1) { refreshTokenRepository.deleteById(tokenId) }
        }
    }

    @Nested
    inner class `deleteAllRefreshTokens` {

        @Test
        fun `should delete all tokens for a given user id`() {
            val userId = UUID.randomUUID()
            every { refreshTokenRepository.deleteByUserId(userId) } returns Unit

            tokenService.deleteAllRefreshTokens(userId)

            verify(exactly = 1) { refreshTokenRepository.deleteByUserId(userId) }
        }
    }

    @Nested
    inner class `revokeAccessToken` {

        @Test
        fun `should store jti in Redis with correct key and TTL`() {
            val jti = UUID.randomUUID().toString()
            val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)

            every { valueOperations.set(any(), any(), any(), any()) } returns Unit

            tokenService.revokeAccessToken(jti, expiresAt)

            verify {
                valueOperations.set(
                    eq("revoked:$jti"),
                    eq("1"),
                    any<Long>(),
                    eq(TimeUnit.SECONDS)
                )
            }
        }

        @Test
        fun `should calculate TTL as seconds remaining until expiration`() {
            val jti = UUID.randomUUID().toString()
            val expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES)

            val ttlSlot = slot<Long>()
            every { valueOperations.set(any(), any(), capture(ttlSlot), any()) } returns Unit

            tokenService.revokeAccessToken(jti, expiresAt)

            // TTL should be approximately 300 seconds (5 minutes)
            assertThat(ttlSlot.captured).isBetween(295L, 305L)
        }
    }

    @Nested
    inner class `isAccessTokenRevoked` {

        @Test
        fun `should return true when jti exists in Redis`() {
            val jti = UUID.randomUUID().toString()
            every { redisTemplate.hasKey("revoked:$jti") } returns true

            val result = tokenService.isAccessTokenRevoked(jti)

            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when jti does not exist in Redis`() {
            val jti = UUID.randomUUID().toString()
            every { redisTemplate.hasKey("revoked:$jti") } returns false

            val result = tokenService.isAccessTokenRevoked(jti)

            assertThat(result).isFalse()
        }
    }
}
