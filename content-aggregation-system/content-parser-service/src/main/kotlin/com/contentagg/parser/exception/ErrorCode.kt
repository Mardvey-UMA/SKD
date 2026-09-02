package com.contentagg.parser.exception

object ErrorCode {
    // Technical errors (500)
    const val TECHNICAL_ERROR = "TECHNICAL_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val JSON_CONVERSION_ERROR = "JSON_CONVERSION_ERROR"
    const val KAFKA_PUBLISH_ERROR = "KAFKA_PUBLISH_ERROR"

    // Business errors (400)
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val PARSING_ERROR = "PARSING_ERROR"
    const val HABR_PARSE_ERROR = "HABR_PARSE_ERROR"
    const val VCRU_PARSE_ERROR = "VCRU_PARSE_ERROR"
    const val TELEGRAM_PARSE_ERROR = "TELEGRAM_PARSE_ERROR"

    // Validation errors (422)
    const val VALIDATION_ERROR = "VALIDATION_ERROR"

    // Not found errors (404)
    const val SOURCE_NOT_FOUND = "SOURCE_NOT_FOUND"
}
