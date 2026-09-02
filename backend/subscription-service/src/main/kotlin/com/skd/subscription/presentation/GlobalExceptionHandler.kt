package com.skd.subscription.presentation

import com.skd.subscription.integration.rest.yookassa.PaymentProviderUnavailableException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(PaymentProviderUnavailableException::class)
    fun handlePaymentProviderUnavailable(
        ex: PaymentProviderUnavailableException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        log.warn(
            "PaymentProviderUnavailableException on path={}: cause={} message={}",
            request.requestURI,
            ex.cause?.javaClass?.name,
            ex.cause?.message
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(buildError("payment_provider_unavailable", "Платёжный провайдер временно недоступен", request))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        log.warn("IllegalArgumentException on path={}: {}", request.requestURI, ex.message)
        return ResponseEntity.badRequest().body(buildError("BAD_REQUEST", ex.message, request))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(
        ex: IllegalStateException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        log.warn("IllegalStateException on path={}: {}", request.requestURI, ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildError("CONFLICT", ex.message, request))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntime(
        ex: RuntimeException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        log.error("RuntimeException on path={}", request.requestURI, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildError("INTERNAL_SERVER_ERROR", ex.message, request))
    }

    private fun buildError(
        error: String,
        message: String?,
        request: HttpServletRequest
    ): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>(
            "error" to error,
            "message" to message,
            "timestamp" to Instant.now().toString()
        )
        val requestId = request.getHeader("X-Request-Id")
        if (!requestId.isNullOrBlank()) {
            body["request_id"] = requestId
        }
        return body
    }
}
