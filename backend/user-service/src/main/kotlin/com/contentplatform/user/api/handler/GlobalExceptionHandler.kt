package com.contentplatform.user.api.handler

import com.contentplatform.user.application.exception.NotFoundException
import com.contentplatform.user.application.exception.OnboardingAlreadyCompletedException
import com.contentplatform.user.infrastructure.client.RecSystemException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<Map<String, Any>> {
        log.warn("NotFoundException: {}", ex.message)
        val body = mapOf(
            "error" to "NOT_FOUND",
            "message" to (ex.message ?: "Resource not found"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
    }

    @ExceptionHandler(OnboardingAlreadyCompletedException::class)
    fun handleOnboardingAlreadyCompleted(ex: OnboardingAlreadyCompletedException): ResponseEntity<Map<String, Any>> {
        log.warn("OnboardingAlreadyCompletedException: {}", ex.message)
        val body = mapOf(
            "error" to "CONFLICT",
            "message" to (ex.message ?: "Onboarding already completed"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        log.warn("MethodArgumentNotValidException: {}", ex.message)
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        val body = mapOf(
            "error" to "UNPROCESSABLE_ENTITY",
            "message" to errors.joinToString("; "),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body)
    }

    @ExceptionHandler(RecSystemException::class)
    fun handleRecSystemException(ex: RecSystemException): ResponseEntity<Map<String, Any>> {
        log.warn("RecSystemException: {}", ex.message)
        val body = mapOf(
            "error" to "BAD_GATEWAY",
            "message" to (ex.message ?: "Upstream service unavailable"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<Map<String, Any>> {
        val body = mapOf(
            "error" to ex.statusCode.toString(),
            "message" to (ex.reason ?: ex.message ?: "Request error"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(ex.statusCode).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, Any>> {
        log.error("Unexpected exception", ex)
        val body = mapOf(
            "error" to "INTERNAL_SERVER_ERROR",
            "message" to (ex.message ?: "Internal server error"),
            "timestamp" to Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}
