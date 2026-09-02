package com.contentagg.aggregator.exception

open class ApplicationException : RuntimeException {

    val errorInfoList: List<ErrorInfo>

    constructor(code: String, message: String) : super(message) {
        this.errorInfoList = listOf(ErrorInfo(code, message))
    }

    constructor(code: String, message: String, cause: Throwable) : super(message, cause) {
        this.errorInfoList = listOf(ErrorInfo(code, message, cause.message))
    }

    constructor(errorInfoList: List<ErrorInfo>) : super(formatMessage(errorInfoList)) {
        this.errorInfoList = errorInfoList
    }

    constructor(errorInfoList: List<ErrorInfo>, cause: Throwable) : super(formatMessage(errorInfoList), cause) {
        this.errorInfoList = errorInfoList
    }

    companion object {
        private fun formatMessage(errors: List<ErrorInfo>): String =
            errors.joinToString(", ") { "[code='${it.code}'; message='${it.message}']" }
                .ifEmpty { "Unknown error" }
    }
}
