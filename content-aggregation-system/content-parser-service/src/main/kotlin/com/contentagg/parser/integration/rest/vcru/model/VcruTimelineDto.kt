package com.contentagg.parser.integration.rest.vcru.model

import com.fasterxml.jackson.annotation.JsonProperty

data class VcruTimelineResponseDto(
    @JsonProperty("result") val result: VcruTimelineDto?
)

data class VcruTimelineDto(
    @JsonProperty("items") val items: List<VcruTimelineItemDto>?,
    @JsonProperty("hasMore") val hasMore: Boolean?
)

data class VcruTimelineItemDto(
    @JsonProperty("type") val type: String?,
    @JsonProperty("data") val data: VcruArticleDto?
)
