package com.contentagg.parser.exception

open class InternalException(
    message: String,
    errors: List<ErrorInfo> = listOf(ErrorInfo(ErrorCode.INTERNAL_ERROR, message)),
    cause: Throwable? = null,
) : ApplicationException(message, errors, cause)
