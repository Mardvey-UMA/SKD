package com.contentagg.parser.api.model.telegram.auth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class SubmitCodeRequest @JsonCreator constructor(
    @field:NotBlank(message = "Auth code must not be blank")
    @JsonProperty("code")
    val code: String,
)
