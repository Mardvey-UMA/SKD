package com.skd.userinteractions.api

import com.skd.userinteractions.api.model.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        exception: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Unprocessable entity: {}", exception.message)
        val requestId = extractRequestId(request)
        val body = ErrorResponse(
            error = "Unprocessable Entity",
            message = exception.message ?: "Unprocessable entity",
            requestId = requestId,
            timestamp = Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        exception: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Internal server error", exception)
        val requestId = extractRequestId(request)
        val body = ErrorResponse(
            error = "Internal Server Error",
            message = exception.message ?: "Internal server error",
            requestId = requestId,
            timestamp = Instant.now().toString()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }

    private fun extractRequestId(request: WebRequest): String {
        return if (request is ServletWebRequest) {
            request.getHeader("X-Request-Id") ?: ""
        } else {
            ""
        }
    }
}
