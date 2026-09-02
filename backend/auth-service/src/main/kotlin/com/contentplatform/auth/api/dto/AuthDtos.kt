package com.contentplatform.auth.api.dto

data class RegisterRequest(val email: String, val password: String)
data class RegisterResponse(val message: String, val email: String)

data class VerifyResponse(val message: String, val email: String)

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Long
)

data class RefreshRequest(val refresh_token: String)

data class LogoutResponse(val message: String)

data class PasswordResetRequestDto(val email: String)

data class PasswordResetDto(val token: String, val new_password: String)

data class PasswordChangeDto(val current_password: String, val new_password: String)

data class VerifyEmailCodeRequest(
    val email: String,
    val code: String,
)

data class ResendVerificationRequest(
    val email: String,
)

data class ResendVerificationResponse(
    val message: String,
    val cooldownSeconds: Long,
)
