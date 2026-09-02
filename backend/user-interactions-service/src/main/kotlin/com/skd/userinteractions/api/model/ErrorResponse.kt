package com.skd.userinteractions.api.model

data class ErrorResponse(
    val error: String,
    val message: String,
    val requestId: String,
    val timestamp: String,
    val details: Map<String, Any>? = null
)
