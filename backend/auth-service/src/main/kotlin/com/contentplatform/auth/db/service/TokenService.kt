package com.contentplatform.auth.db.service

import com.contentplatform.auth.configuration.JwtProperties
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.model.RefreshTokenEntity
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties
) {

    companion object {
        private val log = LoggerFactory.getLogger(TokenService::class.java)
        private val secureRandom = SecureRandom()
    }

    fun generateAccessToken(userId: UUID, roles: List<String>, subscriptionTier: String): String {
        val now = Instant.now()
        val expiresAt = now.plus(jwtProperties.accessTokenTtl, ChronoUnit.SECONDS)

        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuer(jwtProperties.issuer)
            .audience(listOf(jwtProperties.audience))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .claim("subscription_tier", subscriptionTier)
            .build()

        val jwt = jwtEncoder.encode(JwtEncoderParameters.from(claims))
        log.debug("Generated access token for userId={}", userId)
        return jwt.tokenValue
    }

    fun generateRefreshToken(userId: UUID): String {
        val rawBytes = ByteArray(32)
        secureRandom.nextBytes(rawBytes)
        val rawToken = rawBytes.joinToString("") { "%02x".format(it) }

        val tokenHash = sha256Hex(rawToken)
        val expiresAt = Instant.now().plus(jwtProperties.refreshTokenTtl, ChronoUnit.SECONDS)

        val entity = RefreshTokenEntity(
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = expiresAt
        )
        refreshTokenRepository.save(entity)

        log.debug("Generated refresh token for userId={}", userId)
        return rawToken
    }

    fun validateRefreshToken(rawToken: String): RefreshTokenEntity {
        val tokenHash = sha256Hex(rawToken)
        val entity = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw IllegalArgumentException("Refresh token not found")

        if (entity.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Refresh token has expired")
        }

        return entity
    }

    fun deleteRefreshToken(id: UUID) {
        refreshTokenRepository.deleteById(id)
    }

    fun deleteAllRefreshTokens(userId: UUID) {
        refreshTokenRepository.deleteByUserId(userId)
    }

    fun revokeAccessToken(jti: String, expiresAt: Instant) {
        val ttlSeconds = Instant.now().until(expiresAt, ChronoUnit.SECONDS)
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set("revoked:$jti", "1", ttlSeconds, TimeUnit.SECONDS)
            log.debug("Revoked access token jti={}, ttl={}s", jti, ttlSeconds)
        }
    }

    fun isAccessTokenRevoked(jti: String): Boolean {
        return redisTemplate.hasKey("revoked:$jti") == true
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
