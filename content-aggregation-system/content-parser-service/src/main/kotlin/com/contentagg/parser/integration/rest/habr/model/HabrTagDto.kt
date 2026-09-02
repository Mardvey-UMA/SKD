package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty

data class HabrTagDto(
    @JsonProperty("titleHtml") val titleHtml: String?,
)
