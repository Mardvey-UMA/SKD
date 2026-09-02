package com.contentagg.aggregator.exception

object ErrorCode {
    // Technical errors (500)
    const val TECHNICAL_ERROR = "TECHNICAL_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val JSON_CONVERSION_ERROR = "JSON_CONVERSION_ERROR"

    // Business errors (400)
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val DUPLICATE_CONTENT = "DUPLICATE_CONTENT"

    // Not found errors (404)
    const val CONTENT_NOT_FOUND = "CONTENT_NOT_FOUND"
}
