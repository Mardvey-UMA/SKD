package com.contentagg.parser.api.model.telegram.auth
import com.fasterxml.jackson.annotation.JsonProperty

data class AuthStatusResponse(
    val state: String,
    @JsonProperty("isAuthenticated") val isAuthenticated: Boolean,
    val passwordHint: String?,
)
