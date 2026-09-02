package com.contentagg.config.exception

/**
 * Internal server errors.
 * Maps to HTTP 500 Internal Server Error.
 */
open class InternalException : ApplicationException {

    constructor(code: String, message: String) : super(code, message)

    constructor(code: String, message: String, cause: Throwable) : super(code, message, cause)

    constructor(errorInfoList: List<ErrorInfo>) : super(errorInfoList)
}
