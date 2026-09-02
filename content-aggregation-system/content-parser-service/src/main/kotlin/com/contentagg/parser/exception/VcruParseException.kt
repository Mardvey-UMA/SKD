package com.contentagg.parser.exception

class VcruParseException(
    message: String,
    cause: Throwable? = null,
) : BusinessException(
    message = message,
    errors = listOf(ErrorInfo(ErrorCode.VCRU_PARSE_ERROR, message)),
    cause = cause,
) {
    // Secondary constructor for Java interop — Kotlin default params are not visible from Java
    constructor(message: String) : this(message, null)
}
