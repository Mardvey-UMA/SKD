package com.contentagg.aggregator.exception.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val integrationId: String,
    val errors: List<Error>
) {
    constructor(errors: List<Error>) : this(UUID.randomUUID().toString(), errors)

    companion object {
        fun of(code: String, message: String): ErrorResponse =
            ErrorResponse(listOf(Error(code, message)))

        fun of(code: String, message: String, cause: String?): ErrorResponse =
            ErrorResponse(listOf(Error(code, message, cause)))
    }
}
