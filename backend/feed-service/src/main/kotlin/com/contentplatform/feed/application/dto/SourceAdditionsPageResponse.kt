package com.contentplatform.feed.application.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SourceAdditionsPageResponse(
    val items: List<SourceAdditionResponse>,
    val cursor: String?,
    @JsonProperty("has_next") val hasNext: Boolean
)
