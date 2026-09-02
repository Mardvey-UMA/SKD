package com.contentplatform.auth.integration.service

import com.contentplatform.auth.integration.IntegrationTestBase
import com.contentplatform.auth.service.ResendEligibility
import com.contentplatform.auth.service.VerificationCodeService
import com.contentplatform.auth.service.VerifyResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID

@Tag("integration")
class VerificationCodeServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var verificationCodeService: VerificationCodeService

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    private val testEmail = "integration-code-test@example.com"

    @BeforeEach
    fun cleanUpRedis() {
        redisTemplate.delete("verify:code:$testEmail")
        redisTemplate.delete("verify:attempts:$testEmail")
        redisTemplate.delete("verify:resend:$testEmail")
    }

    @Nested
    inner class `generateAndStore in dev mode` {

        @Test
        fun `stores 000000 in redis with TTL and returns it`() {
            // application-test.yml sets dev-mode: true
            val userId = UUID.randomUUID()

            val code = verificationCodeService.generateAndStore(testEmail, userId)

            assertThat(code).isEqualTo("000000")
            val storedJson = redisTemplate.opsForValue().get("verify:code:$testEmail")
            assertThat(storedJson).isNotNull
            assertThat(storedJson).contains("000000")
            assertThat(storedJson).contains(userId.toString())
        }

        @Test
        fun `resets attempts counter in redis`() {
            val userId = UUID.randomUUID()
            // Pre-populate an attempts counter
            redisTemplate.opsForValue().set("verify:attempts:$testEmail", "3")

            verificationCodeService.generateAndStore(testEmail, userId)

            val attemptsValue = redisTemplate.opsForValue().get("verify:attempts:$testEmail")
            assertThat(attemptsValue).isNull()
        }
    }

    @Nested
    inner class `verifyCode with real redis` {

        @Test
        fun `returns Success on correct code`() {
            val userId = UUID.randomUUID()
            verificationCodeService.generateAndStore(testEmail, userId)

            val result = verificationCodeService.verifyCode(testEmail, "000000")

            assertThat(result).isInstanceOf(VerifyResult.Success::class.java)
            assertThat((result as VerifyResult.Success).userId).isEqualTo(userId)
        }

        @Test
        fun `deletes code key from redis on success`() {
            val userId = UUID.randomUUID()
            verificationCodeService.generateAndStore(testEmail, userId)

            verificationCodeService.verifyCode(testEmail, "000000")

            val remaining = redisTemplate.opsForValue().get("verify:code:$testEmail")
            assertThat(remaining).isNull()
        }

        @Test
        fun `returns InvalidCode on wrong code`() {
            val userId = UUID.randomUUID()
            verificationCodeService.generateAndStore(testEmail, userId)

            val result = verificationCodeService.verifyCode(testEmail, "999999")

            assertThat(result).isEqualTo(VerifyResult.InvalidCode)
        }

        @Test
        fun `returns InvalidCode when no code exists`() {
            val result = verificationCodeService.verifyCode(testEmail, "000000")

            assertThat(result).isEqualTo(VerifyResult.InvalidCode)
        }

        @Test
        fun `returns TooManyAttempts after exceeding 5 wrong attempts`() {
            val userId = UUID.randomUUID()
            verificationCodeService.generateAndStore(testEmail, userId)

            // Make 5 failed attempts
            repeat(5) { verificationCodeService.verifyCode(testEmail, "999999") }

            // 6th attempt should be rate-limited
            val result = verificationCodeService.verifyCode(testEmail, "999999")

            assertThat(result).isInstanceOf(VerifyResult.TooManyAttempts::class.java)
            assertThat((result as VerifyResult.TooManyAttempts).retryAfterSeconds).isGreaterThan(0)
        }
    }

    @Nested
    inner class `canResend and markResent` {

        @Test
        fun `canResend returns Allowed when no cooldown key`() {
            val result = verificationCodeService.canResend(testEmail)

            assertThat(result).isEqualTo(ResendEligibility.Allowed)
        }

        @Test
        fun `canResend returns Cooldown after markResent`() {
            verificationCodeService.markResent(testEmail)

            val result = verificationCodeService.canResend(testEmail)

            assertThat(result).isInstanceOf(ResendEligibility.Cooldown::class.java)
            assertThat((result as ResendEligibility.Cooldown).retryAfterSeconds).isGreaterThan(0)
        }
    }
}
