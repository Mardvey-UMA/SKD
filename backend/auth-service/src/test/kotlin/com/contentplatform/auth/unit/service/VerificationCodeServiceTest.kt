package com.contentplatform.auth.unit.service

import com.contentplatform.auth.configuration.AuthProperties
import com.contentplatform.auth.service.ResendEligibility
import com.contentplatform.auth.service.VerificationCodeService
import com.contentplatform.auth.service.VerifyResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("unit")
class VerificationCodeServiceTest {

    private val redisTemplate = mockk<StringRedisTemplate>()
    private val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
    private val authProperties = AuthProperties().apply {
        verificationCodeTtlSeconds = 600
        verificationCodeMaxAttempts = 5
        verificationAttemptsWindowSeconds = 600
        verificationResendCooldownSeconds = 60
    }
    private val objectMapper = jacksonObjectMapper()

    private val service = VerificationCodeService(redisTemplate, authProperties, objectMapper)

    @BeforeEach
    fun setUp() {
        every { redisTemplate.opsForValue() } returns valueOps
    }

    @Nested
    inner class GenerateAndStore {

        @Test
        fun `dev mode always returns 000000`() {
            authProperties.devMode = true
            val userId = UUID.randomUUID()

            every { redisTemplate.delete("verify:attempts:test@example.com") } returns true

            val code = service.generateAndStore("test@example.com", userId)

            assertThat(code).isEqualTo("000000")
        }

        @Test
        fun `prod mode returns 6-digit numeric code`() {
            authProperties.devMode = false
            val userId = UUID.randomUUID()

            every { redisTemplate.delete("verify:attempts:test@example.com") } returns true

            val code = service.generateAndStore("test@example.com", userId)

            assertThat(code).matches("\\d{6}")
            assertThat(code).hasSize(6)
        }

        @Test
        fun `stores code in redis with TTL`() {
            authProperties.devMode = true
            val userId = UUID.randomUUID()
            val email = "store@example.com"
            val keySlot = slot<String>()
            val ttlSlot = slot<Long>()
            val unitSlot = slot<TimeUnit>()

            every { redisTemplate.delete("verify:attempts:$email") } returns true

            service.generateAndStore(email, userId)

            verify {
                valueOps.set(
                    capture(keySlot),
                    any<String>(),
                    capture(ttlSlot),
                    capture(unitSlot)
                )
            }
            assertThat(keySlot.captured).isEqualTo("verify:code:$email")
            assertThat(ttlSlot.captured).isEqualTo(600L)
            assertThat(unitSlot.captured).isEqualTo(TimeUnit.SECONDS)
        }

        @Test
        fun `resets attempts counter on new code generation`() {
            authProperties.devMode = true
            val userId = UUID.randomUUID()
            val email = "reset@example.com"

            every { redisTemplate.delete("verify:attempts:$email") } returns true

            service.generateAndStore(email, userId)

            verify { redisTemplate.delete("verify:attempts:$email") }
        }
    }

    @Nested
    inner class VerifyCode {

        @Test
        fun `returns Success and deletes key on matching code`() {
            authProperties.devMode = true
            val userId = UUID.randomUUID()
            val email = "verify@example.com"
            val storedJson = """{"code":"000000","userId":"$userId"}"""

            every { valueOps.increment("verify:attempts:$email") } returns 1L
            every { redisTemplate.expire("verify:attempts:$email", 600L, TimeUnit.SECONDS) } returns true
            every { valueOps.get("verify:code:$email") } returns storedJson
            every { redisTemplate.delete("verify:code:$email") } returns true

            val result = service.verifyCode(email, "000000")

            assertThat(result).isInstanceOf(VerifyResult.Success::class.java)
            assertThat((result as VerifyResult.Success).userId).isEqualTo(userId)
            verify { redisTemplate.delete("verify:code:$email") }
        }

        @Test
        fun `returns InvalidCode on mismatched code`() {
            val email = "mismatch@example.com"
            val userId = UUID.randomUUID()
            val storedJson = """{"code":"123456","userId":"$userId"}"""

            every { valueOps.increment("verify:attempts:$email") } returns 1L
            every { redisTemplate.expire("verify:attempts:$email", 600L, TimeUnit.SECONDS) } returns true
            every { valueOps.get("verify:code:$email") } returns storedJson

            val result = service.verifyCode(email, "999999")

            assertThat(result).isEqualTo(VerifyResult.InvalidCode)
        }

        @Test
        fun `returns InvalidCode when key is expired (null from redis)`() {
            val email = "expired@example.com"

            every { valueOps.increment("verify:attempts:$email") } returns 1L
            every { redisTemplate.expire("verify:attempts:$email", 600L, TimeUnit.SECONDS) } returns true
            every { valueOps.get("verify:code:$email") } returns null

            val result = service.verifyCode(email, "000000")

            assertThat(result).isEqualTo(VerifyResult.InvalidCode)
        }

        @Test
        fun `returns TooManyAttempts after exceeding max attempts`() {
            val email = "toomany@example.com"

            every { valueOps.increment("verify:attempts:$email") } returns 6L
            every { redisTemplate.getExpire("verify:attempts:$email", TimeUnit.SECONDS) } returns 550L

            val result = service.verifyCode(email, "000000")

            assertThat(result).isInstanceOf(VerifyResult.TooManyAttempts::class.java)
            assertThat((result as VerifyResult.TooManyAttempts).retryAfterSeconds).isEqualTo(550L)
        }

        @Test
        fun `increments attempts counter on every verify call`() {
            val email = "counter@example.com"
            val userId = UUID.randomUUID()
            val storedJson = """{"code":"000000","userId":"$userId"}"""

            every { valueOps.increment("verify:attempts:$email") } returns 2L
            every { valueOps.get("verify:code:$email") } returns storedJson
            every { redisTemplate.delete("verify:code:$email") } returns true

            service.verifyCode(email, "000000")

            verify { valueOps.increment("verify:attempts:$email") }
        }
    }

    @Nested
    inner class CanResend {

        @Test
        fun `returns Allowed when no cooldown key exists`() {
            val email = "free@example.com"

            every { redisTemplate.getExpire("verify:resend:$email", TimeUnit.SECONDS) } returns -2L

            val result = service.canResend(email)

            assertThat(result).isEqualTo(ResendEligibility.Allowed)
        }

        @Test
        fun `returns Cooldown with remaining seconds when key exists`() {
            val email = "cooldown@example.com"

            every { redisTemplate.getExpire("verify:resend:$email", TimeUnit.SECONDS) } returns 45L

            val result = service.canResend(email)

            assertThat(result).isInstanceOf(ResendEligibility.Cooldown::class.java)
            assertThat((result as ResendEligibility.Cooldown).retryAfterSeconds).isEqualTo(45L)
        }
    }

    @Nested
    inner class MarkResent {

        @Test
        fun `sets resend key with cooldown TTL`() {
            val email = "mark@example.com"
            val keySlot = slot<String>()
            val ttlSlot = slot<Long>()

            service.markResent(email)

            verify {
                valueOps.set(
                    capture(keySlot),
                    eq("1"),
                    capture(ttlSlot),
                    eq(TimeUnit.SECONDS)
                )
            }
            assertThat(keySlot.captured).isEqualTo("verify:resend:$email")
            assertThat(ttlSlot.captured).isEqualTo(60L)
        }
    }
}
