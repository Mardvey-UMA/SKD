package com.contentagg.config.exception

/**
 * Thrown when a user tries to add a source beyond their allowed limit.
 * Maps to HTTP 429.
 */
class SourceLimitExceededException(
    val current: Int,
    val limit: Int,
) : ApplicationException(
    ErrorCode.SOURCE_LIMIT_REACHED,
    "Source addition limit reached: $current of $limit"
)
