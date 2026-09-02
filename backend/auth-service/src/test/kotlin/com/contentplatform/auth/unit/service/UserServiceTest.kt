package com.contentplatform.auth.unit.service

import com.contentplatform.auth.api.exception.InvalidVerificationCodeException
import com.contentplatform.auth.api.exception.ResendCooldownException
import com.contentplatform.auth.api.exception.TooManyVerificationAttemptsException
import com.contentplatform.auth.configuration.AuthProperties
import com.contentplatform.auth.db.repository.EmailVerificationTokenRepository
import com.contentplatform.auth.db.repository.OutboxRepository
import com.contentplatform.auth.db.repository.PasswordResetTokenRepository
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.EmailVerificationTokenEntity
import com.contentplatform.auth.db.repository.model.OutboxEventEntity
import com.contentplatform.auth.db.repository.model.PasswordResetTokenEntity
import com.contentplatform.auth.db.repository.model.UserEntity
import com.contentplatform.auth.service.EmailService
import com.contentplatform.auth.service.ResendEligibility
import com.contentplatform.auth.service.UserService
import com.contentplatform.auth.service.VerificationCodeService
import com.contentplatform.auth.service.VerifyResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Tag("unit")
class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val emailVerificationTokenRepository = mockk<EmailVerificationTokenRepository>()
    private val passwordResetTokenRepository = mockk<PasswordResetTokenRepository>()
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val outboxRepository = mockk<OutboxRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val emailService = mockk<EmailService>(relaxed = true)
    private val verificationCodeService = mockk<VerificationCodeService>()
    private val authProperties = AuthProperties().apply {
        verificationTokenTtl = 86400
        resetTokenTtl = 3600
        devMode = false
    }

    private val userService = UserService(
        userRepository = userRepository,
        emailVerificationTokenRepository = emailVerificationTokenRepository,
        passwordResetTokenRepository = passwordResetTokenRepository,
        refreshTokenRepository = refreshTokenRepository,
        outboxRepository = outboxRepository,
        passwordEncoder = passwordEncoder,
        emailService = emailService,
        authProperties = authProperties,
        verificationCodeService = verificationCodeService,
        meterRegistry = SimpleMeterRegistry()
    )

    @Nested
    inner class Register {

        @Test
        fun `should save user with hashed password and generate verification code`() {
            val email = "new@example.com"
            val rawPassword = "Secret123!"
            val hashedPassword = "\$2a\$10\$hashedvalue"
            val userId = UUID.randomUUID()

            every { userRepository.findByEmail(email) } returns null
            every { passwordEncoder.encode(rawPassword) } returns hashedPassword
            every { userRepository.save(any()) } answers {
                (firstArg<UserEntity>()).copy(id = userId)
            }
            every { verificationCodeService.generateAndStore(email, userId) } returns "123456"
            every { outboxRepository.save(any()) } answers { firstArg() }

            val result = userService.register(email, rawPassword)

            assertThat(result).isEqualTo(email)

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.passwordHash).isEqualTo(hashedPassword)
            assertThat(userSlot.captured.emailVerified).isFalse()

            verify { verificationCodeService.generateAndStore(email, userId) }
            // No longer creates DB token for new registrations
            verify(exactly = 0) { emailVerificationTokenRepository.save(any()) }
        }

        @Test
        fun `should write user_registered outbox event on register`() {
            val email = "outbox@example.com"
            val userId = UUID.randomUUID()

            every { userRepository.findByEmail(email) } returns null
            every { passwordEncoder.encode(any()) } returns "hash"
            every { userRepository.save(any()) } answers { (firstArg<UserEntity>()).copy(id = userId) }
            every { verificationCodeService.generateAndStore(email, userId) } returns "123456"
            every { outboxRepository.save(any()) } answers { firstArg() }

            userService.register(email, "password")

            val outboxSlot = slot<OutboxEventEntity>()
            verify { outboxRepository.save(capture(outboxSlot)) }
            assertThat(outboxSlot.captured.eventType).isEqualTo("user.registered")
            assertThat(outboxSlot.captured.aggregateType).isEqualTo("User")
            assertThat(outboxSlot.captured.aggregateId).isEqualTo(userId.toString())
            assertThat(outboxSlot.captured.payload).contains(userId.toString())
            assertThat(outboxSlot.captured.payload).contains(email)
        }

        @Test
        fun `should throw when email already exists`() {
            val email = "existing@example.com"
            every { userRepository.findByEmail(email) } returns UserEntity(
                id = UUID.randomUUID(),
                email = email,
                passwordHash = "hash"
            )

            assertThatThrownBy { userService.register(email, "password") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should not store plain text password`() {
            val rawPassword = "PlainTextPassword"
            val encodedPassword = "\$2a\$10\$encoded"
            val userId = UUID.randomUUID()

            every { userRepository.findByEmail(any()) } returns null
            every { passwordEncoder.encode(rawPassword) } returns encodedPassword
            every { userRepository.save(any()) } answers {
                (firstArg<UserEntity>()).copy(id = UUID.randomUUID())
            }
            every { verificationCodeService.generateAndStore(any(), any()) } returns "123456"
            every { outboxRepository.save(any()) } answers { firstArg() }

            userService.register("user@example.com", rawPassword)

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.passwordHash).isNotEqualTo(rawPassword)
            assertThat(userSlot.captured.passwordHash).isEqualTo(encodedPassword)
        }

        @Test
        fun `should send verification code email in prod mode`() {
            val email = "notify@example.com"
            val userId = UUID.randomUUID()
            authProperties.devMode = false

            every { userRepository.findByEmail(email) } returns null
            every { passwordEncoder.encode(any()) } returns "hash"
            every { userRepository.save(any()) } answers {
                (firstArg<UserEntity>()).copy(id = userId)
            }
            every { verificationCodeService.generateAndStore(email, userId) } returns "654321"
            every { outboxRepository.save(any()) } answers { firstArg() }

            userService.register(email, "password")

            verify { emailService.sendVerificationCode(email, "654321") }
        }

        @Test
        fun `should not send email in dev mode`() {
            val email = "devmode@example.com"
            val userId = UUID.randomUUID()
            authProperties.devMode = true

            every { userRepository.findByEmail(email) } returns null
            every { passwordEncoder.encode(any()) } returns "hash"
            every { userRepository.save(any()) } answers {
                (firstArg<UserEntity>()).copy(id = userId)
            }
            every { verificationCodeService.generateAndStore(email, userId) } returns "000000"
            every { outboxRepository.save(any()) } answers { firstArg() }

            userService.register(email, "password")

            verify(exactly = 0) { emailService.sendVerificationCode(any(), any()) }
        }
    }

    @Nested
    inner class VerifyEmail {

        private val userId = UUID.randomUUID()
        private val userEmail = "verify@example.com"

        @Test
        fun `should set user email_verified to true for valid token`() {
            val token = "valid-token"
            val tokenEntity = EmailVerificationTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(3600),
                used = false
            )
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash")

            every { emailVerificationTokenRepository.findById(token) } returns Optional.of(tokenEntity)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }
            every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }

            val result = userService.verifyEmail(token)

            assertThat(result).isEqualTo(userEmail)

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.emailVerified).isTrue()
        }

        @Test
        fun `should not write outbox event on verifyEmail (event moved to register)`() {
            val token = "no-outbox-token"
            val tokenEntity = EmailVerificationTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(3600),
                used = false
            )
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash")

            every { emailVerificationTokenRepository.findById(token) } returns Optional.of(tokenEntity)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }
            every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }

            userService.verifyEmail(token)

            verify(exactly = 0) { outboxRepository.save(any()) }
        }

        @Test
        fun `should mark verification token as used`() {
            val token = "mark-used-token"
            val tokenEntity = EmailVerificationTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(3600),
                used = false
            )
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash")

            every { emailVerificationTokenRepository.findById(token) } returns Optional.of(tokenEntity)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }
            every { emailVerificationTokenRepository.save(any()) } answers { firstArg() }

            userService.verifyEmail(token)

            val tokenSlot = slot<EmailVerificationTokenEntity>()
            verify { emailVerificationTokenRepository.save(capture(tokenSlot)) }
            assertThat(tokenSlot.captured.used).isTrue()
        }

        @Test
        fun `should throw when token is expired`() {
            val token = "expired-token"
            val tokenEntity = EmailVerificationTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().minusSeconds(3600),
                used = false
            )

            every { emailVerificationTokenRepository.findById(token) } returns Optional.of(tokenEntity)

            assertThatThrownBy { userService.verifyEmail(token) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when token is already used`() {
            val token = "used-token"
            val tokenEntity = EmailVerificationTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(3600),
                used = true
            )

            every { emailVerificationTokenRepository.findById(token) } returns Optional.of(tokenEntity)

            assertThatThrownBy { userService.verifyEmail(token) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when token not found`() {
            every { emailVerificationTokenRepository.findById("nonexistent") } returns Optional.empty()

            assertThatThrownBy { userService.verifyEmail("nonexistent") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class VerifyByCode {

        private val userId = UUID.randomUUID()
        private val userEmail = "code-verify@example.com"
        private val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash", emailVerified = false)

        @Test
        fun `returns VerifyResponse on correct code`() {
            every { verificationCodeService.verifyCode(userEmail, "000000") } returns VerifyResult.Success(userId)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val result = userService.verifyByCode(userEmail, "000000")

            assertThat(result.email).isEqualTo(userEmail)
            assertThat(result.message).isEqualTo("Email verified")
        }

        @Test
        fun `sets emailVerified=true in database on success`() {
            every { verificationCodeService.verifyCode(userEmail, "000000") } returns VerifyResult.Success(userId)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            userService.verifyByCode(userEmail, "000000")

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.emailVerified).isTrue()
        }

        @Test
        fun `does not write outbox event on verifyByCode (event moved to register)`() {
            every { verificationCodeService.verifyCode(userEmail, "000000") } returns VerifyResult.Success(userId)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            userService.verifyByCode(userEmail, "000000")

            verify(exactly = 0) { outboxRepository.save(any()) }
        }

        @Test
        fun `throws InvalidVerificationCodeException on invalid code`() {
            every { verificationCodeService.verifyCode(userEmail, "999999") } returns VerifyResult.InvalidCode

            assertThatThrownBy { userService.verifyByCode(userEmail, "999999") }
                .isInstanceOf(InvalidVerificationCodeException::class.java)
        }

        @Test
        fun `throws TooManyVerificationAttemptsException on rate limit`() {
            every { verificationCodeService.verifyCode(userEmail, "111111") } returns VerifyResult.TooManyAttempts(300L)

            assertThatThrownBy { userService.verifyByCode(userEmail, "111111") }
                .isInstanceOf(TooManyVerificationAttemptsException::class.java)
                .extracting("retryAfterSeconds")
                .isEqualTo(300L)
        }
    }

    @Nested
    inner class ResendVerificationCode {

        private val userId = UUID.randomUUID()
        private val userEmail = "resend@example.com"

        @Test
        fun `sends new code when user is unverified and cooldown elapsed`() {
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash", emailVerified = false)
            authProperties.devMode = false

            every { userRepository.findByEmail(userEmail) } returns user
            every { verificationCodeService.canResend(userEmail) } returns ResendEligibility.Allowed
            every { verificationCodeService.generateAndStore(userEmail, userId) } returns "654321"
            every { verificationCodeService.markResent(userEmail) } just Runs

            val result = userService.resendVerificationCode(userEmail)

            assertThat(result.message).isNotEmpty()
            verify { emailService.sendVerificationCode(userEmail, "654321") }
            verify { verificationCodeService.markResent(userEmail) }
        }

        @Test
        fun `skips email send in dev mode`() {
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash", emailVerified = false)
            authProperties.devMode = true

            every { userRepository.findByEmail(userEmail) } returns user
            every { verificationCodeService.canResend(userEmail) } returns ResendEligibility.Allowed
            every { verificationCodeService.generateAndStore(userEmail, userId) } returns "000000"
            every { verificationCodeService.markResent(userEmail) } just Runs

            userService.resendVerificationCode(userEmail)

            verify(exactly = 0) { emailService.sendVerificationCode(any(), any()) }
        }

        @Test
        fun `returns success immediately when user already verified`() {
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash", emailVerified = true)

            every { userRepository.findByEmail(userEmail) } returns user

            val result = userService.resendVerificationCode(userEmail)

            assertThat(result.message).isNotEmpty()
            verify(exactly = 0) { verificationCodeService.generateAndStore(any(), any()) }
        }

        @Test
        fun `throws ResendCooldownException when cooldown is active`() {
            val user = UserEntity(id = userId, email = userEmail, passwordHash = "hash", emailVerified = false)

            every { userRepository.findByEmail(userEmail) } returns user
            every { verificationCodeService.canResend(userEmail) } returns ResendEligibility.Cooldown(45L)

            assertThatThrownBy { userService.resendVerificationCode(userEmail) }
                .isInstanceOf(ResendCooldownException::class.java)
                .extracting("retryAfterSeconds")
                .isEqualTo(45L)
        }

        @Test
        fun `throws when user not found`() {
            every { userRepository.findByEmail("nobody@example.com") } returns null

            assertThatThrownBy { userService.resendVerificationCode("nobody@example.com") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class Login {

        private val userId = UUID.randomUUID()

        @Test
        fun `should return user for valid credentials`() {
            val email = "login@example.com"
            val password = "correct-password"
            val user = UserEntity(
                id = userId,
                email = email,
                passwordHash = "encoded-hash",
                emailVerified = true
            )

            every { userRepository.findByEmail(email) } returns user
            every { passwordEncoder.matches(password, "encoded-hash") } returns true

            val result = userService.login(email, password)

            assertThat(result.id).isEqualTo(userId)
            assertThat(result.email).isEqualTo(email)
        }

        @Test
        fun `should throw when email not found`() {
            every { userRepository.findByEmail("unknown@example.com") } returns null

            assertThatThrownBy { userService.login("unknown@example.com", "password") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when password does not match`() {
            val user = UserEntity(
                id = userId,
                email = "user@example.com",
                passwordHash = "hash",
                emailVerified = true
            )
            every { userRepository.findByEmail("user@example.com") } returns user
            every { passwordEncoder.matches("wrong", "hash") } returns false

            assertThatThrownBy { userService.login("user@example.com", "wrong") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when email is not verified`() {
            val user = UserEntity(
                id = userId,
                email = "unverified@example.com",
                passwordHash = "hash",
                emailVerified = false
            )
            every { userRepository.findByEmail("unverified@example.com") } returns user
            every { passwordEncoder.matches("password", "hash") } returns true

            assertThatThrownBy { userService.login("unverified@example.com", "password") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class RequestPasswordReset {

        @Test
        fun `should create token and send email for known user`() {
            val email = "known@example.com"
            val userId = UUID.randomUUID()
            val user = UserEntity(id = userId, email = email, passwordHash = "hash")

            every { userRepository.findByEmail(email) } returns user
            every { passwordResetTokenRepository.save(any()) } answers { firstArg() }

            userService.requestPasswordReset(email)

            verify { passwordResetTokenRepository.save(any()) }
            verify { emailService.sendPasswordResetEmail(email, any()) }
        }

        @Test
        fun `should silently return when email not found`() {
            every { userRepository.findByEmail("unknown@example.com") } returns null

            userService.requestPasswordReset("unknown@example.com")

            verify(exactly = 0) { passwordResetTokenRepository.save(any()) }
            verify(exactly = 0) { emailService.sendPasswordResetEmail(any(), any()) }
        }
    }

    @Nested
    inner class ResetPassword {

        private val userId = UUID.randomUUID()

        @Test
        fun `should update password and delete refresh tokens for valid token`() {
            val token = "valid-reset-token"
            val newPassword = "NewPassword123!"
            val encodedNew = "\$2a\$10\$newEncoded"
            val tokenEntity = PasswordResetTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(3600),
                used = false
            )
            val user = UserEntity(id = userId, email = "user@example.com", passwordHash = "old-hash")

            every { passwordResetTokenRepository.findById(token) } returns Optional.of(tokenEntity)
            every { userRepository.findById(userId) } returns Optional.of(user)
            every { passwordEncoder.encode(newPassword) } returns encodedNew
            every { userRepository.save(any()) } answers { firstArg() }
            every { passwordResetTokenRepository.save(any()) } answers { firstArg() }
            every { refreshTokenRepository.deleteByUserId(userId) } just Runs

            userService.resetPassword(token, newPassword)

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.passwordHash).isEqualTo(encodedNew)

            val tokenSlot = slot<PasswordResetTokenEntity>()
            verify { passwordResetTokenRepository.save(capture(tokenSlot)) }
            assertThat(tokenSlot.captured.used).isTrue()

            verify { refreshTokenRepository.deleteByUserId(userId) }
        }

        @Test
        fun `should throw when reset token is expired`() {
            val token = "expired-reset"
            val tokenEntity = PasswordResetTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().minusSeconds(3600),
                used = false
            )

            every { passwordResetTokenRepository.findById(token) } returns Optional.of(tokenEntity)

            assertThatThrownBy { userService.resetPassword(token, "newpass") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when reset token is already used`() {
            val token = "used-reset"
            val tokenEntity = PasswordResetTokenEntity(
                token = token,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(3600),
                used = true
            )

            every { passwordResetTokenRepository.findById(token) } returns Optional.of(tokenEntity)

            assertThatThrownBy { userService.resetPassword(token, "newpass") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class ChangePassword {

        private val userId = UUID.randomUUID()

        @Test
        fun `should update password when current password is correct`() {
            val currentPassword = "OldPass123!"
            val newPassword = "NewPass456!"
            val encodedNew = "\$2a\$10\$newHash"
            val user = UserEntity(id = userId, email = "user@example.com", passwordHash = "old-hash")

            every { userRepository.findById(userId) } returns Optional.of(user)
            every { passwordEncoder.matches(currentPassword, "old-hash") } returns true
            every { passwordEncoder.encode(newPassword) } returns encodedNew
            every { userRepository.save(any()) } answers { firstArg() }

            userService.changePassword(userId, currentPassword, newPassword)

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.passwordHash).isEqualTo(encodedNew)
        }

        @Test
        fun `should throw when current password is wrong`() {
            val user = UserEntity(id = userId, email = "user@example.com", passwordHash = "hash")

            every { userRepository.findById(userId) } returns Optional.of(user)
            every { passwordEncoder.matches("wrong", "hash") } returns false

            assertThatThrownBy { userService.changePassword(userId, "wrong", "newpass") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should throw when user not found`() {
            every { userRepository.findById(userId) } returns Optional.empty()

            assertThatThrownBy { userService.changePassword(userId, "current", "new") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class UpdateSubscriptionTier {

        @Test
        fun `should update user subscription tier`() {
            val userId = UUID.randomUUID()
            val user = UserEntity(id = userId, email = "user@example.com", passwordHash = "hash", subscriptionTier = "free")

            every { userRepository.findById(userId) } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            userService.updateSubscriptionTier(userId, "premium")

            val userSlot = slot<UserEntity>()
            verify { userRepository.save(capture(userSlot)) }
            assertThat(userSlot.captured.subscriptionTier).isEqualTo("premium")
        }
    }
}
