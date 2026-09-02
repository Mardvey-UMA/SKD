package com.contentagg.parser.exception

class ValidationException(
    message: String,
    errors: List<ErrorInfo> = listOf(ErrorInfo(ErrorCode.VALIDATION_ERROR, message)),
) : ApplicationException(message, errors) {

    companion object {
        fun forField(field: String, message: String): ValidationException =
            ValidationException(
                message = "Validation failed for field '$field': $message",
                errors = listOf(ErrorInfo(ErrorCode.VALIDATION_ERROR, message, field)),
            )
    }
}
