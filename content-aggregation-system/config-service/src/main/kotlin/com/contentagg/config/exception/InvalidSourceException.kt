package com.contentagg.config.exception

/**
 * Exception thrown when source configuration is invalid.
 * Extends BusinessException (HTTP 400).
 */
class InvalidSourceException : BusinessException {

    constructor(message: String) : super(ErrorCode.INVALID_SOURCE, message)

    constructor(message: String, cause: Throwable) : super(ErrorCode.INVALID_SOURCE, message, cause)
}
