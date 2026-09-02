package com.contentagg.parser.exception

class ParsingException(
    val source: String,
    val externalId: String,
    cause: Throwable? = null,
) : InternalException(
    message = "Failed to parse content from source '$source' with id '$externalId'",
    errors = listOf(ErrorInfo(ErrorCode.PARSING_ERROR, "Parsing failed for source: $source")),
    cause = cause,
)
