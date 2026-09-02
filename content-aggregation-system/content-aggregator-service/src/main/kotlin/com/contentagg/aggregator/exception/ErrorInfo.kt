package com.contentagg.aggregator.exception

data class ErrorInfo(
    val code: String,
    val message: String,
    val cause: String? = null
)
