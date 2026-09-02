package com.contentagg.config.exception

/**
 * Business logic errors.
 * Maps to HTTP 400 Bad Request.
 */
open class BusinessException : ApplicationException {

    constructor(code: String, message: String) : super(code, message)

    constructor(code: String, message: String, cause: Throwable) : super(code, message, cause)

    constructor(errorInfoList: List<ErrorInfo>) : super(errorInfoList)
}
