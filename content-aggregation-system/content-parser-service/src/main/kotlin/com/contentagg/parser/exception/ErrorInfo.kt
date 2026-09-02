package com.contentagg.parser.exception

data class ErrorInfo(
    val code: String,
    val message: String,
    val cause: String? = null,
)
