package com.contentagg.aggregator.api.model.content

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Size
import java.util.UUID

data class ContentBatchRequest(
    @JsonProperty("ids")
    @field:Size(max = 100, message = "ids must contain at most 100 items")
    val ids: List<UUID> = emptyList(),

    @JsonProperty("include_related")
    val includeRelated: Boolean = false,

    @JsonProperty("related_limit")
    @field:Max(value = 10, message = "related_limit must be at most 10")
    val relatedLimit: Int = 5
)
