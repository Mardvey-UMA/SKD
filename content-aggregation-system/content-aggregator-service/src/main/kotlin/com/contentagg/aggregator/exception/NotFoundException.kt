package com.contentagg.aggregator.exception

open class NotFoundException : ApplicationException {

    constructor(code: String, message: String) : super(code, message)

    constructor(code: String, message: String, cause: Throwable) : super(code, message, cause)

    constructor(errorInfoList: List<ErrorInfo>) : super(errorInfoList)
}
