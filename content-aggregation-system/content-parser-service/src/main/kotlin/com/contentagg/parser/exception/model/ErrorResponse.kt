package com.contentagg.parser.exception.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val integrationId: String?,
    val errors: List<Error>,
) {
    companion object {
        fun of(integrationId: String?, errors: List<Error>) =
            ErrorResponse(integrationId, errors)

        fun of(errors: List<Error>) =
            ErrorResponse(null, errors)
    }
}
