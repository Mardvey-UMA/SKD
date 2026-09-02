package com.contentagg.aggregator.api.model.content

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ContentBatchResponse(
    @JsonProperty("items")
    val items: Map<String, ContentBatchItem>,

    @JsonProperty("not_found")
    val notFound: List<String>
)
