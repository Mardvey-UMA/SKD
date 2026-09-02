package com.contentagg.config.exception

/**
 * Exception thrown when a source is not found.
 * Extends NotFoundException (HTTP 404).
 */
class SourceNotFoundException(sourceId: String) : NotFoundException(
    ErrorCode.SOURCE_NOT_FOUND,
    "Source not found with id: $sourceId"
)
