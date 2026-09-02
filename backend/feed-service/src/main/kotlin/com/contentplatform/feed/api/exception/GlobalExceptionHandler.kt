package com.contentplatform.feed.api.exception

import com.contentplatform.feed.application.exception.AlreadyExistsException
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

    @ExceptionHandler(AlreadyExistsException::class)
    fun handleAlreadyExists(ex: AlreadyExistsException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("AlreadyExistsException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("conflict", ex.message ?: "Already exists", request))
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("NotFoundException: {}", ex.message)
        val errorCode = if (ex.message?.contains("Space", ignoreCase = true) == true) "space_not_found" else "not_found"
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(errorCode, ex.message ?: "Not found", request))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("IllegalArgumentException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("validation_error", ex.message ?: "Bad request", request))
    }

    @ExceptionHandler(SpaceLimitReachedException::class)
    fun handleSpaceLimit(ex: SpaceLimitReachedException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("SpaceLimitReachedException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("space_limit_reached", ex.message ?: "Space limit reached", request))
    }

    @ExceptionHandler(DuplicateSpaceNameException::class)
    fun handleDuplicateName(ex: DuplicateSpaceNameException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("DuplicateSpaceNameException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("duplicate_space_name", ex.message ?: "Duplicate space name", request))
    }

    @ExceptionHandler(InvalidSignatureException::class)
    fun handleInvalidSignature(ex: InvalidSignatureException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("InvalidSignatureException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("invalid_signature", ex.message ?: "Invalid signature", request))
    }

    @ExceptionHandler(UpstreamUnavailableException::class)
    fun handleUpstreamUnavailable(ex: UpstreamUnavailableException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("UpstreamUnavailableException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody("upstream_unavailable", ex.message ?: "Upstream unavailable", request))
    }

    @ExceptionHandler(SpaceFeedUnavailableException::class)
    fun handleSpaceFeedUnavailable(ex: SpaceFeedUnavailableException, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.warn("SpaceFeedUnavailableException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody("space_feed_unavailable", ex.message ?: "Space feed unavailable", request))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        log.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("internal_error", "An unexpected error occurred", request))
    }

    private fun errorBody(error: String, message: String, request: HttpServletRequest): Map<String, Any?> {
        return mapOf(
            "error" to error,
            "message" to message,
            "request_id" to request.getHeader("X-Request-Id"),
            "timestamp" to Instant.now().toString()
        )
    }
}
