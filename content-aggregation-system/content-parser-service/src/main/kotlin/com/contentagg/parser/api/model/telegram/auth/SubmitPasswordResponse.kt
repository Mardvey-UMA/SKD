package com.contentagg.parser.api.model.telegram.auth

data class SubmitPasswordResponse(
    val success: Boolean,
    val message: String,
    val newState: String,
)
