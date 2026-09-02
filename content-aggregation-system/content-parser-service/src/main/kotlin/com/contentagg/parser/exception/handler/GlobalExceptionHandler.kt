package com.contentagg.parser.exception.handler

import com.contentagg.parser.exception.ApplicationException
import com.contentagg.parser.exception.BusinessException
import com.contentagg.parser.exception.ErrorCode
import com.contentagg.parser.exception.NotFoundException
import com.contentagg.parser.exception.ValidationException
import com.contentagg.parser.exception.model.Error
import com.contentagg.parser.exception.model.ErrorResponse
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
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.map {
            Error(ErrorCode.VALIDATION_ERROR, "${it.field}: ${it.defaultMessage}")
        }
        log.warn("Validation failed: {} errors", errors.size)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(UUID.randomUUID().toString(), errors))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                integrationId = UUID.randomUUID().toString(),
                errors = listOf(Error(ErrorCode.TECHNICAL_ERROR, "Internal server error", ex.message)),
            )
        )
    }

    private fun mapToErrorResponse(ex: ApplicationException): ErrorResponse {
        val integrationId = UUID.randomUUID().toString()
        val errors = ex.errors.map { info -> Error(info.code, info.message, info.cause) }
        return ErrorResponse(integrationId, errors)
    }
}
