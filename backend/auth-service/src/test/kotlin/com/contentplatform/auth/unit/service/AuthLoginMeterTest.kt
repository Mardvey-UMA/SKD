package com.contentplatform.auth.unit.service

import com.contentplatform.auth.configuration.AuthProperties
import com.contentplatform.auth.db.repository.EmailVerificationTokenRepository
import com.contentplatform.auth.db.repository.OutboxRepository
import com.contentplatform.auth.db.repository.PasswordResetTokenRepository
import com.contentplatform.auth.db.repository.RefreshTokenRepository
import com.contentplatform.auth.db.repository.UserRepository
import com.contentplatform.auth.db.repository.model.UserEntity
import com.contentplatform.auth.service.EmailService
import com.contentplatform.auth.service.UserService
import com.contentplatform.auth.service.VerificationCodeService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

@Tag("unit")
class AuthLoginMeterTest {

    private val meterRegistry = SimpleMeterRegistry()
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
        meterRegistry = meterRegistry
    )

    private val userId = UUID.randomUUID()
    private val email = "meter@example.com"
    private val password = "correct-pass"

    private val verifiedUser = UserEntity(
        id = userId,
        email = email,
        passwordHash = "hash",
        emailVerified = true,
        subscriptionTier = "free"
    )

    @Test
    fun `successful login increments auth_login_attempts counter with result=success`() {
        every { userRepository.findByEmail(email) } returns verifiedUser
        every { passwordEncoder.matches(password, "hash") } returns true

        userService.login(email, password)

        val counter = meterRegistry.find("auth_login_attempts").tag("result", "success").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `failed login due to wrong password increments auth_login_attempts counter with result=fail`() {
        every { userRepository.findByEmail(email) } returns verifiedUser
        every { passwordEncoder.matches("wrong", "hash") } returns false

        assertThatThrownBy { userService.login(email, "wrong") }
            .isInstanceOf(IllegalArgumentException::class.java)

        val counter = meterRegistry.find("auth_login_attempts").tag("result", "fail").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `login with unverified email increments auth_login_attempts counter with result=locked`() {
        val unverifiedUser = verifiedUser.copy(emailVerified = false)
        every { userRepository.findByEmail(email) } returns unverifiedUser
        every { passwordEncoder.matches(password, "hash") } returns true

        assertThatThrownBy { userService.login(email, password) }
            .isInstanceOf(IllegalArgumentException::class.java)

        val counter = meterRegistry.find("auth_login_attempts").tag("result", "locked").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}
