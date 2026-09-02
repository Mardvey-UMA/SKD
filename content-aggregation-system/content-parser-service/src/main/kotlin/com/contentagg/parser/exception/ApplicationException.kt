package com.contentagg.parser.exception

open class ApplicationException(
    message: String,
    val errors: List<ErrorInfo> = listOf(ErrorInfo(ErrorCode.INTERNAL_ERROR, message)),
    cause: Throwable? = null,
) : RuntimeException(message, cause)
