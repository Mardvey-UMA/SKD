package com.contentagg.parser.integration.rest.configservice.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ApiResponseDto<T>(
    @JsonProperty("success") val success: Boolean?,
    @JsonProperty("data") val data: T?,
    @JsonProperty("error") val error: String?,
    @JsonProperty("message") val message: String?,
    @JsonProperty("timestamp") val timestamp: Long?,
) {
    companion object {
        fun <T> success(data: T): ApiResponseDto<T> =
            ApiResponseDto(true, data, null, null, System.currentTimeMillis())

        fun <T> error(error: String, message: String): ApiResponseDto<T> =
            ApiResponseDto(false, null, error, message, System.currentTimeMillis())
    }
}
