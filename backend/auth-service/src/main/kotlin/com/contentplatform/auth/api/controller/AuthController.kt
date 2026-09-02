package com.contentplatform.auth.api.controller

import com.contentplatform.auth.api.dto.LoginRequest
import com.contentplatform.auth.api.dto.LoginResponse
import com.contentplatform.auth.api.dto.LogoutResponse
import com.contentplatform.auth.api.dto.PasswordChangeDto
import com.contentplatform.auth.api.dto.PasswordResetDto
import com.contentplatform.auth.api.dto.PasswordResetRequestDto
import com.contentplatform.auth.api.dto.RefreshRequest
import com.contentplatform.auth.api.dto.RegisterRequest
import com.contentplatform.auth.api.dto.RegisterResponse
import com.contentplatform.auth.api.dto.ResendVerificationRequest
import com.contentplatform.auth.api.dto.ResendVerificationResponse
import com.contentplatform.auth.api.dto.VerifyEmailCodeRequest
import com.contentplatform.auth.api.dto.VerifyResponse
import com.contentplatform.auth.api.exception.DuplicateEmailException
import com.contentplatform.auth.api.exception.EmailNotVerifiedException
import com.contentplatform.auth.api.exception.InvalidCredentialsException
import com.contentplatform.auth.api.exception.InvalidVerificationCodeException
import com.contentplatform.auth.api.exception.ResendCooldownException
import com.contentplatform.auth.api.exception.TokenExpiredException
import com.contentplatform.auth.api.exception.TokenNotFoundException
import com.contentplatform.auth.api.exception.TooManyVerificationAttemptsException
import com.contentplatform.auth.api.exception.ValidationException
import com.contentplatform.auth.configuration.JwtProperties
import com.contentplatform.auth.db.service.TokenService
import com.contentplatform.auth.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val tokenService: TokenService,
    private val jwtProperties: JwtProperties,
    private val jwtDecoder: JwtDecoder
) {

    companion object {
        private val log = LoggerFactory.getLogger(AuthController::class.java)
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val UPPERCASE_REGEX = Regex("[A-Z]")
        private val LOWERCASE_REGEX = Regex("[a-z]")
        private val DIGIT_REGEX = Regex("[0-9]")
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<RegisterResponse> {
        validateEmail(request.email)
        validatePassword(request.password)

        val email = try {
            userService.register(request.email, request.password)
        } catch (ex: IllegalArgumentException) {
            if (ex.message?.contains("already registered") == true) {
                throw DuplicateEmailException(ex.message!!)
            }
            throw ex
        }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RegisterResponse(message = "Registration successful. Please verify your email.", email = email))
    }

    @GetMapping("/verify")
    fun verify(@RequestParam token: String): ResponseEntity<VerifyResponse> {
        val email = try {
            userService.verifyEmail(token)
        } catch (ex: IllegalArgumentException) {
            val msg = ex.message ?: ""
            when {
                msg.contains("not found") -> throw TokenNotFoundException(msg)
                msg.contains("already used") -> throw TokenExpiredException(msg)
                msg.contains("expired") -> throw TokenExpiredException(msg)
                else -> throw ex
            }
        }

        return ResponseEntity.ok(VerifyResponse(message = "Email verified successfully", email = email))
    }

    @PostMapping("/verify")
    fun verifyEmailByCode(@RequestBody request: VerifyEmailCodeRequest): ResponseEntity<VerifyResponse> {
        validateVerifyCodeRequest(request)
        val response = try {
            userService.verifyByCode(request.email, request.code)
        } catch (ex: InvalidVerificationCodeException) {
            throw ex
        } catch (ex: TooManyVerificationAttemptsException) {
            throw ex
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/resend-verification")
    fun resendVerification(@RequestBody request: ResendVerificationRequest): ResponseEntity<ResendVerificationResponse> {
        val response = try {
            userService.resendVerificationCode(request.email)
        } catch (ex: ResendCooldownException) {
            throw ex
        } catch (ex: IllegalArgumentException) {
            throw ex
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val user = try {
            userService.login(request.email, request.password)
        } catch (ex: IllegalArgumentException) {
            val msg = ex.message ?: ""
            when {
                msg.contains("not verified") -> throw EmailNotVerifiedException(msg)
                msg.contains("not found") -> throw InvalidCredentialsException("Invalid credentials")
                msg.contains("Invalid password") -> throw InvalidCredentialsException("Invalid credentials")
                else -> throw ex
            }
        }

        val accessToken = tokenService.generateAccessToken(
            userId = user.id!!,
            roles = listOf("USER"),
            subscriptionTier = user.subscriptionTier
        )
        val refreshToken = tokenService.generateRefreshToken(user.id!!)

        return ResponseEntity.ok(
            LoginResponse(
                access_token = accessToken,
                refresh_token = refreshToken,
                token_type = "Bearer",
                expires_in = jwtProperties.accessTokenTtl
            )
        )
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<LoginResponse> {
        val refreshTokenEntity = try {
            tokenService.validateRefreshToken(request.refresh_token)
        } catch (ex: IllegalArgumentException) {
            throw InvalidCredentialsException(ex.message ?: "Invalid refresh token")
        }

        tokenService.deleteRefreshToken(refreshTokenEntity.id!!)

        val user = try {
            userService.findById(refreshTokenEntity.userId)
        } catch (ex: IllegalArgumentException) {
            throw InvalidCredentialsException("User not found")
        }

        val accessToken = tokenService.generateAccessToken(
            userId = user.id!!,
            roles = listOf("USER"),
            subscriptionTier = user.subscriptionTier
        )
        val newRefreshToken = tokenService.generateRefreshToken(user.id!!)

        return ResponseEntity.ok(
            LoginResponse(
                access_token = accessToken,
                refresh_token = newRefreshToken,
                token_type = "Bearer",
                expires_in = jwtProperties.accessTokenTtl
            )
        )
    }

    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ResponseEntity<LogoutResponse> {
        val authHeader = httpRequest.getHeader("Authorization") ?: ""
        val token = if (authHeader.startsWith("Bearer ")) authHeader.substring(7) else ""

        if (token.isNotEmpty()) {
            try {
                val jwt = jwtDecoder.decode(token)
                val jti = jwt.id
                val expiresAt = jwt.expiresAt
                if (jti != null && expiresAt != null) {
                    tokenService.revokeAccessToken(jti, expiresAt)
                }
            } catch (ex: Exception) {
                log.warn("Failed to decode access token during logout: {}", ex.message)
            }
        }

        val authentication = SecurityContextHolder.getContext().authentication
        val userId = authentication?.principal as? String
        if (userId != null) {
            try {
                tokenService.deleteAllRefreshTokens(UUID.fromString(userId))
            } catch (ex: Exception) {
                log.warn("Failed to delete refresh tokens for userId={}: {}", userId, ex.message)
            }
        }

        return ResponseEntity.ok(LogoutResponse(message = "Logged out successfully"))
    }

    @PostMapping("/password/reset-request")
    fun requestPasswordReset(@RequestBody request: PasswordResetRequestDto): ResponseEntity<Map<String, String>> {
        try {
            userService.requestPasswordReset(request.email)
        } catch (ex: Exception) {
            log.warn("Password reset request failed: {}", ex.message)
        }
        return ResponseEntity.ok(mapOf("message" to "If the email is registered, a password reset link has been sent."))
    }

    @PostMapping("/password/reset")
    fun resetPassword(@RequestBody request: PasswordResetDto): ResponseEntity<Map<String, String>> {
        try {
            userService.resetPassword(request.token, request.new_password)
        } catch (ex: IllegalArgumentException) {
            val msg = ex.message ?: ""
            when {
                msg.contains("not found") -> throw IllegalArgumentException(msg)
                msg.contains("already used") -> throw IllegalArgumentException(msg)
                msg.contains("expired") -> throw IllegalArgumentException(msg)
                else -> throw ex
            }
        }
        return ResponseEntity.ok(mapOf("message" to "Password reset successfully"))
    }

    @PostMapping("/password/change")
    fun changePassword(@RequestBody request: PasswordChangeDto): ResponseEntity<Map<String, String>> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = UUID.fromString(authentication.principal as String)

        try {
            userService.changePassword(userId, request.current_password, request.new_password)
        } catch (ex: IllegalArgumentException) {
            val msg = ex.message ?: ""
            if (msg.contains("incorrect") || msg.contains("password")) {
                throw InvalidCredentialsException(msg)
            }
            throw ex
        }

        return ResponseEntity.ok(mapOf("message" to "Password changed successfully"))
    }

    private fun validateVerifyCodeRequest(request: VerifyEmailCodeRequest) {
        if (!EMAIL_REGEX.matches(request.email)) {
            throw ValidationException("Invalid email format: ${request.email}")
        }
        if (!request.code.matches(Regex("\\d{6}"))) {
            throw ValidationException("Code must be exactly 6 digits")
        }
    }

    private fun validateEmail(email: String) {
        if (!EMAIL_REGEX.matches(email)) {
            throw ValidationException("Invalid email format: $email")
        }
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw ValidationException("Password must be at least 8 characters long")
        }
        if (!UPPERCASE_REGEX.containsMatchIn(password)) {
            throw ValidationException("Password must contain at least one uppercase letter")
        }
        if (!LOWERCASE_REGEX.containsMatchIn(password)) {
            throw ValidationException("Password must contain at least one lowercase letter")
        }
        if (!DIGIT_REGEX.containsMatchIn(password)) {
            throw ValidationException("Password must contain at least one digit")
        }
    }
}
