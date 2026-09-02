package com.contentagg.config.exception

/**
 * Exception thrown when JSON conversion fails.
 * Extends InternalException (HTTP 500).
 */
class JsonConversionException : InternalException {

    constructor(message: String) : super(ErrorCode.JSON_CONVERSION_ERROR, message)

    constructor(message: String, cause: Throwable) : super(ErrorCode.JSON_CONVERSION_ERROR, message, cause)
}
