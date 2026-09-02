package com.contentagg.parser.exception

open class BusinessException(
    message: String,
    errors: List<ErrorInfo> = listOf(ErrorInfo(ErrorCode.INVALID_REQUEST, message)),
    cause: Throwable? = null,
) : ApplicationException(message, errors, cause)
