package com.contentplatform.auth.integration.service

import com.contentplatform.auth.api.exception.InvalidVerificationCodeException
import com.contentplatform.auth.api.exception.ResendCooldownException
import com.contentplatform.auth.db.repository.EmailVerificationTokenRepository
import com.contentplatform.auth.db.repository.OutboxRepository
import com.contentplatform.auth.db.repository.PasswordResetTokenRepository
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.RefreshTokenEntity
import com.contentplatform.auth.integration.IntegrationTestBase
import com.contentplatform.auth.service.EmailService
import com.contentplatform.auth.service.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

@Tag("integration")
class UserServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var userService: UserService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @Autowired
    lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var outboxRepository: OutboxRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @MockkBean(relaxed = true)
    lateinit var emailService: EmailService

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")
        jdbcTemplate.execute("DELETE FROM refresh_tokens")
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM users")
        // Clean up any leftover Redis keys from previous tests
        redisTemplate.keys("verify:*")?.forEach { redisTemplate.delete(it) }
    }

    @Nested
    inner class `code-based register and verify flow` {

        @Test
        fun `register stores user in DB and generates code in Redis (dev mode)`() {
            // application-test.yml has dev-mode: true → code = "000000"
            val email = "code-flow@example.com"

            userService.register(email, "SecurePass123!")

            val user = userRepository.findByEmail(email)
            assertThat(user).isNotNull
            assertThat(user!!.emailVerified).isFalse()

            // Code stored in Redis, NOT a DB verification token
            val codeJson = redisTemplate.opsForValue().get("verify:code:$email")
            assertThat(codeJson).isNotNull
            assertThat(codeJson).contains("000000")

            // No DB token created for new registration
            val tokens = emailVerificationTokenRepository.findAll().toList()
            assertThat(tokens).isEmpty()
        }

        @Test
        fun `register writes user_registered outbox event immediately`() {
            val email = "register-outbox@example.com"

            userService.register(email, "Password123!")

            val user = userRepository.findByEmail(email)!!
            val outboxEntries = outboxRepository.findUnpublished(100)
            assertThat(outboxEntries).hasSize(1)

            SoftAssertions.assertSoftly { softly ->
                softly.assertThat(outboxEntries[0].eventType).isEqualTo("user.registered")
                softly.assertThat(outboxEntries[0].aggregateType).isEqualTo("User")
                softly.assertThat(outboxEntries[0].aggregateId).isEqualTo(user.id.toString())
                softly.assertThat(outboxEntries[0].payload).contains(user.id.toString())
                softly.assertThat(outboxEntries[0].payload).contains(email)
            }
        }

        @Test
        fun `verifyByCode sets emailVerified but does not create additional outbox event`() {
            val email = "verify-by-code@example.com"

            userService.register(email, "Password123!")

            // Outbox already has 1 entry from register()
            assertThat(outboxRepository.findUnpublished(100)).hasSize(1)

            // In dev mode, code is always "000000"
            val response = userService.verifyByCode(email, "000000")

            assertThat(response.email).isEqualTo(email)
            assertThat(response.message).isEqualTo("Email verified")

            // User should be verified in DB
            val user = userRepository.findByEmail(email)
            assertThat(user!!.emailVerified).isTrue()

            // Still only 1 outbox entry — verifyByCode does NOT add another
            assertThat(outboxRepository.findUnpublished(100)).hasSize(1)
        }

        @Test
        fun `verifyByCode with wrong code throws InvalidVerificationCodeException`() {
            val email = "wrong-code@example.com"
            userService.register(email, "Password123!")

            assertThatThrownBy { userService.verifyByCode(email, "999999") }
                .isInstanceOf(InvalidVerificationCodeException::class.java)
        }

        @Test
        fun `should store hashed password not plain text`() {
            val rawPassword = "MyPlainPassword!"

            userService.register("hash-check@example.com", rawPassword)

            val user = userRepository.findByEmail("hash-check@example.com")
            assertThat(user).isNotNull
            assertThat(user!!.passwordHash).isNotEqualTo(rawPassword)
            assertThat(user.passwordHash).startsWith("\$2a\$")
        }

        @Test
        fun `should reject duplicate email registration`() {
            userService.register("dup@example.com", "Pass123!")

            assertThatThrownBy { userService.register("dup@example.com", "Pass456!") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class `resend verification code flow` {

        @Test
        fun `resend generates fresh code and marks cooldown`() {
            val email = "resend-test@example.com"
            userService.register(email, "Password123!")

            val response = userService.resendVerificationCode(email)

            assertThat(response.message).isNotEmpty()

            // Cooldown key should be set in Redis
            val cooldownTtl = redisTemplate.getExpire("verify:resend:$email")
            assertThat(cooldownTtl).isGreaterThan(0)
        }

        @Test
        fun `second resend within cooldown throws ResendCooldownException`() {
            val email = "resend-cooldown@example.com"
            userService.register(email, "Password123!")

            userService.resendVerificationCode(email) // First resend ok

            assertThatThrownBy { userService.resendVerificationCode(email) }
                .isInstanceOf(ResendCooldownException::class.java)
        }

        @Test
        fun `resend for already verified user returns success without new code`() {
            val email = "already-verified@example.com"
            userService.register(email, "Password123!")
            userService.verifyByCode(email, "000000")

            val response = userService.resendVerificationCode(email)

            assertThat(response.message).isNotEmpty()
            // No email sent for already-verified user
            verify(exactly = 0) { emailService.sendVerificationCode(any(), any()) }
        }
    }

    @Nested
    inner class `legacy GET verify token flow` {

        @Test
        fun `verifyEmail with token still works for legacy path`() {
            // Manually create a legacy DB token for the legacy GET path
            userService.register("legacy@example.com", "Password123!")
            val user = userRepository.findByEmail("legacy@example.com")!!

            // Manually insert legacy token for GET verify path
            jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (token, user_id, expires_at, used) VALUES (?, ?, ?, false)",
                "legacy-token-abc", user.id, java.sql.Timestamp.from(Instant.now().plusSeconds(3600))
            )

            val resultEmail = userService.verifyEmail("legacy-token-abc")

            assertThat(resultEmail).isEqualTo("legacy@example.com")
            assertThat(userRepository.findByEmail("legacy@example.com")!!.emailVerified).isTrue()
        }
    }

    @Nested
    inner class login {

        @Test
        fun `should return user for valid credentials with verified email`() {
            userService.register("login@example.com", "CorrectPass123!")
            userService.verifyByCode("login@example.com", "000000")

            val user = userService.login("login@example.com", "CorrectPass123!")

            assertThat(user.email).isEqualTo("login@example.com")
            assertThat(user.emailVerified).isTrue()
        }

        @Test
        fun `should throw when password is wrong`() {
            userService.register("wrong-pass@example.com", "RealPassword!")
            userService.verifyByCode("wrong-pass@example.com", "000000")

            assertThatThrownBy { userService.login("wrong-pass@example.com", "WrongPassword!") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when email is not verified`() {
            userService.register("unverified-login@example.com", "Password123!")

            assertThatThrownBy { userService.login("unverified-login@example.com", "Password123!") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when user does not exist`() {
            assertThatThrownBy { userService.login("ghost@example.com", "pass") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class `reset password` {

        @Test
        fun `should update password and delete refresh tokens`() {
            userService.register("reset@example.com", "OldPassword123!")
            userService.verifyByCode("reset@example.com", "000000")

            val user = userRepository.findByEmail("reset@example.com")!!

            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = user.id!!,
                    tokenHash = "some-hash",
                    expiresAt = Instant.now().plusSeconds(86400)
                )
            )
            assertThat(refreshTokenRepository.findByTokenHash("some-hash")).isNotNull

            userService.requestPasswordReset("reset@example.com")
            val resetTokens = passwordResetTokenRepository.findAll().toList()
            assertThat(resetTokens).hasSize(1)
            val resetToken = resetTokens[0].token

            userService.resetPassword(resetToken, "NewPassword456!")

            val loggedIn = userService.login("reset@example.com", "NewPassword456!")
            assertThat(loggedIn.email).isEqualTo("reset@example.com")

            assertThatThrownBy { userService.login("reset@example.com", "OldPassword123!") }
                .isInstanceOf(IllegalArgumentException::class.java)

            val updatedResetToken = passwordResetTokenRepository.findById(resetToken)
            assertThat(updatedResetToken).isPresent
            assertThat(updatedResetToken.get().used).isTrue()

            assertThat(refreshTokenRepository.findByTokenHash("some-hash")).isNull()
        }

        @Test
        fun `should silently handle unknown email for password reset request`() {
            userService.requestPasswordReset("nobody@example.com")

            val resetTokens = passwordResetTokenRepository.findAll().toList()
            assertThat(resetTokens).isEmpty()
        }
    }

    @Nested
    inner class `change password` {

        @Test
        fun `should change password when current password is correct`() {
            userService.register("change@example.com", "CurrentPass!")
            userService.verifyByCode("change@example.com", "000000")

            val user = userRepository.findByEmail("change@example.com")!!

            userService.changePassword(user.id!!, "CurrentPass!", "NewChanged!")

            val loggedIn = userService.login("change@example.com", "NewChanged!")
            assertThat(loggedIn.email).isEqualTo("change@example.com")

            assertThatThrownBy { userService.login("change@example.com", "CurrentPass!") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when current password is wrong`() {
            userService.register("change-fail@example.com", "RealPass!")
            userService.verifyByCode("change-fail@example.com", "000000")

            val user = userRepository.findByEmail("change-fail@example.com")!!

            assertThatThrownBy { userService.changePassword(user.id!!, "WrongPass!", "NewPass!") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class `update subscription tier` {

        @Test
        fun `should update user subscription tier in database`() {
            userService.register("tier@example.com", "Pass123!")
            val user = userRepository.findByEmail("tier@example.com")!!

            assertThat(user.subscriptionTier).isEqualTo("free")

            userService.updateSubscriptionTier(user.id!!, "premium")

            val updated = userRepository.findById(user.id!!).get()
            assertThat(updated.subscriptionTier).isEqualTo("premium")
        }
    }
}
