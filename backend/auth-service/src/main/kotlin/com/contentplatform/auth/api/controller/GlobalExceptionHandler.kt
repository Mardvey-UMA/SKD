package com.contentplatform.auth.api.controller

import com.contentplatform.auth.api.exception.DuplicateEmailException
import com.contentplatform.auth.api.exception.EmailNotVerifiedException
import com.contentplatform.auth.api.exception.InvalidCredentialsException
import com.contentplatform.auth.api.exception.InvalidVerificationCodeException
import com.contentplatform.auth.api.exception.ResendCooldownException
import com.contentplatform.auth.api.exception.TokenExpiredException
import com.contentplatform.auth.api.exception.TokenNotFoundException
import com.contentplatform.auth.api.exception.TooManyVerificationAttemptsException
import com.contentplatform.auth.api.exception.ValidationException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.Instant

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.BAD_REQUEST, "bad_request", ex.message ?: "Bad request", request)
    }

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(
        ex: DuplicateEmailException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.CONFLICT, "duplicate_email", ex.message ?: "Conflict", request)
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(
        ex: InvalidCredentialsException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.UNAUTHORIZED, "invalid_credentials", ex.message ?: "Unauthorized", request)
    }

    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerified(
        ex: EmailNotVerifiedException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.FORBIDDEN, "email_not_verified", ex.message ?: "Forbidden", request)
    }

    @ExceptionHandler(TokenNotFoundException::class)
    fun handleTokenNotFound(
        ex: TokenNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.NOT_FOUND, "token_not_found", ex.message ?: "Not found", request)
    }

    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpired(
        ex: TokenExpiredException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.BAD_REQUEST, "token_expired", ex.message ?: "Token expired", request)
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(
        ex: ValidationException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "validation_error", ex.message ?: "Validation error", request)
    }

    @ExceptionHandler(InvalidVerificationCodeException::class)
    fun handleInvalidVerificationCode(
        ex: InvalidVerificationCodeException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_CODE", "Код подтверждения недействителен или истёк", request)
    }

    @ExceptionHandler(TooManyVerificationAttemptsException::class)
    fun handleTooManyVerificationAttempts(
        ex: TooManyVerificationAttemptsException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        val body = mapOf(
            "error" to "TOO_MANY_ATTEMPTS",
            "message" to "Слишком много неверных попыток. Повторите позже.",
            "retryAfterSeconds" to ex.retryAfterSeconds,
            "request_id" to request.getHeader("X-Request-Id"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(body)
    }

    @ExceptionHandler(ResendCooldownException::class)
    fun handleResendCooldown(
        ex: ResendCooldownException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        val body = mapOf(
            "error" to "RESEND_COOLDOWN",
            "message" to "Повторная отправка недоступна. Подождите перед следующей попыткой.",
            "retryAfterSeconds" to ex.retryAfterSeconds,
            "request_id" to request.getHeader("X-Request-Id"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(body)
    }

    private fun buildResponse(
        status: HttpStatus,
        error: String,
        message: String,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        val body = mapOf(
            "error" to error,
            "message" to message,
            "request_id" to request.getHeader("X-Request-Id"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(status).body(body)
    }
}
