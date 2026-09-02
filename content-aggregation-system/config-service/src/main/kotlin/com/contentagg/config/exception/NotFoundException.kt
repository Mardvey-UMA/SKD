package com.contentagg.config.exception

/**
 * Resource not found errors.
 * Maps to HTTP 404 Not Found.
 */
open class NotFoundException : ApplicationException {

    constructor(code: String, message: String) : super(code, message)

    constructor(code: String, message: String, cause: Throwable) : super(code, message, cause)

    constructor(errorInfoList: List<ErrorInfo>) : super(errorInfoList)

    companion object {
        /** Create not found exception for a source by ID. */
        fun sourceNotFound(sourceId: String): NotFoundException =
            NotFoundException(
                ErrorCode.SOURCE_NOT_FOUND,
                "Source not found with id: $sourceId"
            )
    }
}
