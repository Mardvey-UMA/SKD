package com.contentplatform.auth.service

import com.contentplatform.auth.api.dto.ResendVerificationResponse
import com.contentplatform.auth.api.dto.VerifyResponse
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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val outboxRepository: OutboxRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    private val authProperties: AuthProperties,
    private val verificationCodeService: VerificationCodeService,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    companion object {
        private val log = LoggerFactory.getLogger(UserService::class.java)
        private val secureRandom = SecureRandom()
    }

    private val loginSuccessCounter: Counter = Counter.builder("auth_login_attempts")
        .tag("result", "success").register(meterRegistry)
    private val loginFailCounter: Counter = Counter.builder("auth_login_attempts")
        .tag("result", "fail").register(meterRegistry)
    private val loginLockedCounter: Counter = Counter.builder("auth_login_attempts")
        .tag("result", "locked").register(meterRegistry)

    @Transactional
    fun register(email: String, password: String): String {
        if (userRepository.findByEmail(email) != null) {
            throw IllegalArgumentException("Email already registered: $email")
        }

        val passwordHash = passwordEncoder.encode(password)
        val user = UserEntity(email = email, passwordHash = passwordHash)
        val savedUser = userRepository.save(user)

        val payload = objectMapper.writeValueAsString(
            mapOf("user_id" to savedUser.id.toString(), "email" to savedUser.email)
        )
        outboxRepository.save(
            OutboxEventEntity(
                aggregateType = "User",
                aggregateId = savedUser.id.toString(),
                eventType = "user.registered",
                payload = payload
            )
        )

        val code = verificationCodeService.generateAndStore(email, savedUser.id!!)

        if (authProperties.devMode) {
            log.info("DEV MODE: verification code generated for email={} (retrieve from Valkey or DB)", email)
        } else {
            emailService.sendVerificationCode(email, code)
        }

        log.info("Registered user email={}", email)
        return email
    }

    /**
     * Legacy GET-token-based verification path. Kept for backward compatibility.
     * New registrations use the code-based flow (verifyByCode).
     */
    @Transactional
    fun verifyEmail(token: String): String {
        val tokenEntity = emailVerificationTokenRepository.findById(token)
            .orElseThrow { IllegalArgumentException("Verification token not found: $token") }

        if (tokenEntity.used) {
            throw IllegalArgumentException("Verification token already used")
        }
        if (tokenEntity.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Verification token expired")
        }

        val user = userRepository.findById(tokenEntity.userId)
            .orElseThrow { IllegalArgumentException("User not found: ${tokenEntity.userId}") }

        userRepository.save(user.copy(emailVerified = true))
        emailVerificationTokenRepository.save(tokenEntity.copy(used = true))

        log.info("Verified email (legacy token) for userId={}", user.id)
        return user.email
    }

    /**
     * Code-based email verification. Delegates to VerificationCodeService.
     * On success: marks user verified. The user.registered outbox event is written in register().
     */
    @Transactional
    fun verifyByCode(email: String, code: String): VerifyResponse {
        return when (val result = verificationCodeService.verifyCode(email, code)) {
            is VerifyResult.Success -> {
                val user = userRepository.findById(result.userId)
                    .orElseThrow { IllegalArgumentException("User not found: ${result.userId}") }

                userRepository.save(user.copy(emailVerified = true))

                log.info("Verified email by code for userId={}", user.id)
                VerifyResponse(message = "Email verified", email = user.email)
            }

            is VerifyResult.InvalidCode -> throw InvalidVerificationCodeException()

            is VerifyResult.TooManyAttempts -> throw TooManyVerificationAttemptsException(result.retryAfterSeconds)
        }
    }

    /**
     * Resend email verification code. Respects cooldown and skips already-verified users.
     */
    fun resendVerificationCode(email: String): ResendVerificationResponse {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("User not found: $email")

        if (user.emailVerified) {
            return ResendVerificationResponse(
                message = "Email already verified",
                cooldownSeconds = 0L
            )
        }

        when (val eligibility = verificationCodeService.canResend(email)) {
            is ResendEligibility.Cooldown -> throw ResendCooldownException(eligibility.retryAfterSeconds)

            is ResendEligibility.Allowed -> {
                val code = verificationCodeService.generateAndStore(email, user.id!!)

                if (authProperties.devMode) {
                    log.info("DEV MODE: verification code generated for email={} (retrieve from Valkey or DB)", email)
                } else {
                    emailService.sendVerificationCode(email, code)
                }

                verificationCodeService.markResent(email)

                return ResendVerificationResponse(
                    message = "Verification code sent",
                    cooldownSeconds = authProperties.verificationResendCooldownSeconds
                )
            }
        }
    }

    fun login(email: String, password: String): UserEntity {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("User not found: $email")

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            loginFailCounter.increment()
            throw IllegalArgumentException("Invalid password for email: $email")
        }
        if (!user.emailVerified) {
            loginLockedCounter.increment()
            throw IllegalArgumentException("Email not verified for: $email")
        }

        loginSuccessCounter.increment()
        log.info("User logged in email={}", email)
        return user
    }

    fun requestPasswordReset(email: String) {
        val user = userRepository.findByEmail(email) ?: return

        val token = if (authProperties.devMode) {
            passwordResetTokenRepository.findById("000000").ifPresent {
                passwordResetTokenRepository.deleteById("000000")
            }
            "000000"
        } else {
            generateToken()
        }
        val expiresAt = Instant.now().plusSeconds(authProperties.resetTokenTtl)
        val resetToken = PasswordResetTokenEntity(
            token = token,
            userId = user.id!!,
            expiresAt = expiresAt
        ).markNew()
        passwordResetTokenRepository.save(resetToken)

        if (authProperties.devMode) {
            log.info("DEV MODE: password reset token generated for email={} (retrieve from DB)", email)
        } else {
            emailService.sendPasswordResetEmail(email, token)
        }
        log.info("Password reset requested for email={}", email)
    }

    @Transactional
    fun resetPassword(token: String, newPassword: String) {
        val tokenEntity = passwordResetTokenRepository.findById(token)
            .orElseThrow { IllegalArgumentException("Password reset token not found: $token") }

        if (tokenEntity.used) {
            throw IllegalArgumentException("Password reset token already used")
        }
        if (tokenEntity.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Password reset token expired")
        }

        val user = userRepository.findById(tokenEntity.userId)
            .orElseThrow { IllegalArgumentException("User not found: ${tokenEntity.userId}") }

        val newHash = passwordEncoder.encode(newPassword)
        userRepository.save(user.copy(passwordHash = newHash))
        passwordResetTokenRepository.save(tokenEntity.copy(used = true))
        refreshTokenRepository.deleteByUserId(tokenEntity.userId)

        log.info("Password reset for userId={}", user.id)
    }

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw IllegalArgumentException("Current password is incorrect for userId=$userId")
        }

        val newHash = passwordEncoder.encode(newPassword)
        userRepository.save(user.copy(passwordHash = newHash))

        log.info("Password changed for userId={}", userId)
    }

    fun findById(userId: UUID): UserEntity {
        return userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
    }

    fun updateSubscriptionTier(userId: UUID, tier: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }

        userRepository.save(user.copy(subscriptionTier = tier))
        log.info("Updated subscription tier for userId={} to tier={}", userId, tier)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
