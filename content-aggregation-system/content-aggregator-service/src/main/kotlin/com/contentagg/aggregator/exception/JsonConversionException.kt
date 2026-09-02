package com.contentagg.aggregator.exception

class JsonConversionException : InternalException {

    constructor(message: String) : super(ErrorCode.JSON_CONVERSION_ERROR, message)

    constructor(message: String, cause: Throwable) : super(ErrorCode.JSON_CONVERSION_ERROR, message, cause)
}
