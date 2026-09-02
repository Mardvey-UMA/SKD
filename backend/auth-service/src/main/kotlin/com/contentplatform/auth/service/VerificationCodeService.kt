package com.contentplatform.auth.service

import com.contentplatform.auth.configuration.AuthProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

sealed interface VerifyResult {
    data class Success(val userId: UUID) : VerifyResult
    data object InvalidCode : VerifyResult
    data class TooManyAttempts(val retryAfterSeconds: Long) : VerifyResult
}

sealed interface ResendEligibility {
    data object Allowed : ResendEligibility
    data class Cooldown(val retryAfterSeconds: Long) : ResendEligibility
}

@Service
class VerificationCodeService(
    private val redisTemplate: StringRedisTemplate,
    private val authProperties: AuthProperties,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(VerificationCodeService::class.java)
    }

    private data class CodeRecord(val code: String, val userId: String)

    /**
     * Generates a 6-digit verification code and stores it in Valkey.
     * In dev mode always returns "000000". Resets the attempts counter.
     */
    fun generateAndStore(email: String, userId: UUID): String {
        val code = if (authProperties.devMode) "000000"
        else Random.nextInt(0, 1_000_000).toString().padStart(6, '0')

        val record = CodeRecord(code = code, userId = userId.toString())
        val json = objectMapper.writeValueAsString(record)

        redisTemplate.opsForValue().set(
            "verify:code:$email",
            json,
            authProperties.verificationCodeTtlSeconds,
            TimeUnit.SECONDS
        )
        // Reset attempts counter so a fresh code gets a fresh attempt window
        redisTemplate.delete("verify:attempts:$email")

        log.debug("Generated verification code for email={}, devMode={}", email, authProperties.devMode)
        return code
    }

    /**
     * Checks the submitted code against the stored one.
     * Increments the attempts counter on every call.
     * Returns Success (and deletes key), InvalidCode, or TooManyAttempts.
     */
    fun verifyCode(email: String, submittedCode: String): VerifyResult {
        val attemptsKey = "verify:attempts:$email"
        val attempts = redisTemplate.opsForValue().increment(attemptsKey) ?: 1L
        if (attempts == 1L) {
            redisTemplate.expire(attemptsKey, authProperties.verificationAttemptsWindowSeconds, TimeUnit.SECONDS)
        }

        if (attempts > authProperties.verificationCodeMaxAttempts) {
            val ttl = redisTemplate.getExpire(attemptsKey, TimeUnit.SECONDS)
            return VerifyResult.TooManyAttempts(maxOf(ttl, 0L))
        }

        val json = redisTemplate.opsForValue().get("verify:code:$email")
            ?: return VerifyResult.InvalidCode

        val record = objectMapper.readValue(json, CodeRecord::class.java)

        // Constant-time comparison to prevent timing attacks
        val match = MessageDigest.isEqual(
            submittedCode.toByteArray(Charsets.UTF_8),
            record.code.toByteArray(Charsets.UTF_8)
        )

        return if (match) {
            redisTemplate.delete("verify:code:$email")
            VerifyResult.Success(UUID.fromString(record.userId))
        } else {
            VerifyResult.InvalidCode
        }
    }

    /**
     * Returns Allowed if no cooldown key exists, Cooldown with remaining TTL otherwise.
     */
    fun canResend(email: String): ResendEligibility {
        val ttl = redisTemplate.getExpire("verify:resend:$email", TimeUnit.SECONDS)
        return if (ttl > 0L) ResendEligibility.Cooldown(ttl)
        else ResendEligibility.Allowed
    }

    /**
     * Sets the resend cooldown key in Valkey.
     */
    fun markResent(email: String) {
        redisTemplate.opsForValue().set(
            "verify:resend:$email",
            "1",
            authProperties.verificationResendCooldownSeconds,
            TimeUnit.SECONDS
        )
    }
}
