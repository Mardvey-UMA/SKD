package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty

data class HabrHubDto(
    @JsonProperty("id") val id: String?,
    @JsonProperty("alias") val alias: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("titleHtml") val titleHtml: String?,
    @JsonProperty("isProfiled") val isProfiled: Boolean?,
)
