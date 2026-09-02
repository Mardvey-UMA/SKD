package com.contentagg.config.exception

/**
 * Error information with code, message, and optional cause.
 */
data class ErrorInfo(
    val code: String,
    val message: String,
    val cause: String? = null
)
