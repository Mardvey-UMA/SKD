package com.contentagg.config.exception

/**
 * Thrown when the Telegram validation backend (content-parser-service) is unreachable.
 * Maps to HTTP 503.
 */
class TelegramValidationUnavailableException(
    message: String = "Telegram validation service unavailable",
) : ApplicationException(
    ErrorCode.TELEGRAM_VALIDATION_UNAVAILABLE,
    message,
)

