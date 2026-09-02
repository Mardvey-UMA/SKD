package com.contentplatform.user.api.dto

data class HealthResponse(
    val status: String,
    val service: String,
    val checks: Map<String, String>
)
