package com.contentagg.parser.exception

class NotFoundException(
    message: String,
    errors: List<ErrorInfo> = listOf(ErrorInfo(ErrorCode.SOURCE_NOT_FOUND, message)),
) : ApplicationException(message, errors) {

    companion object {
        fun sourceNotFound(sourceId: String): NotFoundException =
            NotFoundException("Source not found: $sourceId")
    }
}
