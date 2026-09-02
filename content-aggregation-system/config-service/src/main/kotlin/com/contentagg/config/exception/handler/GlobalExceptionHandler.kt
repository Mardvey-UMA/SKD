package com.contentagg.config.exception.handler

import com.contentagg.config.exception.ApplicationException
import com.contentagg.config.exception.BusinessException
import com.contentagg.config.exception.ErrorCode
import com.contentagg.config.exception.ErrorInfo
import com.contentagg.config.exception.NotFoundException
import com.contentagg.config.exception.SourceLimitExceededException
import com.contentagg.config.exception.TelegramValidationUnavailableException
import com.contentagg.config.exception.ValidationException
import com.contentagg.config.exception.model.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.UUID

/**
 * Global exception handler for REST API.
 * Uses structured ErrorResponse format with error codes.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("NotFoundException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapToErrorResponse(ex))
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("BusinessException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapToErrorResponse(ex))
    }

    @ExceptionHandler(SourceLimitExceededException::class)
    fun handleSourceLimitExceeded(
        ex: SourceLimitExceededException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("SourceLimitExceededException: current={} limit={}", ex.current, ex.limit)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapToErrorResponse(ex))
    }

    @ExceptionHandler(TelegramValidationUnavailableException::class)
    fun handleTelegramValidationUnavailable(
        ex: TelegramValidationUnavailableException,
        request: WebRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("TelegramValidationUnavailableException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapToErrorResponse(ex))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(ex: ValidationException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.warn("ValidationException: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(mapToErrorResponse(ex))
    }

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(ex: ApplicationException, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error("ApplicationException: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapToErrorResponse(ex))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val errors = ex.bindingResult.fieldErrors.map { error ->
            ErrorInfo(
                code = ErrorCode.VALIDATION_ERROR,
                message = "${error.field}: ${error.defaultMessage}"
            )
        }

        log.warn("Validation failed: {} errors", errors.size)

        val response = ErrorResponse(UUID.randomUUID().toString(), errors)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error: {}", ex.message, ex)

        val response = ErrorResponse(listOf(
            ErrorInfo(ErrorCode.TECHNICAL_ERROR, "Internal server error", ex.message)
        ))

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }

    private fun mapToErrorResponse(ex: ApplicationException): ErrorResponse {
        val integrationId = UUID.randomUUID().toString()
        return ErrorResponse(integrationId, ex.errorInfoList)
    }
}
