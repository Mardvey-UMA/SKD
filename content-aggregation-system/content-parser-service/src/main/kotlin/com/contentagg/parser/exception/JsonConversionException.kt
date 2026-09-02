package com.contentagg.parser.exception

class JsonConversionException(
    message: String,
    cause: Throwable? = null,
) : InternalException(
    message = message,
    errors = listOf(ErrorInfo(ErrorCode.JSON_CONVERSION_ERROR, message)),
    cause = cause,
)
