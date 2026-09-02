package com.contentagg.config.exception

/**
 * Validation errors.
 * Maps to HTTP 422 Unprocessable Entity.
 */
class ValidationException : ApplicationException {

    constructor(code: String, message: String) : super(code, message)

    constructor(code: String, message: String, cause: String) : super(listOf(ErrorInfo(code, message, cause)))

    constructor(errorInfoList: List<ErrorInfo>) : super(errorInfoList)

    companion object {
        /** Create validation exception for a field. */
        fun forField(fieldName: String, message: String): ValidationException =
            ValidationException(
                ErrorCode.VALIDATION_ERROR,
                "Field '$fieldName': $message"
            )
    }
}
