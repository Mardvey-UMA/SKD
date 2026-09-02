package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty

data class HabrAuthorDto(
    @JsonProperty("id") val id: String?,
    @JsonProperty("alias") val alias: String?,
    @JsonProperty("fullname") val fullname: String?,
    @JsonProperty("avatarUrl") val avatarUrl: String?,
    @JsonProperty("speciality") val speciality: String?,
)
