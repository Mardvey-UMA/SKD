package com.contentagg.parser.api.model.telegram.auth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class SubmitPasswordRequest @JsonCreator constructor(
    @field:NotBlank(message = "Password must not be blank")
    @JsonProperty("password")
    val password: String,
)
