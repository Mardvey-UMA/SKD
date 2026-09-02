package com.contentagg.config.exception

/**
 * Centralized error codes for config-service.
 */
object ErrorCode {
    // Technical errors (500)
    const val TECHNICAL_ERROR = "TECHNICAL_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val JSON_CONVERSION_ERROR = "JSON_CONVERSION_ERROR"

    // Business errors (400)
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val DUPLICATE_SOURCE = "DUPLICATE_SOURCE"
    const val INVALID_SOURCE = "INVALID_SOURCE"
    const val CHANNEL_NOT_ACCESSIBLE = "channel_not_accessible"
    const val INVALID_CURSOR = "invalid_cursor"

    // Validation errors (422)
    const val VALIDATION_ERROR = "VALIDATION_ERROR"

    // Not found errors (404)
    const val SOURCE_NOT_FOUND = "SOURCE_NOT_FOUND"

    // Rate/limit errors (429)
    const val SOURCE_LIMIT_REACHED = "source_limit_reached"

    // Service unavailable (503)
    const val TELEGRAM_VALIDATION_UNAVAILABLE = "telegram_validation_unavailable"
}
