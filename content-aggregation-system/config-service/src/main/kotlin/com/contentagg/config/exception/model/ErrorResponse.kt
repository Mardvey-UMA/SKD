package com.contentagg.config.exception.model

import com.contentagg.config.exception.ErrorInfo
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

/**
 * Error response wrapper.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val integrationId: String,
    val errors: List<ErrorInfo>
) {
    constructor(errors: List<ErrorInfo>) : this(UUID.randomUUID().toString(), errors)

    companion object {
        fun of(code: String, message: String): ErrorResponse =
            ErrorResponse(listOf(ErrorInfo(code, message)))

        fun of(code: String, message: String, cause: String): ErrorResponse =
            ErrorResponse(listOf(ErrorInfo(code, message, cause)))
    }
}
