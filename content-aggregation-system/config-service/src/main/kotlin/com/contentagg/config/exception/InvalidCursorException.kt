package com.contentagg.config.exception

/**
 * Thrown when catalog cursor cannot be decoded. Maps to HTTP 400.
 */
class InvalidCursorException(
    message: String = "Invalid cursor",
) : BusinessException(
    ErrorCode.INVALID_CURSOR,
    message,
)
